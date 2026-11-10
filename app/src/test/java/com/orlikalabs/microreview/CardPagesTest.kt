package com.orlikalabs.microreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardPagesTest {
    /** 8 lines without the page buttons, 6 with them, for one text; 7 and 5 for two. */
    private val room = CardPages.Room(height = 90f, pageButtons = 20f, textPadding = 10f, lineHeight = 10f)

    /** One line per word. */
    private fun lineEnds(text: CharSequence): List<Int> {
        val ends = Regex("\\S+ ?").findAll(text).map { it.range.last + 1 }.toList()
        return ends
    }

    private fun words(count: Int, word: String = "w") = (1..count).joinToString(" ") { "$word$it" }

    private fun pages(question: String, answer: String?) = CardPages.pages(question, answer, room, ::lineEnds)

    @Test
    fun aShortCardFitsOnOnePage() {
        assertEquals(listOf(CardPages.Page("q", null)), pages("q", null))
        assertEquals(listOf(CardPages.Page("q", "a")), pages("q", "a"))
    }

    @Test
    fun aQuestionThatJustFitsNeedsNoMoreButton() {
        assertEquals(1, pages(words(8), null).size)
        assertEquals(2, pages(words(9), null).size)
    }

    @Test
    fun aLongQuestionIsSplitWithoutLosingAWord() {
        val question = words(20)

        val result = pages(question, null)

        assertEquals(4, result.size)
        assertEquals(question, result.joinToString(" ") { it.question!! })
    }

    @Test
    fun aLongQuestionAndAnswerArePagedOneAfterTheOther() {
        val question = words(10, "q")
        val answer = words(10, "a")

        val result = pages(question, answer)

        assertEquals(question, result.mapNotNull { it.question }.joinToString(" "))
        assertEquals(answer, result.mapNotNull { it.answer }.joinToString(" "))
        assertTrue(result.none { it.question != null && it.answer != null })
    }

    @Test
    fun aShortQuestionStaysAboveEachPageOfALongAnswer() {
        val answer = words(12)

        val result = pages("q", answer)

        assertEquals(3, result.size)
        assertTrue(result.all { it.question == "q" })
        assertEquals(answer, result.joinToString(" ") { it.answer!! })
    }

    @Test
    fun allThePagesOfACardHaveTheSameHeight() {
        // The lines a page takes: each text at least its minimum.
        fun height(page: CardPages.Page) =
            maxOf(page.question?.let { lineEnds(it).size } ?: 0, page.questionLines) +
                maxOf(page.answer?.let { lineEnds(it).size } ?: 0, page.answerLines)

        assertEquals(listOf(6, 6, 6, 6), pages(words(20), null).map(::height))
        // The last page has 1 line of the answer.
        assertEquals(listOf(5, 5, 5, 5), pages("q", words(13)).map(::height))
        assertEquals(setOf(6), pages(words(10, "q"), words(9, "a")).map(::height).toSet())
        // Each text keeps its minimum even if it takes fewer lines than measured.
        assertTrue(pages(words(20), null).all { it.questionLines == 6 && it.answerLines == 0 })
        // A card on one page keeps its own height.
        assertEquals(CardPages.Page("q", "a"), pages("q", "a").single())
    }

    @Test
    fun firstAnswerPageSkipsTheQuestionPages() {
        assertEquals(2, CardPages.firstAnswerPage(pages(words(10, "q"), words(10, "a"))))
        assertEquals(0, CardPages.firstAnswerPage(pages("q", "a")))
        assertEquals(0, CardPages.firstAnswerPage(pages("q", null)))
    }

    @Test
    fun aPageAlwaysHoldsAtLeastOneLine() {
        val tiny = CardPages.Room(height = 10f, pageButtons = 20f, textPadding = 10f, lineHeight = 10f)

        assertEquals(3, CardPages.pages(words(3), null, tiny, ::lineEnds).size)
    }

    @Test
    fun anEmptyTextStillMakesAPage() {
        assertEquals(listOf(CardPages.Page("", null)), pages("", null))
    }
}
