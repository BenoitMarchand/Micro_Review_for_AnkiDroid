package com.orlikalabs.microreview

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.UnderlineSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** [CardText.answer] on real card HTML: it needs Android's HTML parser, so it runs on a device. */
@RunWith(AndroidJUnit4::class)
class CardTextDeviceTest {

    @Test
    fun answerWithoutMarker_dropsRepeatedQuestion() {
        val question = "Capital of <b>France</b>?"
        assertEquals("Paris", CardText.answer(question, "Capital of <b>France</b>?<br>Paris"))
        assertEquals("Paris", CardText.answer(question, "Capital of <b>France</b>?<hr>Paris"))
        assertEquals("Paris", CardText.answer(question, "Capital of <b>France</b>?<hr id='answer'>Paris"))
        assertEquals("Paris", CardText.answer(question, "<div>Capital of <b>France</b>?</div><div>Paris</div>"))
    }

    @Test
    fun pureAnswer_isUnchanged() {
        assertEquals("Paris", CardText.answer("Capital of France?", "Paris"))
    }

    @Test
    fun clozeAnswer_isUnchanged() {
        assertEquals(
            "Paris is the capital of France",
            CardText.answer(
                "Paris is the capital of <span class=cloze>[...]</span>",
                "Paris is the capital of <span class=cloze>France</span>",
            ),
        )
    }

    @Test
    fun styled_keepsEmphasis_dropsColorsAndSizes() {
        val text = CardText.styled("<b>bold</b> <i>it</i> <u>u</u> H<sub>2</sub>O <font color=red>red</font> <big>big</big>") as Spanned
        assertEquals("bold it u H2O red big", text.toString())
        val spans = text.getSpans(0, text.length, Any::class.java)
        assertEquals(
            setOf(StyleSpan::class.java, UnderlineSpan::class.java, SubscriptSpan::class.java),
            spans.map { it.javaClass }.toSet(),
        )
        val bold = spans.filterIsInstance<StyleSpan>().single { it.style == Typeface.BOLD }
        assertEquals("bold", text.subSequence(text.getSpanStart(bold), text.getSpanEnd(bold)).toString())
    }

    @Test
    fun styled_clozeAnswerIsBold() {
        val text = CardText.styled("Paris is the capital of <span class=cloze>France</span>") as Spanned
        val bold = text.getSpans(0, text.length, StyleSpan::class.java).single()
        assertEquals("France", text.subSequence(text.getSpanStart(bold), text.getSpanEnd(bold)).toString())
    }

    @Test
    fun styledAnswer_keepsStylesAfterRemovingTheQuestion() {
        val text = CardText.styledAnswer("Capital of France?", "Capital of France?<br><b>Paris</b>") as Spanned
        assertEquals("Paris", text.toString())
        assertEquals(1, text.getSpans(0, text.length, StyleSpan::class.java).size)
    }

    @Test
    fun styled_imagesAndBlankLines_likeClean() {
        assertEquals("a\nb", CardText.clean("a<br><br><br><br>b"))
        assertEquals("x", CardText.clean("<img src=a.png> x "))
    }

    @Test
    fun emptyLines_areRemoved_keepingStyles() {
        assertEquals("a\nb\nc", CardText.clean("<p>a</p><p>b</p><div>c</div>"))
        assertEquals("a\nb", CardText.clean("a<br>&nbsp;<br> <br>b"))
        val text = CardText.styled("<p>one</p><p><b>two</b></p>") as Spanned
        assertEquals("one\ntwo", text.toString())
        val bold = text.getSpans(0, text.length, StyleSpan::class.java).single()
        assertEquals("two", text.subSequence(text.getSpanStart(bold), text.getSpanEnd(bold)).toString())
    }

    @Test
    fun horizontalRule_separatesTexts() {
        assertEquals("a\nb", CardText.clean("a<hr>b"))
    }
}
