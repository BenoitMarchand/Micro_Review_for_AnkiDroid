package com.orlikalabs.microreview

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
    fun horizontalRule_separatesTexts() {
        assertEquals("a\nb", CardText.clean("a<hr>b"))
    }
}
