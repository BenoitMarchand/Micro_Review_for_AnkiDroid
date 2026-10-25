package com.orlikalabs.microreview

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import java.util.concurrent.Executors

/**
 * The card as a static lock screen picture, where the live wallpaper can't be set on the lock screen alone: before
 * Android 14, or without a "Wallpaper & style" picker (MagicOS applies a live wallpaper to the home screen only
 * and keeps its own lock screen picture). Any app may set a static picture on the lock screen only
 * (`WallpaperManager.setBitmap` with `FLAG_LOCK`): the displayed card is drawn with [CardRenderer] and set again
 * each time the card or the lock screen style changes.
 *
 * A picture gets no screen events: the lock session works as without the wallpaper (the notification stays
 * posted, cards follow each other). The user's previous lock screen picture can't be put back: reading it needs a
 * storage permission.
 */
object LockScreenPicture {
    private const val TAG = "BackgroundService"
    private const val PREFS = "com.ankidroid.companion.lock_screen_picture"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_WALLPAPER_ID = "wallpaper_id"
    private const val KEY_CONTENT = "content"

    /** Setting a picture takes about a second: one at a time, off the main thread. */
    private val background = Executors.newSingleThreadExecutor()

    /** Whether the lock screen shows the card picture: the one the app set is still the lock screen's wallpaper. */
    @JvmStatic
    fun isActive(context: Context): Boolean {
        val prefs = prefs(context)
        return LockScreenState.isPictureShown(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            setId = prefs.getInt(KEY_WALLPAPER_ID, 0),
            lockPictureId = lockWallpaperId(context),
        )
    }

    /** Whether the app may change the lock screen picture (not on some managed devices). */
    fun canSet(context: Context): Boolean {
        val manager = WallpaperManager.getInstance(context)
        return manager.isWallpaperSupported && manager.isSetWallpaperAllowed
    }

    /** Sets the card picture on the lock screen and keeps it up to date. Call off the main thread. */
    fun enable(context: Context): Boolean = try {
        set(context.applicationContext, chosen = true)
    } catch (e: Exception) {
        Log.e(TAG, "couldn't set the lock screen picture", e)
        false
    }

    /** Stops updating the picture. The last one stays on the lock screen until the user picks another. */
    fun disable(context: Context) {
        prefs(context).edit().putBoolean(KEY_ENABLED, false).remove(KEY_CONTENT).apply()
    }

    /** The displayed card or the lock screen style changed: sets the picture again in the background, if in use. */
    @JvmStatic
    fun update(context: Context) {
        if (!prefs(context).getBoolean(KEY_ENABLED, false)) return
        val appContext = context.applicationContext
        background.execute {
            try {
                set(appContext, chosen = false)
            } catch (e: Exception) {
                Log.e(TAG, "couldn't update the lock screen picture", e)
            }
        }
    }

    /**
     * Draws the current card and sets it on the lock screen, unless it is already there. [chosen]: the user just
     * asked for it; otherwise the picture is only replaced while it is still ours.
     */
    private fun set(context: Context, chosen: Boolean): Boolean = synchronized(this) {
        val prefs = prefs(context)
        if (!chosen && !isActive(context)) {
            // Another lock screen picture or wallpaper was chosen since: don't replace it.
            Log.i(TAG, "the lock screen picture was changed, no longer updating it")
            disable(context)
            return false
        }
        val style = WallpaperStyle.load(context)
        val content = CardWallpaperService.cardContent(context, waitingForNextLock = false)
        val (width, height) = screenSize(context)
        val key = "$content|$style|${width}x$height"
        if (!chosen && key == prefs.getString(KEY_CONTENT, null)) return true

        val image = if (style.hasImage) WallpaperStyle.loadBackground(context) else null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        CardRenderer(context.resources.displayMetrics.density).draw(Canvas(bitmap), width, height, style, image, content)
        val id = WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK)
        bitmap.recycle()
        if (id <= 0) {
            Log.i(TAG, "the lock screen picture was refused")
            return false
        }
        Log.i(TAG, "lock screen picture set ($id)")
        prefs.edit().putBoolean(KEY_ENABLED, true).putInt(KEY_WALLPAPER_ID, id).putString(KEY_CONTENT, key).apply()
        true
    }

    /** The id of the lock screen's own wallpaper, negative when it shows the home one. */
    fun lockWallpaperId(context: Context): Int =
        WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_LOCK)

    /** The screen size in portrait, as the lock screen is shown on phones. */
    private fun screenSize(context: Context): Pair<Int, Int> {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        val width = minOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
        val height = maxOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)
        return width to height
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
