package com.orlikalabs.microreview

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlin.concurrent.thread

/**
 * Handles the notification buttons ("Show answer" and the answer buttons, of the lock screen notification and of
 * the scheduled review) and the scheduled review alarm.
 */
class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i("Notifications", "onReceive called: ${intent?.action}")
        if (context == null)
            return
        val action = intent?.action ?: return
        val task: (Context) -> Unit = when (action) {
            Notifications.ACTION_SHOW_ANSWER -> CardReview::revealAnswer
            ScheduledReview.ACTION_ALARM -> ScheduledReview::onAlarm
            ScheduledReview.ACTION_START -> ScheduledReview::start
            ScheduledReview.ACTION_SHOW_ANSWER -> ScheduledReview::revealAnswer
            else -> answerTask(action, intent) ?: return
        }

        // Looking through several decks can take a while: don't block the main thread.
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        thread {
            try {
                task(appContext)
            } catch (e: Exception) {
                Log.e("Notifications", "could not handle $action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** An answer button of the lock screen notification or of the scheduled review, for the card in [intent]. */
    private fun answerTask(action: String, intent: Intent): ((Context) -> Unit)? {
        val scheduled = action.startsWith(ScheduledReview.ACTION_BUTTON_PREFIX)
        val prefix = if (scheduled) ScheduledReview.ACTION_BUTTON_PREFIX else Notifications.ACTION_BUTTON_PREFIX
        if (!action.startsWith(prefix)) return null
        val button = action.removePrefix(prefix).toIntOrNull()?.takeIf { it in AnswerButtons.AGAIN..AnswerButtons.EASY } ?: return null
        val card = CardReview.answeredCard(intent)
        if (card == null) {
            Log.w("Notifications", "$action without its card, ignored")
            return null
        }
        return if (scheduled) {
            { c -> ScheduledReview.answer(c, button, card) }
        } else {
            { c -> CardReview.answer(c, button, card) }
        }
    }
}
