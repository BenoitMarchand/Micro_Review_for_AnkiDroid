package com.orlikalabs.microreview

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Reviewing the displayed card from the notification buttons.
 * Does ContentProvider I/O: call it off the main thread.
 */
object CardReview {
    private const val TAG = "Notifications"
    private const val PREFS = "com.ankidroid.companion.card_review"

    /** The card whose answer is shown, as "noteId:ord". A new card doesn't match, so the flag clears itself. */
    private const val KEY_REVEALED_CARD = "revealed_card"

    /** The page shown after "More" ([KEY_PAGE]) and its card ([KEY_PAGE_CARD]); another card doesn't match. */
    private const val KEY_PAGE_CARD = "page_card"
    private const val KEY_PAGE = "page"

    /** The intent data of an answer button: the card it answers ([answeredCard]). */
    private const val CARD_SCHEME = "microreview"
    private const val CARD_AUTHORITY = "card"

    /** Answers one at a time: a second tap must see the card stored by the first one ([answer]). */
    private val answerLock = Any()

    /**
     * Answers the displayed card with [button] (one of [AnswerButtons]) and stores the next card. The next card
     * is shown right away, or, once the lock session's cards are reviewed, at the next lock ("Next card at your next unlock").
     * With no card left, the notification is removed.
     *
     * Only if [card], the card the button was shown with, is still the displayed one: otherwise the tap came late
     * (a double tap, a tap queued while the previous answer was being recorded) and would answer a card the user
     * hasn't seen, or the same card twice. It is then ignored.
     *
     * @param countsForSession whether the answer is the lock session's review; not from the home screen widget,
     *   which always moves on to the next card
     * @return what happens next, or null if the answer was ignored
     */
    fun answer(context: Context, button: Int, card: CardSync.CardKey, countsForSession: Boolean = true): LockSession.AfterAnswer? =
        synchronized(answerLock) {
            val anki = AnkiDroidHelper(context)
            val localState = anki.storedState?.takeIf { it.hasCard() }
            if (localState == null || CardSync.CardKey(localState.noteID, localState.cardOrd) != card) {
                Log.i(TAG, "answer for note ${card.noteId}, ord ${card.ord} ignored: it isn't the displayed card any more")
                return null
            }
            Log.i(TAG, "answering note ${localState.noteID}, ord ${localState.cardOrd}")
            // Measured now, before the AnkiDroid queries below.
            val timeTaken = ReviewTimer.timeTaken(context, card)
            val ease = AnswerButtons.easeFor(button, localState.buttonCount)
            if (ease == null) {
                Log.w(TAG, "button $button is not valid for a card with ${localState.buttonCount} buttons")
                return null
            }
            // Also when answered while unlocked (scheduled review): it counts as this session's review.
            val after = if (countsForSession) LockSession.onAnswered(context) else LockSession.AfterAnswer.SHOW_NEXT
            // The card may have been reviewed, buried or deleted in AnkiDroid since it was shown: answering it
            // again would record a second review. Null means it couldn't be checked: answer as before.
            if (anki.isCardDue(localState.deckId, localState.noteID, localState.cardOrd) == false) {
                Log.i(TAG, "card no longer due in AnkiDroid, not answering it")
            } else {
                Log.i(TAG, "time taken: $timeTaken ms")
                anki.reviewCard(localState.noteID, localState.cardOrd, ease, timeTaken)
            }

            // After the session's last review, the next card is stored now but its notification waits for the next lock.
            CardStateController.showNextCard(context, silent = true)
            // Only while locked: unlocked (answered from a scheduled review), the lock screen notification stays hidden.
            // Not with no card left: no next card to announce. The stored card stays if AnkiDroid couldn't be read.
            if (after == LockSession.AfterAnswer.WAIT_FOR_NEXT_LOCK && LockSession.isLocked(context) && anki.storedState?.hasCard() == true) {
                // The answered card stays in the notification, with the message in place of its buttons.
                Notifications.create().showNextCardMessage(context, localState.toCardInfo(), localState.deckName)
            }
            after
        }

    /**
     * [intent], an answer button's, for [card]. The card is in the intent data, so each card gets its own
     * PendingIntent: one created for an earlier card keeps answering that card only.
     */
    fun forCard(intent: Intent, card: CardSync.CardKey): Intent =
        intent.setData(
            Uri.Builder().scheme(CARD_SCHEME).authority(CARD_AUTHORITY)
                .appendPath(card.noteId.toString()).appendPath(card.ord.toString()).build()
        )

    /** The card an answer button's [intent] answers ([forCard]), or null if it has none. */
    fun answeredCard(intent: Intent): CardSync.CardKey? {
        val data = intent.data?.takeIf { it.scheme == CARD_SCHEME && it.authority == CARD_AUTHORITY } ?: return null
        val noteId = data.pathSegments.getOrNull(0)?.toLongOrNull() ?: return null
        val ord = data.pathSegments.getOrNull(1)?.toIntOrNull() ?: return null
        return CardSync.CardKey(noteId, ord)
    }

    /** Shows the answer of the displayed card, in the notification too (updated in place, without alert). */
    fun revealAnswer(context: Context) {
        val state = AnkiDroidHelper(context).storedState?.takeIf { it.hasCard() } ?: return
        // The card is obviously seen: start the timer if no screen event did (it keeps running otherwise).
        ReviewTimer.onCardSeen(context, CardSync.CardKey(state.noteID, state.cardOrd))
        // The pages change with the answer: back to its first one.
        prefs(context).edit().putString(KEY_REVEALED_CARD, key(state.noteID, state.cardOrd)).remove(KEY_PAGE_CARD).apply()
        StandardCardWidget.updateAll(context)
        if (!LockSession.mayPostNow(context)) return
        Notifications.create().showNotification(context, state.toCardInfo(), state.deckName, isSilent = true, replace = false)
    }

    /** "More": the next page of the displayed card, in the notification (updated in place, without alert). */
    fun showNextPage(context: Context) {
        val state = AnkiDroidHelper(context).storedState?.takeIf { it.hasCard() } ?: return
        val card = state.toCardInfo()
        val revealed = isAnswerRevealed(context, card) || isAnswerOnWallpaper(context)
        moveToNextPage(context, card, revealed)
        if (!LockSession.mayPostNow(context)) return
        Notifications.create().showNotification(context, card, state.deckName, isSilent = true, replace = false)
    }

    /** Moves [card] to its next page, back to the first after the last one. The card is obviously seen: as in [revealAnswer]. */
    fun moveToNextPage(context: Context, card: CardInfo, revealed: Boolean) {
        ReviewTimer.onCardSeen(context, CardSync.CardKey(card.noteID, card.cardOrd))
        val pages = Notifications.cardPages(context, card, revealed)
        val next = (currentPage(context, card, pages) + 1) % pages.size
        prefs(context).edit().putString(KEY_PAGE_CARD, key(card.noteID, card.cardOrd)).putInt(KEY_PAGE, next).apply()
    }

    /**
     * The page of [card] to show, among its [pages]. Until "More" is tapped: the answer's first page after "Show
     * answer"; otherwise the first page, also when the wallpaper shows the answer: the question hasn't been read yet.
     */
    fun currentPage(context: Context, card: CardInfo, pages: List<CardPages.Page>): Int {
        val prefs = prefs(context)
        if (prefs.getString(KEY_PAGE_CARD, null) == key(card.noteID, card.cardOrd)) {
            return prefs.getInt(KEY_PAGE, 0).coerceIn(pages.indices)
        }
        return if (isAnswerRevealed(context, card)) CardPages.firstAnswerPage(pages) else 0
    }

    /** Whether the answer of [card] was revealed. */
    fun isAnswerRevealed(context: Context, card: CardInfo): Boolean =
        prefs(context).getString(KEY_REVEALED_CARD, null) == key(card.noteID, card.cardOrd)

    /**
     * Whether the lock screen wallpaper (or the card picture) shows the answer under the question: the
     * notification then shows the answer and its buttons right away, without "Show answer".
     */
    fun isAnswerOnWallpaper(context: Context): Boolean =
        WallpaperStyle.load(context).showAnswer &&
            (CardWallpaperService.isActive(context) || LockScreenPicture.isActive(context))

    private fun key(noteId: Long, ord: Int) = "$noteId:$ord"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
