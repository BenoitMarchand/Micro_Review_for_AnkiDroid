package com.orlikalabs.microreview

import android.content.Context
import java.time.LocalDate

/**
 * How many cards were reviewed through the app today, and for how many days in a row: shown on the main screen.
 * Only a count, no card data. Reviews done in AnkiDroid itself are not counted. Days are local calendar days.
 */
object ReviewStats {
    /** The stored counts. [lastDay] is the epoch day of the last review, or null if there was none. */
    data class Counts(val lastDay: Long?, val reviewsThatDay: Int, val streakDays: Int)

    /** What to show [today]: reviews done today, and the streak, still alive if the last review was yesterday. */
    data class Summary(val reviewsToday: Int, val streakDays: Int)

    val NONE = Counts(lastDay = null, reviewsThatDay = 0, streakDays = 0)

    /** The counts after one more review on [today] (epoch day). */
    fun record(counts: Counts, today: Long): Counts = when (counts.lastDay) {
        today -> counts.copy(reviewsThatDay = counts.reviewsThatDay + 1)
        today - 1 -> Counts(today, reviewsThatDay = 1, streakDays = counts.streakDays + 1)
        // First review, a day was missed, or the clock went back.
        else -> Counts(today, reviewsThatDay = 1, streakDays = 1)
    }

    fun summary(counts: Counts, today: Long): Summary = when (counts.lastDay) {
        today -> Summary(counts.reviewsThatDay, counts.streakDays)
        today - 1 -> Summary(0, counts.streakDays)
        else -> Summary(0, 0)
    }

    private const val PREFS = "com.ankidroid.companion.review_stats"
    private const val KEY_LAST_DAY = "last_day"
    private const val KEY_REVIEWS = "reviews_that_day"
    private const val KEY_STREAK = "streak_days"

    /** A review was recorded through the app. */
    @Synchronized
    fun onReviewed(context: Context, today: Long = LocalDate.now().toEpochDay()) {
        val counts = record(load(context), today)
        prefs(context).edit()
            .putLong(KEY_LAST_DAY, counts.lastDay!!)
            .putInt(KEY_REVIEWS, counts.reviewsThatDay)
            .putInt(KEY_STREAK, counts.streakDays)
            .apply()
    }

    fun summary(context: Context, today: Long = LocalDate.now().toEpochDay()): Summary = summary(load(context), today)

    private fun load(context: Context): Counts {
        val prefs = prefs(context)
        if (!prefs.contains(KEY_LAST_DAY)) return NONE
        return Counts(prefs.getLong(KEY_LAST_DAY, 0), prefs.getInt(KEY_REVIEWS, 0), prefs.getInt(KEY_STREAK, 0))
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
