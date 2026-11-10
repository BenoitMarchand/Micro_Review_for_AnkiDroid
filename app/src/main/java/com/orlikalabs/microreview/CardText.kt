package com.orlikalabs.microreview

import android.text.SpannableStringBuilder
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.UnderlineSpan
import androidx.core.text.HtmlCompat

/**
 * Turns AnkiDroid's card HTML into the text shown in the notification, the widget, the review screen and on the lock
 * screen: [styled] keeps the emphasis (bold, italics...), [clean] is the plain text.
 */
object CardText {
    private val SOUND = Regex("""\[sound:[^\]]*]|\[anki:play:[^\]]*]""")
    private val TYPE_ANSWER = Regex("""\[\[type:[^\]]*]]""")
    // Android's HTML converter drops unknown tags but keeps their content: scripts and styles written in
    // the card template would show as text.
    private val HIDDEN_HTML = Regex("""<(script|style)\b[^>]*>.*?</\1\s*>|<!--.*?-->""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val HORIZONTAL_RULE = Regex("""<hr\b[^>]*>""", RegexOption.IGNORE_CASE)
    // The cloze being asked ("[...]" in the question, the hidden text in the answer): `class=cloze`, not `cloze-inactive`.
    private val CLOZE = Regex(
        """<span\b[^>]*\bclass\s*=\s*(["']?)cloze\1(?=[\s>])[^>]*>(.*?)</span\s*>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val EXTRA_BLANK_LINES = Regex("\n{3,}")
    private const val OBJECT_REPLACEMENT = '￼'

    /** Whether the HTML plays a sound. Cards whose question has sound are skipped for now. */
    @JvmStatic
    fun hasSound(html: String?): Boolean = html != null && SOUND.containsMatchIn(html)

    /** Removes the sound and typed-answer tags, which have no text to show. */
    @JvmStatic
    fun stripMediaTags(html: String): String = html.replace(SOUND, "").replace(TYPE_ANSWER, "")

    /** Removes the `<script>` and `<style>` blocks and the HTML comments, which are not shown on a card. */
    @JvmStatic
    fun stripHiddenHtml(html: String): String = html.replace(HIDDEN_HTML, "")

    /**
     * The HTML prepared for Android's converter: hidden blocks and media tags removed, `<hr>` turned into a line break
     * (the converter ignores it: the texts on each side would run together) and the cloze made bold (the card's CSS,
     * which highlights it, isn't applied).
     */
    fun prepare(html: String): String =
        stripMediaTags(stripHiddenHtml(html)).replace(HORIZONTAL_RULE, "<br>").replace(CLOZE, "<b>$2</b>")

    /** The plain text of the card HTML. */
    @JvmStatic
    fun clean(html: String?): String = styled(html).toString()

    /**
     * The text of the card HTML with its emphasis: bold, italics, underline, strikethrough, subscript and superscript.
     * Colors, fonts and sizes are dropped: they may be unreadable on the notification or the chosen card style.
     */
    @JvmStatic
    fun styled(html: String?): CharSequence {
        if (html.isNullOrEmpty()) return ""
        val text = SpannableStringBuilder(HtmlCompat.fromHtml(prepare(html), HtmlCompat.FROM_HTML_MODE_LEGACY))
        text.getSpans(0, text.length, Any::class.java).filterNot(::isEmphasis).forEach(text::removeSpan)
        // Images: their placeholder character becomes a space. One character for one, so the spans stay in place.
        for (i in text.indices) if (text[i] == OBJECT_REPLACEMENT) text.replace(i, i + 1, " ")
        EXTRA_BLANK_LINES.findAll(text.toString()).toList().asReversed().forEach { text.replace(it.range.first, it.range.last + 1, "\n\n") }
        return text.trim()
    }

    private fun isEmphasis(span: Any): Boolean =
        span is StyleSpan || span is UnderlineSpan || span is StrikethroughSpan || span is SubscriptSpan || span is SuperscriptSpan

    /** The answer to show for a card: [clean]ed, without the question repeated at its start ([withoutQuestion]). */
    @JvmStatic
    fun answer(questionHtml: String?, answerHtml: String?): String = withoutQuestion(clean(questionHtml), clean(answerHtml)).toString()

    /** The answer to show for a card, [styled], without the question repeated at its start ([withoutQuestion]). */
    @JvmStatic
    fun styledAnswer(questionHtml: String?, answerHtml: String?): CharSequence = withoutQuestion(clean(questionHtml), styled(answerHtml))

    /**
     * Removes the question from the start of the answer. AnkiDroid's "pure" answer is the back side after
     * `<hr id=answer>`; when a note type's back template doesn't have that marker (`{{FrontSide}}<br>{{Back}}`,
     * a plain `<hr>`...), it is the whole back side, which starts with the question.
     *
     * Whitespace differences are ignored, and the question must be followed by whitespace, so a word that merely
     * starts like the question is kept. The answer is returned unchanged when nothing would be left (back side
     * identical to the front) or when it doesn't start with the question (cloze cards: "[...]" vs. the answer).
     * A styled answer keeps its styles ([CharSequence.subSequence]).
     */
    fun withoutQuestion(question: CharSequence, answer: CharSequence): CharSequence {
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
        return answer.subSequence(a, answer.length).trim().ifEmpty { answer }
    }
}
