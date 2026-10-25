package com.orlikalabs.microreview

import androidx.core.text.HtmlCompat

/** Turns AnkiDroid's card HTML into the plain text shown in the notification and on the lock screen. */
object CardText {
    private val SOUND = Regex("""\[sound:[^\]]*]|\[anki:play:[^\]]*]""")
    private val TYPE_ANSWER = Regex("""\[\[type:[^\]]*]]""")
    // Android's HTML converter drops unknown tags but keeps their content: scripts and styles written in
    // the card template would show as text.
    private val HIDDEN_HTML = Regex("""<(script|style)\b[^>]*>.*?</\1\s*>|<!--.*?-->""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val EXTRA_BLANK_LINES = Regex("\n{3,}")
    private const val OBJECT_REPLACEMENT = '\uFFFC'

    /** Whether the HTML plays a sound. Cards whose question has sound are skipped for now. */
    @JvmStatic
    fun hasSound(html: String?): Boolean = html != null && SOUND.containsMatchIn(html)

    /** Removes the sound and typed-answer tags, which have no text to show. */
    @JvmStatic
    fun stripMediaTags(html: String): String = html.replace(SOUND, "").replace(TYPE_ANSWER, "")

    /** Removes the `<script>` and `<style>` blocks and the HTML comments, which are not shown on a card. */
    @JvmStatic
    fun stripHiddenHtml(html: String): String = html.replace(HIDDEN_HTML, "")

    @JvmStatic
    fun clean(html: String?): String {
        if (html.isNullOrEmpty()) return ""
        val withoutTags = stripMediaTags(stripHiddenHtml(html))
        return HtmlCompat.fromHtml(withoutTags, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
            .replace(OBJECT_REPLACEMENT, ' ') // images
            .replace(EXTRA_BLANK_LINES, "\n\n")
            .trim()
    }
}
