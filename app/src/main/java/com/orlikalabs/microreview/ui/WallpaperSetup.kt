package com.orlikalabs.microreview.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.orlikalabs.microreview.LockScreenPicture
import com.orlikalabs.microreview.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ACTION_WALLPAPER_SETTINGS = "android.settings.WALLPAPER_SETTINGS"

/** Setting the card on the lock screen, shared by the main screen and Settings. */
object WallpaperSetup {
    /**
     * Whether the live wallpaper can be set on the lock screen only: Android 14+ with a "Wallpaper & style" picker
     * (Pixel). Without it (MagicOS, older versions), the system applies a live wallpaper to the home screen too,
     * and some lock screens keep their own picture: the card picture ([LockScreenPicture]) is offered instead.
     */
    fun liveLockOnly(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            context.packageManager.resolveActivity(Intent(ACTION_WALLPAPER_SETTINGS), PackageManager.MATCH_DEFAULT_ONLY) != null

    /** Why to set the card on the lock screen: the live wallpaper or, where it can't be lock only, the picture. */
    fun setupDetail(liveLockOnly: Boolean): Int =
        if (liveLockOnly) R.string.setup_wallpaper_detail else R.string.setup_wallpaper_detail_picture

    fun openSettings(context: Context) {
        // Only the system wallpaper app can apply a live wallpaper to the lock screen alone, so the user picks it
        // there. ACTION_CHANGE_LIVE_WALLPAPER would apply it to both screens.
        try {
            // Public action, but its constant is hidden from the SDK. Opens "Wallpaper & style" on Pixel.
            context.startActivity(Intent(ACTION_WALLPAPER_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            context.startActivity(
                Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), context.getString(R.string.set_lock_wallpaper))
            )
        }
    }

    /** Sets the card picture on the lock screen, and tells whether it worked. */
    suspend fun setPicture(context: Context): Boolean {
        val done = withContext(Dispatchers.IO) { LockScreenPicture.canSet(context) && LockScreenPicture.enable(context) }
        Toast.makeText(context, if (done) R.string.lock_picture_set else R.string.lock_picture_refused, Toast.LENGTH_LONG).show()
        return done
    }
}

/**
 * How to show the card on the lock screen. Where the live wallpaper can be lock only ([liveLockOnly]): its steps,
 * then a button to the wallpaper settings, or the picture instead. Elsewhere: the card picture, or the live
 * wallpaper on both screens. The picture is only set after its explanation (it replaces the lock screen picture).
 */
@Composable
fun WallpaperInstructionsDialog(
    liveLockOnly: Boolean,
    onDismiss: () -> Unit,
    onOpenWallpaperSettings: () -> Unit,
    onSetPicture: () -> Unit,
) {
    var showPicture by rememberSaveable { mutableStateOf(!liveLockOnly) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.set_lock_wallpaper)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (showPicture) {
                    Text(stringResource(R.string.lock_picture_instructions))
                    if (!liveLockOnly) {
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.lock_picture_live_alternative))
                    }
                } else {
                    Text(stringResource(R.string.set_lock_wallpaper_instructions))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                if (showPicture) onSetPicture() else onOpenWallpaperSettings()
            }) { Text(stringResource(if (showPicture) R.string.lock_picture_set_button else R.string.open_wallpaper_settings)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    when {
                        !showPicture -> showPicture = true
                        // Back to the steps of the lock screen only live wallpaper.
                        liveLockOnly -> showPicture = false
                        else -> {
                            onDismiss()
                            onOpenWallpaperSettings()
                        }
                    }
                }) { Text(stringResource(if (showPicture) R.string.live_wallpaper_instead else R.string.lock_picture_instead)) }
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
            }
        },
    )
}
