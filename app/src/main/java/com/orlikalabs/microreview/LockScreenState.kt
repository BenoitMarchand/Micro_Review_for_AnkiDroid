package com.orlikalabs.microreview

/**
 * How the card reaches the lock screen, from what the system reports. Pure logic: [CardWallpaperService.isActive]
 * and [LockScreenPicture.isActive] feed it.
 */
object LockScreenState {
    enum class Mode {
        /** The card live wallpaper: drives the lock session (one card per unlock, rotation, open after unlock). */
        LIVE,

        /** A static picture of the card, set by the app: the lock session works as without the wallpaper. */
        PICTURE,

        /** The card isn't on the lock screen. */
        NONE,
    }

    fun mode(liveShown: Boolean, pictureShown: Boolean): Mode = when {
        liveShown -> Mode.LIVE
        pictureShown -> Mode.PICTURE
        else -> Mode.NONE
    }

    /**
     * Whether the card live wallpaper is shown on the lock screen.
     *
     * @param lockLiveIsOurs whether the lock screen's own live wallpaper is ours (Android 14+), null if it has none
     * @param lockPictureId the id of the lock screen's own wallpaper, negative when it shows the home one
     * @param homeIsOurs whether the home screen's live wallpaper is ours
     */
    fun isLiveShown(lockLiveIsOurs: Boolean?, lockPictureId: Int, homeIsOurs: Boolean): Boolean = when {
        lockLiveIsOurs != null -> lockLiveIsOurs
        // The lock screen has its own static picture (e.g. MagicOS keeps one when a live wallpaper is applied to
        // both screens): the home wallpaper isn't shown there.
        lockPictureId >= 0 -> false
        else -> homeIsOurs
    }

    /**
     * Whether the lock screen still shows the card picture the app set: [setId] (from `setBitmap`) is still the
     * lock screen's wallpaper ([lockPictureId]). Another picture or wallpaper chosen since has another id.
     */
    fun isPictureShown(enabled: Boolean, setId: Int, lockPictureId: Int): Boolean =
        enabled && setId > 0 && setId == lockPictureId
}
