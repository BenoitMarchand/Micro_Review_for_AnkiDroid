package com.orlikalabs.microreview

import com.orlikalabs.microreview.LockScreenState.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockScreenStateTest {

    @Test
    fun liveWallpaperOnTheLockScreenOnly() {
        assertTrue(LockScreenState.isLiveShown(lockLiveIsOurs = true, lockPictureId = 12, homeIsOurs = false))
    }

    @Test
    fun anotherLiveWallpaperOnTheLockScreen() {
        assertFalse(LockScreenState.isLiveShown(lockLiveIsOurs = false, lockPictureId = 12, homeIsOurs = true))
    }

    @Test
    fun homeWallpaperShownOnTheLockScreen() {
        assertTrue(LockScreenState.isLiveShown(lockLiveIsOurs = null, lockPictureId = -1, homeIsOurs = true))
        assertFalse(LockScreenState.isLiveShown(lockLiveIsOurs = null, lockPictureId = -1, homeIsOurs = false))
    }

    @Test
    fun lockScreenWithItsOwnPictureDoesNotShowTheHomeWallpaper() {
        // Seen on MagicOS: the live wallpaper applied to both screens only reaches the home screen.
        assertFalse(LockScreenState.isLiveShown(lockLiveIsOurs = null, lockPictureId = 10, homeIsOurs = true))
    }

    @Test
    fun pictureShownWhileStillTheLockScreenWallpaper() {
        assertTrue(LockScreenState.isPictureShown(enabled = true, setId = 13, lockPictureId = 13))
    }

    @Test
    fun pictureReplacedOrNeverSet() {
        assertFalse(LockScreenState.isPictureShown(enabled = true, setId = 13, lockPictureId = 14))
        assertFalse(LockScreenState.isPictureShown(enabled = true, setId = 0, lockPictureId = 0))
        assertFalse(LockScreenState.isPictureShown(enabled = false, setId = 13, lockPictureId = 13))
    }

    @Test
    fun liveWallpaperWinsOverPicture() {
        assertEquals(Mode.LIVE, LockScreenState.mode(liveShown = true, pictureShown = true))
        assertEquals(Mode.PICTURE, LockScreenState.mode(liveShown = false, pictureShown = true))
        assertEquals(Mode.NONE, LockScreenState.mode(liveShown = false, pictureShown = false))
    }
}
