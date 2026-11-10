package com.orlikalabs.microreview

import com.orlikalabs.microreview.ReviewStats.Counts
import com.orlikalabs.microreview.ReviewStats.Summary
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewStatsTest {
    private val day = 20_000L

    @Test
    fun firstReviewStartsAStreak() {
        assertEquals(Counts(day, 1, 1), ReviewStats.record(ReviewStats.NONE, day))
    }

    @Test
    fun reviewsOfTheSameDayAddUp() {
        val counts = ReviewStats.record(ReviewStats.record(ReviewStats.NONE, day), day)
        assertEquals(Counts(day, 2, 1), counts)
    }

    @Test
    fun nextDayExtendsTheStreak() {
        assertEquals(Counts(day + 1, 1, 4), ReviewStats.record(Counts(day, 12, 3), day + 1))
    }

    @Test
    fun missedDayRestartsTheStreak() {
        assertEquals(Counts(day + 2, 1, 1), ReviewStats.record(Counts(day, 12, 3), day + 2))
    }

    @Test
    fun clockGoingBackRestartsTheCount() {
        assertEquals(Counts(day - 1, 1, 1), ReviewStats.record(Counts(day, 12, 3), day - 1))
    }

    @Test
    fun summaryOfToday() {
        assertEquals(Summary(12, 3), ReviewStats.summary(Counts(day, 12, 3), day))
    }

    @Test
    fun streakFromYesterdayIsStillAlive() {
        assertEquals(Summary(0, 3), ReviewStats.summary(Counts(day, 12, 3), day + 1))
    }

    @Test
    fun streakIsLostAfterAMissedDay() {
        assertEquals(Summary(0, 0), ReviewStats.summary(Counts(day, 12, 3), day + 2))
        assertEquals(Summary(0, 0), ReviewStats.summary(ReviewStats.NONE, day))
    }
}
