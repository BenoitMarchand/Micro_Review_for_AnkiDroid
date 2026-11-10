package com.orlikalabs.microreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewLimitTest {
    private val hour = ReviewLimit.HOUR_MS
    private val minute = 60_000L
    private val now = 10 * hour

    @Test
    fun noLimitNeverHolds() {
        val times = List(30) { now - it * minute }
        assertNull(ReviewLimit.heldUntil(times, ReviewLimit.OFF, now))
    }

    @Test
    fun belowTheLimitDoesNotHold() {
        assertNull(ReviewLimit.heldUntil(listOf(now - 10 * minute, now - 5 * minute), limit = 3, now = now))
    }

    @Test
    fun atTheLimitHoldsUntilTheOldestAnswerIsAnHourOld() {
        val times = listOf(now - 50 * minute, now - 20 * minute, now)
        assertEquals(now + 10 * minute, ReviewLimit.heldUntil(times, limit = 3, now = now))
    }

    @Test
    fun aboveTheLimitWaitsForEnoughAnswersToExpire() {
        // Lowered from 5 to 3 with 4 answers in the hour: two of them must expire.
        val times = listOf(now - 50 * minute, now - 40 * minute, now - 20 * minute, now)
        assertEquals(now + 20 * minute, ReviewLimit.heldUntil(times, limit = 3, now = now))
    }

    @Test
    fun answersOlderThanAnHourDoNotCount() {
        val times = listOf(now - 2 * hour, now - hour, now - 30 * minute, now)
        assertNull(ReviewLimit.heldUntil(times, limit = 3, now = now))
    }

    @Test
    fun releasedExactlyAnHourAfterTheAnswer() {
        val times = listOf(now - 50 * minute, now - 20 * minute, now)
        val until = ReviewLimit.heldUntil(times, limit = 3, now = now)!!
        assertNull(ReviewLimit.heldUntil(times, limit = 3, now = until))
        assertEquals(until, ReviewLimit.heldUntil(times, limit = 3, now = until - 1))
    }

    @Test
    fun answerTimesAfterNowAreDropped() {
        // The clock went back: these must not hold cards back for longer than an hour.
        val times = listOf(now + 3 * hour, now + 2 * hour, now)
        assertNull(ReviewLimit.heldUntil(times, limit = 2, now = now))
    }

    @Test
    fun recordKeepsOnlyTheLastHourSorted() {
        val times = listOf(now - 10 * minute, now - 2 * hour, now - 30 * minute)
        assertEquals(listOf(now - 30 * minute, now - 10 * minute, now), ReviewLimit.record(times, now))
    }

    @Test
    fun recordKeepsAtMostTheLargestChoice() {
        val times = List(40) { now - 40 * minute + it * 10_000L }
        val recorded = ReviewLimit.record(times, now)
        assertEquals(ReviewLimit.CHOICES.max(), recorded.size)
        assertEquals(now, recorded.last())
    }
}
