package com.orlikalabs.microreview

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.orlikalabs.microreview.ui.CurrentCard
import com.orlikalabs.microreview.ui.DeckItem
import com.orlikalabs.microreview.ui.MainScreen
import com.orlikalabs.microreview.ui.MainScreenActions
import com.orlikalabs.microreview.ui.ScreenState
import com.orlikalabs.microreview.ui.SetupStatus
import com.orlikalabs.microreview.ui.WallpaperSetup
import com.orlikalabs.microreview.ui.theme.AnkiNotificationsTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DECK_CHANGE_DEBOUNCE_MS = 800L
private const val TAG = "BackgroundService"

class MainActivity : ComponentActivity() {
    private var screenState by mutableStateOf<ScreenState>(ScreenState.Loading)
    private val decks = mutableStateListOf<DeckItem>()
    private val selectedDeckIds = mutableStateListOf<Long>()
    private var currentCard by mutableStateOf<CurrentCard>(CurrentCard.NotStarted)
    private var setupStatus by mutableStateOf(SetupStatus())

    private val permissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results[Manifest.permission.POST_NOTIFICATIONS] == false) {
                Toast.makeText(this, R.string.notification_permission_denied, Toast.LENGTH_LONG).show()
            }
            refresh()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Notifications.createChannel(this)
        if (DeckSelection.load(this).isNotEmpty()) {
            // Reviewing is started: make sure the periodic check uses the current interval.
            PeriodicWorker.schedule(applicationContext)
        }
        // Alarms are lost when the app is force-stopped: set the next scheduled review again.
        ScheduledReview.schedule(applicationContext)
        requestMissingPermissions()

        val actions = MainScreenActions(
            onToggleDeck = ::toggleDeck,
            onMoveDeck = ::moveDeck,
            onGrantPermission = ::requestMissingPermissions,
            onRetry = ::refresh,
            onOpenWallpaperSettings = { WallpaperSetup.openSettings(this) },
            onSetLockScreenPicture = { lifecycleScope.launch { if (WallpaperSetup.setPicture(this@MainActivity)) refresh() } },
            onOpenSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
            onOpenNotificationSettings = ::openNotificationSettings,
            onOpenBatterySettings = ::openBatterySettings,
        )
        setContent {
            AnkiNotificationsTheme {
                MainScreen(
                    state = screenState,
                    decks = decks,
                    selectedDeckIds = selectedDeckIds,
                    currentCard = currentCard,
                    liveLockOnly = WallpaperSetup.liveLockOnly(this),
                    setup = setupStatus,
                    actions = actions,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Due counts and the current card change while reviewing from the notification.
        refresh()
    }

    private fun requestMissingPermissions() {
        val missing = buildList {
            if (AnkiDroidHelper.isApiAvailable(this@MainActivity) && !AnkiDroidHelper(this@MainActivity).isPermissionGranted) {
                add(AnkiDroidContract.READ_WRITE_PERMISSION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !isGranted(Manifest.permission.POST_NOTIFICATIONS)) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (missing.isNotEmpty()) {
            permissionsLauncher.launch(missing.toTypedArray())
        }
    }

    private fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun refresh() {
        setupStatus = readSetupStatus()
        if (!AnkiDroidHelper.isApiAvailable(this)) {
            screenState = ScreenState.ApiUnavailable
            return
        }
        if (!AnkiDroidHelper(this).isPermissionGranted) {
            screenState = ScreenState.PermissionMissing
            return
        }
        lifecycleScope.launch {
            val loaded = try {
                withContext(Dispatchers.IO) {
                    // The card may have been reviewed or changed in AnkiDroid since it was shown.
                    CardStateController.syncWithAnkiDroid(applicationContext)
                    loadDecks()
                }
            } catch (e: RuntimeException) {
                Log.e(TAG, "Could not load the decks", e)
                null
            }
            if (loaded == null) {
                screenState = ScreenState.LoadFailed
                return@launch
            }
            decks.clear()
            decks.addAll(loaded.decks)
            selectedDeckIds.clear()
            selectedDeckIds.addAll(loaded.selectedDeckIds)
            currentCard = loaded.currentCard
            screenState = ScreenState.Ready
        }
    }

    private class LoadedData(val decks: List<DeckItem>, val selectedDeckIds: List<Long>, val currentCard: CurrentCard)

    /** The decks and the displayed card, or null if AnkiDroid doesn't give its decks. */
    private fun loadDecks(): LoadedData? {
        val anki = AnkiDroidHelper(this)
        val names = anki.deckNames ?: return null
        val counts = anki.getDeckCounts()
        val decks = names.map { (id, name) ->
            val count = counts[id]
            DeckItem(id, name, newCount = count?.get(2), learnCount = count?.get(0), reviewCount = count?.get(1))
        }.sortedBy { it.name.lowercase() }
        val selected = DeckSelection.load(this).filter { it in names }
        return LoadedData(decks, selected, readCurrentCard(anki))
    }

    private fun readCurrentCard(anki: AnkiDroidHelper): CurrentCard {
        val state = anki.storedState ?: return CurrentCard.NotStarted
        if (!state.hasCard()) {
            return CurrentCard.NoneDue
        }
        val question = CardText.clean(state.question)
        // A card stored by an older version may have nothing left to show (e.g. a sound-only question).
        if (question.isEmpty() || CardText.hasSound(state.question)) {
            return CurrentCard.NotStarted
        }
        return CurrentCard.Showing(question, anki.getDeckName(state.deckId) ?: "")
    }

    private fun toggleDeck(deckId: Long) {
        if (!selectedDeckIds.remove(deckId)) {
            selectedDeckIds.add(deckId)
        }
        onDeckSelectionChanged()
    }

    private fun moveDeck(deckId: Long, offset: Int) {
        val from = selectedDeckIds.indexOf(deckId)
        val to = from + offset
        if (from == -1 || to !in selectedDeckIds.indices) return
        selectedDeckIds.add(to, selectedDeckIds.removeAt(from))
        onDeckSelectionChanged()
    }

    private var deckChangeSync: Job? = null

    /**
     * Saves the selection and, once the user stops changing it, shows the new first card if it differs. Reviewing
     * starts with the first selected deck, and stops when none is left.
     */
    private fun onDeckSelectionChanged() {
        DeckSelection.save(this, selectedDeckIds.toList())
        if (selectedDeckIds.isNotEmpty()) PeriodicWorker.schedule(applicationContext)
        deckChangeSync?.cancel()
        deckChangeSync = lifecycleScope.launch {
            delay(DECK_CHANGE_DEBOUNCE_MS)
            val action = try {
                withContext(Dispatchers.IO) { CardStateController.syncWithAnkiDroid(applicationContext) }
            } catch (e: RuntimeException) {
                Log.e(TAG, "Could not sync with AnkiDroid", e)
                null
            }
            // A failed sync goes through refresh() too, which shows the error screen if AnkiDroid still fails.
            if (action != CardSync.Action.NONE) refresh()
        }
    }

    private fun readSetupStatus(): SetupStatus {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = notificationManager.getNotificationChannel(Notifications.CHANNEL_ID)
        val notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled() &&
            channel?.importance != NotificationManager.IMPORTANCE_NONE

        // The live wallpaper or the card picture.
        val wallpaperActive = CardWallpaperService.isActive(this) || LockScreenPicture.isActive(this)

        return SetupStatus(
            notificationsEnabled = notificationsEnabled,
            wallpaperActive = wallpaperActive,
            backgroundAllowed = !isBackgroundRestricted(),
        )
    }

    /**
     * Whether the user restricted the app's background activity ("Restricted" battery usage), which stops the
     * hourly check. The default battery optimization ("Optimized") only delays it a little: that's fine.
     */
    private fun isBackgroundRestricted(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            (getSystemService(ACTIVITY_SERVICE) as ActivityManager).isBackgroundRestricted

    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        )
    }

    private fun openBatterySettings() {
        // The app's info page, where "App battery usage" is.
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }
}
