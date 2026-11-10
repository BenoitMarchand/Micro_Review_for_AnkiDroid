package com.orlikalabs.microreview

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat


class Notifications {
    companion object Factory {
        /** Shown in full on the lock screen, so a card can be answered without unlocking. */
        const val CHANNEL_ID = "lockscreen_review"
        const val NOTIFICATION_ID = 1

        /** The scheduled reviews ([ScheduledReview]). */
        const val SCHEDULED_CHANNEL_ID = "scheduled_review"
        const val ACTION_SHOW_ANSWER = "ACTION_SHOW_ANSWER"
        const val ACTION_PREVIOUS_PAGE = "ACTION_PREVIOUS_PAGE"
        const val ACTION_NEXT_PAGE = "ACTION_NEXT_PAGE"

        /** The user swiped the card notification away (its delete intent, with the card: [CardReview.forCard]). */
        const val ACTION_DISMISSED = "ACTION_DISMISSED"

        /** The answer buttons: followed by the button number, 1 to 4 ([AnswerButtons]), with the card ([CardReview.forCard]). */
        const val ACTION_BUTTON_PREFIX = "ACTION_BUTTON_"

        /** How long "Next card at your next unlock" stays before disappearing. */
        private const val NEXT_CARD_MESSAGE_TIMEOUT_MS = 5_000L

        /** The height left for the card's text in the expanded view, whose maximum is 256 dp, and what it contains. */
        private const val TEXT_ROOM_DP = 120f
        private const val PAGE_BUTTONS_DP = 36f
        private const val TEXT_PADDING_DP = 17f

        /** The texts' padding in the layout (`paddingVertical`, 8 dp). */
        private const val TEXT_VERTICAL_PADDING_DP = 16f

        /** What the system's template takes from the screen's width, at most, and a margin for the question's bolder font. */
        private const val TEMPLATE_MARGINS_DP = 104f

        /**
         * The widest the notifications get, margins included: on a tablet (or a phone in landscape) they are in a
         * narrower panel than the screen (543 dp on a 753 dp wide Honor tablet), whose width SystemUI doesn't reliably
         * expose (Honor's `notification_panel_width` says "full width").
         */
        private const val MAX_PANEL_WIDTH_DP = 560
        private const val TEXT_WIDTH_MARGIN = 0.95f
        private const val LEGACY_CHANNEL_ID = "channel_id"

        /** The pages of [card], its answer included if [revealed], at the current screen width and font size. */
        fun cardPages(context: Context, card: CardInfo, revealed: Boolean): List<CardPages.Page> {
            val density = context.resources.displayMetrics.density
            val paint = textPaint(context)
            val panelWidthDp = minOf(context.resources.configuration.screenWidthDp, MAX_PANEL_WIDTH_DP)
            val width = ((panelWidthDp - TEMPLATE_MARGINS_DP) * density * TEXT_WIDTH_MARGIN).toInt().coerceAtLeast(1)
            fun layout(text: CharSequence) = StaticLayout.Builder.obtain(text, 0, text.length, paint, width).build()
            val room = CardPages.Room(
                height = TEXT_ROOM_DP * density,
                pageButtons = PAGE_BUTTONS_DP * density,
                textPadding = TEXT_PADDING_DP * density,
                lineHeight = lineHeight(paint),
            )
            return CardPages.pages(CardText.styled(card.q), if (revealed) CardText.styledAnswer(card.q, card.a) else null, room) { text ->
                val laidOut = layout(text)
                if (text.isEmpty()) emptyList() else (0 until laidOut.lineCount).map { laidOut.getLineEnd(it) }
            }
        }

        /** The text of the measures: the larger of the question's and the answer's font size. */
        private fun textPaint(context: Context) = TextPaint().apply {
            textSize = maxOf(
                textSize(context, androidx.core.R.style.TextAppearance_Compat_Notification_Title),
                textSize(context, androidx.core.R.style.TextAppearance_Compat_Notification),
            )
        }

        private fun lineHeight(paint: TextPaint) = StaticLayout.Builder.obtain("Ag", 0, 2, paint, Int.MAX_VALUE).build().height.toFloat()

        /**
         * The height in pixels of a text of [lines] measured lines, with its padding; 0 for none. In pixels, not lines:
         * the question's and the answer's fonts have slightly different line heights.
         */
        private fun textHeight(context: Context, lines: Int): Int {
            if (lines == 0) return 0
            val density = context.resources.displayMetrics.density
            return kotlin.math.ceil(lines * lineHeight(textPaint(context)) + TEXT_VERTICAL_PADDING_DP * density).toInt()
        }

        private fun textSize(context: Context, style: Int): Float {
            val attributes = context.obtainStyledAttributes(style, intArrayOf(android.R.attr.textSize))
            try {
                return attributes.getDimension(0, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, context.resources.displayMetrics))
            } finally {
                attributes.recycle()
            }
        }

        fun create(): Notifications = Notifications()

        /** Creates the notification channel. Safe to call repeatedly. */
        fun createChannel(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
            // The lock screen visibility of an existing channel can't be changed: replace the old channel.
            notificationManager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        }

        /** The channel of the scheduled reviews: high importance, so it pops up at the chosen times. */
        fun createScheduledChannel(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                SCHEDULED_CHANNEL_ID,
                context.getString(R.string.scheduled_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.scheduled_channel_description)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Posts the card notification, or the "Bravo" notification when [card] is null. The answer and the answer
     * buttons are only shown once "Show answer" was tapped ([CardReview.isAnswerRevealed]), or right away when the
     * lock screen wallpaper already shows the answer ([CardReview.isAnswerOnWallpaper]).
     *
     * @param replace whether to remove the current notification first, which also closes its expanded view.
     * Pass false to update it in place (e.g. to reveal the answer).
     */
    fun showNotification(context: Context, card: CardInfo?, deckName: String, isSilent: Boolean, replace: Boolean = true) {
        Log.i("Notifications", "showNotification called")
        createChannel(context)
        val builder: NotificationCompat.Builder
        val collapsedView = RemoteViews(context.packageName, R.layout.notification_collapsed)

        // There is a card to show, show a notification with the expanded view.
        if (card != null) {
            LockSession.onNotificationPosted(context)
            val title = context.getString(R.string.notification_title_deck, deckName)
            val revealed = CardReview.isAnswerRevealed(context, card) || CardReview.isAnswerOnWallpaper(context)
            collapsedView.setTextViewText(R.id.textViewCollapsedHeader, CardText.clean(card.q))
            collapsedView.setTextViewText(R.id.textViewCollapsedTitle, title)

            val expandedView = cardView(context, card, revealed, PageActions(ACTION_PREVIOUS_PAGE, ACTION_NEXT_PAGE))
            expandedView.setViewVisibility(R.id.answerButtons, if (revealed) View.VISIBLE else View.GONE)
            expandedView.setViewVisibility(R.id.buttonShowAnswer, if (revealed) View.GONE else View.VISIBLE)
            expandedView.setOnClickPendingIntent(R.id.buttonShowAnswer, createIntent(context, ACTION_SHOW_ANSWER))

            setAnswerButtons(context, expandedView, card, ACTION_BUTTON_PREFIX)

            builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(collapsedView)
                .setCustomBigContentView(expandedView)
                .setContentTitle(title)
                // No tap action: on the lock screen, tapping expands the notification in place, while opening
                // anything would ask to unlock first.
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSilent(isSilent)
                .setOnlyAlertOnce(!replace)
                // Can be swiped away like other notifications; the sync then leaves it until the next lock or card.
                .setDeleteIntent(createIntent(context, ACTION_DISMISSED, CardSync.CardKey(card.noteID, card.cardOrd)))
        } else {
            val title = context.getString(R.string.notification_title)
            collapsedView.setTextViewText(R.id.textViewCollapsedHeader, context.getString(R.string.all_cards_done))
            collapsedView.setTextViewText(R.id.textViewCollapsedTitle, title)

            val expandedView = RemoteViews(context.packageName, R.layout.notification_expanded_empty)
            expandedView.setTextViewText(R.id.textViewEmptyExpandedHeader, context.getString(R.string.all_cards_done))
            expandedView.setTextViewText(R.id.textViewEmptyExpandedContent, context.getString(R.string.all_cards_done_next))

            builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(collapsedView)
                .setCustomBigContentView(expandedView)
                .setContentTitle(title)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSilent(isSilent)
                .setOngoing(false)
        }

        val notification: Notification = builder.build()

        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (replace) {
            notificationManager.cancel(NOTIFICATION_ID) // Cancel the current notification before sending a new one
        }
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * After this session's review: the same notification, updated in place, shows the answered [card] with
     * "Next card at your next unlock" instead of the answer buttons, then disappears by itself.
     */
    fun showNextCardMessage(context: Context, card: CardInfo, deckName: String) {
        createChannel(context)
        val message = context.getString(R.string.next_card_at_next_unlock)
        val title = context.getString(R.string.notification_title_deck, deckName)
        val collapsedView = RemoteViews(context.packageName, R.layout.notification_collapsed)
        collapsedView.setTextViewText(R.id.textViewCollapsedHeader, message)
        collapsedView.setTextViewText(R.id.textViewCollapsedTitle, title)

        val expandedView = cardView(context, card, revealed = true, pageActions = null)
        expandedView.setViewVisibility(R.id.textViewNextCard, View.VISIBLE)
        expandedView.setViewVisibility(R.id.buttonShowAnswer, View.GONE)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsedView)
            .setCustomBigContentView(expandedView)
            .setContentTitle(title)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setTimeoutAfter(NEXT_CARD_MESSAGE_TIMEOUT_MS)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // No cancel first: the notification is updated in place and stays expanded.
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * The scheduled review notification ([ScheduledReview]), separate from the lock screen one. Before Start:
     * "Time for a card" and a Start action, which also shows in the pop-up. After Start: the question and
     * "Show answer", then the answer and the answer buttons once [revealed].
     *
     * @param alert whether to alert (a scheduled time); otherwise it is updated in place, silently
     */
    fun showScheduledReview(context: Context, card: CardInfo, deckName: String, started: Boolean, revealed: Boolean, alert: Boolean) {
        createScheduledChannel(context)
        val title = context.getString(R.string.notification_title_deck, deckName)
        val prompt = context.getString(R.string.scheduled_review_prompt)
        val collapsedView = RemoteViews(context.packageName, R.layout.notification_collapsed)
        collapsedView.setTextViewText(R.id.textViewCollapsedHeader, if (started) CardText.clean(card.q) else prompt)
        collapsedView.setTextViewText(R.id.textViewCollapsedTitle, title)

        val builder = NotificationCompat.Builder(context, SCHEDULED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(collapsedView)
            .setContentTitle(title)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSilent(!alert)
            .setOnlyAlertOnce(!alert)
        if (started) {
            val expandedView = cardView(context, card, revealed, PageActions(ScheduledReview.ACTION_PREVIOUS_PAGE, ScheduledReview.ACTION_NEXT_PAGE))
            expandedView.setViewVisibility(R.id.answerButtons, if (revealed) View.VISIBLE else View.GONE)
            expandedView.setViewVisibility(R.id.buttonShowAnswer, if (revealed) View.GONE else View.VISIBLE)
            expandedView.setOnClickPendingIntent(R.id.buttonShowAnswer, createIntent(context, ScheduledReview.ACTION_SHOW_ANSWER))
            setAnswerButtons(context, expandedView, card, ScheduledReview.ACTION_BUTTON_PREFIX)
            builder.setCustomBigContentView(expandedView)
        } else {
            // A standard action, unlike the buttons of the custom view, is also shown in the pop-up.
            builder.addAction(0, context.getString(R.string.start), createIntent(context, ScheduledReview.ACTION_START))
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // A new scheduled time replaces an unanswered notification, and alerts again.
        if (alert) notificationManager.cancel(ScheduledReview.NOTIFICATION_ID)
        notificationManager.notify(ScheduledReview.NOTIFICATION_ID, builder.build())
    }

    /** Shows [card]'s answer buttons, each sending [actionPrefix] followed by its number, with the card. */
    private fun setAnswerButtons(context: Context, view: RemoteViews, card: CardInfo, actionPrefix: String) {
        val visibleButtons = AnswerButtons.visibleButtons(card.buttonCount)
        val key = CardSync.CardKey(card.noteID, card.cardOrd)
        listOf(R.id.button1, R.id.button2, R.id.button3, R.id.button4).forEachIndexed { index, buttonId ->
            view.setOnClickPendingIntent(buttonId, createIntent(context, actionPrefix + (index + 1), key))
            view.setViewVisibility(buttonId, if (index + 1 in visibleButtons) View.VISIBLE else View.GONE)
        }
    }

    /** The broadcasts of the "Previous" and "Next" buttons. */
    private class PageActions(val previous: String, val next: String)

    /**
     * The expanded view with the current page of [card] ([CardReview.currentPage]) and, for a card of several pages,
     * "Previous" and "Next" sending [pageActions] (none if null); the caller chooses the other buttons.
     */
    private fun cardView(context: Context, card: CardInfo, revealed: Boolean, pageActions: PageActions?): RemoteViews {
        val view = RemoteViews(context.packageName, R.layout.notification_expanded_full)
        val pages = cardPages(context, card, revealed)
        val index = CardReview.currentPage(context, card, pages)
        val page = pages[index]
        view.setTextViewText(R.id.textViewExpandedHeader, page.question ?: "")
        view.setInt(R.id.textViewExpandedHeader, "setMinHeight", textHeight(context, page.questionLines))
        view.setViewVisibility(R.id.textViewExpandedHeader, if (page.question != null) View.VISIBLE else View.GONE)
        view.setTextViewText(R.id.textViewContent, page.answer ?: "")
        view.setInt(R.id.textViewContent, "setMinHeight", textHeight(context, page.answerLines))
        view.setViewVisibility(R.id.textViewContent, if (page.answer != null) View.VISIBLE else View.GONE)
        view.setViewVisibility(R.id.answerDivider, if (page.question != null && page.answer != null) View.VISIBLE else View.GONE)
        if (pageActions != null && pages.size > 1) {
            view.setViewVisibility(R.id.pageButtons, View.VISIBLE)
            view.setTextViewText(R.id.textViewPage, context.getString(R.string.card_page_count, index + 1, pages.size))
            view.setViewVisibility(R.id.buttonPreviousPage, if (index > 0) View.VISIBLE else View.INVISIBLE)
            view.setViewVisibility(R.id.buttonNextPage, if (index < pages.lastIndex) View.VISIBLE else View.INVISIBLE)
            view.setOnClickPendingIntent(R.id.buttonPreviousPage, createIntent(context, pageActions.previous))
            view.setOnClickPendingIntent(R.id.buttonNextPage, createIntent(context, pageActions.next))
        }
        return view
    }

    /** A button's broadcast; with [card] for an answer button ([CardReview.forCard]). */
    private fun createIntent(context: Context, action: String, card: CardSync.CardKey? = null): PendingIntent {
        val intent = Intent(context, NotificationReceiver::class.java)
        intent.action = action
        if (card != null) CardReview.forCard(intent, card)
        // A tap must be handled right away, not wait in the background broadcast queue (busy after boot).
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

}
