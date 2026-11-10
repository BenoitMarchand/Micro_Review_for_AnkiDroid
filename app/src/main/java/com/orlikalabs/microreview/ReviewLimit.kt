package com.orlikalabs.microreview

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.text.format.DateFormat
import android.util.Log
import java.util.Date

/**
 * "At most N cards per hour" ([ReviewSettings.cardsPerHour]): once N answers were recorded in the last 60 minutes,
 * the next card is still found and stored, but the lock screen notification, the wallpaper and the review screen
 * hold it back until the oldest of those answers is an hour old ("Next card at 14:35").
 *
 * Only answers count, from every way to review (the widget and the reviews at set times too, which are never held
 * back themselves: the user opens them on purpose). A swiped-away, rotated or unanswered card doesn't count.
 * "Cards per unlock" still applies: the stricter of the two wins.
 *
 * The card comes back with an inexact alarm; the next lock and the hourly sync catch up if it is late.
 */
object ReviewLimit {
    private const val TAG = "Notifications"
    private const val PREFS = "com.ankidroid.companion.review_limit"
    private const val KEY_ANSWER_TIMES = "answer_times"

    /** When the held card comes back, while one is held: the wallpaper redraws when it changes. */
    const val KEY_HELD_UNTIL = "held_until"

    const val ACTION_RELEASE = "ACTION_REVIEW_LIMIT_RELEASE"

    const val HOUR_MS = 60 * 60 * 1000L

    /** The setting's values, [OFF] first. */
    const val OFF = 0
    val CHOICES = listOf(OFF, 3, 5, 10, 20)

    /** The answer times that still count at [now]: those of the last hour, oldest first. */
    fun recent(times: List<Long>, now: Long): List<Long> =
        // A time after now (the clock went back) would hold cards back for longer than an hour: dropped.
        times.filter { it in (now - HOUR_MS + 1)..now }.sorted()

    /** The answer times after one more answer at [now], keeping only what can still count. */
    fun record(times: List<Long>, now: Long): List<Long> = (recent(times, now) + now).takeLast(CHOICES.max())

    /**
     * When the next card may be shown, if it is held back at [now]: [limit] answers in the last hour. Null when it
     * may be shown now, or with no limit.
     */
    fun heldUntil(times: List<Long>, limit: Int, now: Long): Long? {
        if (limit <= OFF) return null
        val recent = recent(times, now)
        if (recent.size < limit) return null
        // Below the limit again once the limit-th most recent answer is an hour old.
        return recent[recent.size - limit] + HOUR_MS
    }

    /** An answer was recorded (in AnkiDroid) through the app. Call before the next card is stored. */
    @Synchronized
    fun onAnswered(context: Context, now: Long = System.currentTimeMillis()) {
        val times = record(load(context), now)
        val held = heldUntil(times, ReviewSettings.cardsPerHour(context), now)
        prefs(context).edit()
            .putString(KEY_ANSWER_TIMES, times.joinToString(","))
            .apply { if (held != null) putLong(KEY_HELD_UNTIL, held) else remove(KEY_HELD_UNTIL) }
            .apply()
        if (held != null) {
            Log.i(TAG, "hourly limit reached: next card held back until ${Date(held)}")
            scheduleRelease(context, held)
        }
    }

    /** When the next card may be shown, if the hourly limit holds it back now; null otherwise. */
    fun heldUntil(context: Context, now: Long = System.currentTimeMillis()): Long? =
        heldUntil(load(context), ReviewSettings.cardsPerHour(context), now)

    /** "14:35", in the user's time format. */
    fun formatTime(context: Context, time: Long): String = DateFormat.getTimeFormat(context).format(Date(time))

    /** "Next card at 14:35", while a card is held back; null otherwise. */
    fun message(context: Context): String? =
        heldUntil(context)?.let { context.getString(R.string.next_card_at_time, formatTime(context, it)) }

    /** Sets the alarm that brings the held card back (again after a reboot), or cancels it when none is held. */
    fun scheduleRelease(context: Context, at: Long? = heldUntil(context)) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (at == null) {
            alarms.cancel(releaseIntent(context))
            return
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, releaseIntent(context))
    }

    /**
     * The alarm: the held card may come now. Posts it, and lets the wallpaper redraw. Delivered early, it is set
     * again. Does ContentProvider I/O: call it off the main thread.
     */
    fun onRelease(context: Context) {
        val held = heldUntil(context)
        if (held != null) {
            scheduleRelease(context, held)
            return
        }
        Log.i(TAG, "hourly limit: the next card may come now")
        prefs(context).edit().remove(KEY_HELD_UNTIL).apply()
        LockScreenPicture.update(context)
        CardStateController.postHeldCard(context)
    }

    /** The limit was changed in the settings: a card held back by the old one may come now. */
    fun onSettingChanged(context: Context) {
        val held = heldUntil(context)
        if (held != null) {
            prefs(context).edit().putLong(KEY_HELD_UNTIL, held).apply()
            scheduleRelease(context, held)
        } else if (prefs(context).contains(KEY_HELD_UNTIL)) {
            scheduleRelease(context, null)
            Thread { onRelease(context.applicationContext) }.start()
        }
    }

    private fun load(context: Context): List<Long> =
        prefs(context).getString(KEY_ANSWER_TIMES, null).orEmpty()
            .split(',').mapNotNull { it.toLongOrNull() }

    private fun releaseIntent(context: Context): PendingIntent {
        val intent = Intent(context, NotificationReceiver::class.java).setAction(ACTION_RELEASE)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
