package com.orlikalabs.microreview

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.FlashCardsContract
import com.ichi2.anki.api.AddContentApi
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalTime

/**
 * Prepares the Play Store assets (`scripts/store-assets.ps1`), on the real AnkiDroid: a demo deck whose cards
 * are new again at each run, selected in the app, and the 512 px listing icon.
 *
 * Changes the user's data, so it only runs when the script passes `-e storeAssets true`: it's skipped in the
 * device tests and in CI.
 */
@RunWith(AndroidJUnit4::class)
class StoreAssetsSetup {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun onlyWhenAsked() {
        assumeTrue(
            "run by scripts/store-assets.ps1 only",
            InstrumentationRegistry.getArguments().getString("storeAssets") == "true"
        )
    }

    /** Empties the demo deck and fills it again, so every card is new and due (the deck is only for this). */
    @Test
    fun resetDemoDeck() {
        val api = AddContentApi(context)
        val deckId: Long = api.deckList!!.entries.firstOrNull { it.value == DECK }?.key ?: api.addNewDeck(DECK)!!
        val modelId: Long = api.modelList!!.entries.firstOrNull { it.value == MODEL }?.key ?: api.addNewBasicModel(MODEL)!!

        // The notes provider takes an AnkiDroid search as its selection.
        val previous = context.contentResolver.query(
            FlashCardsContract.Note.CONTENT_URI, arrayOf(FlashCardsContract.Note._ID), "deck:\"$DECK\"", null, null
        )?.use { cursor -> List(cursor.count) { cursor.moveToPosition(it); cursor.getLong(0) } }.orEmpty()
        for (noteId in previous) {
            context.contentResolver.delete(ContentUris.withAppendedId(FlashCardsContract.Note.CONTENT_URI, noteId), null, null)
        }
        CARDS.forEach { (country, capital) ->
            api.addNote(modelId, deckId, arrayOf(question(country), capital), setOf(TAG))
        }

        DeckSelection.save(context, listOf(deckId))
        CardStateController.showNextCard(context, silent = true, notifyIfNone = false)
        flush()
        Log.i(LOG_TAG, "demo deck $deckId reset with ${CARDS.size} cards")
    }

    /**
     * The settings the recordings show: one card per unlock reviewed from the notification (lock screen GIF), no
     * scheduled review yet (notification GIF, [scheduleReviewSoon]), and a see-through widget (widget GIF).
     */
    @Test
    fun prepareScenarios() {
        ReviewSettings.setSeveralInARow(context, false)
        ReviewSettings.setRotateOnUnlock(context, false)
        ReviewSettings.setReviewMode(context, ReviewSettings.ReviewMode.NOTIFICATION)
        ScheduledReviewSettings.setEnabled(context, false)
        // A scheduled review notification left from an earlier run would show on the lock screen.
        ScheduledReview.cancel(context)
        val widget = WallpaperStyle.load(context, WallpaperStyle.Target.WIDGET)
        WallpaperStyle.save(
            context,
            widget.copy(seeThrough = true, filter = PictureFilter.DARKEN, filterIntensity = WIDGET_FADE, textColor = Color.WHITE),
            WallpaperStyle.Target.WIDGET,
        )
        flush()
    }

    /**
     * Turns "Reviews at set times" on, at the next minute (the one after if it starts in less than 30 s). Run right
     * before the notification recording; the app sets the alarm when it is opened next (this run force-stops it,
     * which removes its alarms).
     */
    @Test
    fun scheduleReviewSoon() {
        assumeTrue(
            "run by scripts/store-assets.ps1 before the notification recording only",
            InstrumentationRegistry.getArguments().getString("scheduleReview") == "true"
        )
        val now = LocalTime.now()
        val minute = (now.hour * 60 + now.minute + if (now.second < 30) 1 else 2) % (24 * 60)
        ScheduledReviewSettings.setTimes(context, listOf(minute))
        ScheduledReviewSettings.setEnabled(context, true)
        flush()
        Log.i(LOG_TAG, "scheduled review at %02d:%02d".format(minute / 60, minute % 60))
    }

    /**
     * Writes the settings saved with `apply()` before this run ends: `am instrument` then kills the app's process,
     * and a pending write would be lost.
     */
    private fun flush() {
        listOf(
            "com.ankidroid.companion.deck_selection", AnkiDroidHelper.STATE_DB, "com.ankidroid.companion.review_settings",
            "com.ankidroid.companion.scheduled_review_settings", "com.ankidroid.companion.scheduled_review",
            WallpaperStyle.Target.WIDGET.prefs,
        ).forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().commit() }
    }

    /** The launcher icon as the Play Store wants it: 512 x 512, full square (the store applies the mask). */
    @Test
    fun renderListingIcon() {
        val icon = ContextCompat.getDrawable(context, R.mipmap.ic_launcher) as AdaptiveIconDrawable
        val size = 512
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // The layers are 108 dp, of which the inner 72 dp are always visible: that part fills the square.
        val inset = size * 18 / 72
        for (layer in listOfNotNull(icon.background, icon.foreground)) {
            layer.setBounds(-inset, -inset, size + inset, size + inset)
            layer.draw(canvas)
        }
        // App-specific external storage: adb can pull it without run-as.
        val file = File(context.getExternalFilesDir(null), "store/icon-512.png").apply { parentFile!!.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i(LOG_TAG, "icon written to $file")
    }

    private fun question(country: String) = "What is the capital of $country?"

    companion object {
        const val DECK = "World capitals"
        private const val MODEL = "Basic"
        private const val TAG = "micro-review-demo"
        private const val LOG_TAG = "StoreAssets"

        /** How much the see-through widget darkens the home screen wallpaper behind it, in percent. */
        private const val WIDGET_FADE = 45
        private val CARDS = listOf(
            "Japan" to "Tokyo", "Australia" to "Canberra", "Canada" to "Ottawa", "Brazil" to "Brasília",
            "Kenya" to "Nairobi", "Norway" to "Oslo", "Peru" to "Lima", "Egypt" to "Cairo",
            "Vietnam" to "Hanoi", "Portugal" to "Lisbon", "New Zealand" to "Wellington", "Morocco" to "Rabat",
        )
    }
}
