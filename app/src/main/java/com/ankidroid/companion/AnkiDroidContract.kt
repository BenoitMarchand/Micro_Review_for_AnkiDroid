package com.ankidroid.companion

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

/**
 * The part of AnkiDroid's public ContentProvider API the app uses: its authority, permission, URIs and column
 * names (AnkiDroid's `FlashCardsContract`). Written here rather than taken from AnkiDroid's API library, which
 * isn't published on Maven Central: these names are AnkiDroid's public interface, shared by every app using it,
 * so they don't change without breaking those apps.
 */
object AnkiDroidContract {
    const val AUTHORITY = "com.ichi2.anki.flashcards"

    /** Needed for every read and write. Also declared in the manifest. */
    const val READ_WRITE_PERMISSION = "com.ichi2.anki.permission.READ_WRITE_DATABASE"

    private val AUTHORITY_URI: Uri = Uri.parse("content://$AUTHORITY")

    /** The AnkiDroid app providing the API, or null if none is installed. */
    fun ankiDroidPackageName(context: Context): String? {
        val manager = context.packageManager
        val provider = if (Build.VERSION.SDK_INT >= 33) {
            manager.resolveContentProvider(AUTHORITY, PackageManager.ComponentInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            manager.resolveContentProvider(AUTHORITY, 0)
        }
        return provider?.packageName
    }

    /** Notes. A card's content: `notes/<note id>/cards/<card ord>`, with the [Card] columns. */
    object Note {
        val CONTENT_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "notes")
    }

    object Card {
        const val QUESTION_SIMPLE = "question_simple"
        const val ANSWER_PURE = "answer_pure"
    }

    /** The due cards (query, `limit=?,deckID=?`) and answering one (update). */
    object ReviewInfo {
        val CONTENT_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "schedule")
        const val NOTE_ID = "note_id"
        const val CARD_ORD = "ord"
        const val BUTTON_COUNT = "button_count"
        const val NEXT_REVIEW_TIMES = "next_review_times"
        const val MEDIA_FILES = "media_files"
        const val EASE = "answer_ease"
        const val TIME_TAKEN = "time_taken"
    }

    object Deck {
        val CONTENT_ALL_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "decks")

        /** The deck selected in AnkiDroid: read with a query, changed with an update of [DECK_ID]. */
        val CONTENT_SELECTED_URI: Uri = Uri.withAppendedPath(AUTHORITY_URI, "selected_deck")
        const val DECK_ID = "deck_id"
        const val DECK_NAME = "deck_name"

        /** JSON array of the due counts: [learn, review, new]. */
        const val DECK_COUNTS = "deck_count"
    }
}
