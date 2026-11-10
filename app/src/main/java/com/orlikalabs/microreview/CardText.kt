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
        // Android's converter ignores <hr>: without a line break, the texts on each side would run together.
        val withoutTags = stripMediaTags(stripHiddenHtml(html)).replace(HORIZONTAL_RULE, "<br>")
        return HtmlCompat.fromHtml(withoutTags, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
            .replace(OBJECT_REPLACEMENT, ' ') // images
            .replace(EXTRA_BLANK_LINES, "\n\n")
            .trim()
    }

    private val HORIZONTAL_RULE = Regex("""<hr\b[^>]*>""", RegexOption.IGNORE_CASE)

    /** The answer to show for a card: [clean]ed, without the question repeated at its start ([withoutQuestion]). */
    @JvmStatic
    fun answer(questionHtml: String?, answerHtml: String?): String = withoutQuestion(clean(questionHtml), clean(answerHtml))

    /**
     * Removes the question from the start of the answer. AnkiDroid's "pure" answer is the back side after
     * `<hr id=answer>`; when a note type's back template doesn't have that marker (`{{FrontSide}}<br>{{Back}}`,
     * a plain `<hr>`...), it is the whole back side, which starts with the question.
     *
     * Whitespace differences are ignored, and the question must be followed by whitespace, so a word that merely
     * starts like the question is kept. The answer is returned unchanged when nothing would be left (back side
     * identical to the front) or when it doesn't start with the question (cloze cards: "[...]" vs. the answer).
     */
    fun withoutQuestion(question: String, answer: String): String {
        if (question.isBlank()) return answer
        var q = 0
        var a = 0
        while (q < question.length) {
            when {
                question[q].isWhitespace() -> q++
                a >= answer.length -> return answer
                answer[a].isWhitespace() -> a++
                question[q] == answer[a] -> { q++; a++ }
                else -> return answer
            }
        }
        if (a < answer.length && !answer[a].isWhitespace()) return answer
        return answer.substring(a).trim().ifEmpty { answer }
    }
}
