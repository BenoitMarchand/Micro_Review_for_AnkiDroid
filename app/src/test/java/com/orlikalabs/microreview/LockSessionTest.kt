package com.orlikalabs.microreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockSessionTest {

    @Test
    fun oneReviewPerSessionByDefault() {
        assertEquals(LockSession.AfterAnswer.WAIT_FOR_NEXT_LOCK, LockSession.afterAnswer(wallpaperActive = true, answers = 1, cardsPerUnlock = 1))
    }

    @Test
    fun nextCardComesRightAwayUntilTheSessionsCardsAreReviewed() {
        assertEquals(LockSession.AfterAnswer.SHOW_NEXT, LockSession.afterAnswer(wallpaperActive = true, answers = 1, cardsPerUnlock = 3))
        assertEquals(LockSession.AfterAnswer.SHOW_NEXT, LockSession.afterAnswer(wallpaperActive = true, answers = 2, cardsPerUnlock = 3))
        assertEquals(LockSession.AfterAnswer.WAIT_FOR_NEXT_LOCK, LockSession.afterAnswer(wallpaperActive = true, answers = 3, cardsPerUnlock = 3))
    }

    @Test
    fun noLimitShowsTheNextCardRightAway() {
        assertEquals(LockSession.AfterAnswer.SHOW_NEXT, LockSession.afterAnswer(wallpaperActive = true, answers = 50, cardsPerUnlock = ReviewSettings.NO_LIMIT))
    }

    @Test
    fun withoutTheWallpaperTheNextCardIsShownRightAway() {
        // Without screen events, the next lock can't be detected.
        assertEquals(LockSession.AfterAnswer.SHOW_NEXT, LockSession.afterAnswer(wallpaperActive = false, answers = 1, cardsPerUnlock = 1))
    }

    @Test
    fun notificationIsOnlyPostedWhileLocked() {
        assertTrue(LockSession.mayPost(wallpaperActive = true, locked = true, waitingForNextLock = false))
        assertFalse(LockSession.mayPost(wallpaperActive = true, locked = false, waitingForNextLock = false))
    }

    @Test
    fun nextCardWaitsForTheNextLockAfterTheSessionsReview() {
        assertFalse(LockSession.mayPost(wallpaperActive = true, locked = true, waitingForNextLock = true))
    }

    @Test
    fun withoutTheWallpaperTheNotificationIsAlwaysPosted() {
        assertTrue(LockSession.mayPost(wallpaperActive = false, locked = false, waitingForNextLock = false))
        assertTrue(LockSession.mayPost(wallpaperActive = false, locked = true, waitingForNextLock = true))
    }

    @Test
    fun notificationIsRemovedAtEveryUnlockOnceStarted() {
        assertTrue(LockSession.shouldHideOnUnlock(started = true))
        assertFalse(LockSession.shouldHideOnUnlock(started = false))
    }

    @Test
    fun openAfterUnlockNeverPostsTheNotification() {
        assertFalse(LockSession.mayPost(wallpaperActive = true, locked = true, waitingForNextLock = false, openAfterUnlock = true))
        assertFalse(LockSession.mayPost(wallpaperActive = true, locked = false, waitingForNextLock = false, openAfterUnlock = true))
    }

    @Test
    fun openAfterUnlockWithoutTheWallpaperFallsBackToTheNotification() {
        assertTrue(LockSession.mayPost(wallpaperActive = false, locked = false, waitingForNextLock = false, openAfterUnlock = true))
    }

    @Test
    fun reviewScreenOpensOnceAtUnlockWithACard() {
        assertTrue(LockSession.shouldOpenReview(openAfterUnlock = true, wasLocked = true, hasCard = true, waitingForNextLock = false))
        // The same unlock reported again (USER_PRESENT, then the wallpaper hidden).
        assertFalse(LockSession.shouldOpenReview(openAfterUnlock = true, wasLocked = false, hasCard = true, waitingForNextLock = false))
    }

    @Test
    fun reviewScreenDoesNotOpenWithoutACardOrOnceTheSessionsCardIsAnswered() {
        assertFalse(LockSession.shouldOpenReview(openAfterUnlock = true, wasLocked = true, hasCard = false, waitingForNextLock = false))
        assertFalse(LockSession.shouldOpenReview(openAfterUnlock = true, wasLocked = true, hasCard = true, waitingForNextLock = true))
    }

    @Test
    fun reviewScreenDoesNotOpenInNotificationMode() {
        assertFalse(LockSession.shouldOpenReview(openAfterUnlock = false, wasLocked = true, hasCard = true, waitingForNextLock = false))
    }
}
