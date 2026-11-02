package com.orlikalabs.microreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardPagesTest {
    /** 8 lines without "More", 6 with it, for one text; 7 and 5 for two. */
    private val room = CardPages.Room(height = 90f, moreButton = 20f, textPadding = 10f, lineHeight = 10f)

    /** One line per word. */
    private fun lineEnds(text: String): List<Int> {
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
    fun firstAnswerPageSkipsTheQuestionPages() {
        assertEquals(2, CardPages.firstAnswerPage(pages(words(10, "q"), words(10, "a"))))
        assertEquals(0, CardPages.firstAnswerPage(pages("q", "a")))
        assertEquals(0, CardPages.firstAnswerPage(pages("q", null)))
    }

    @Test
    fun aPageAlwaysHoldsAtLeastOneLine() {
        val tiny = CardPages.Room(height = 10f, moreButton = 20f, textPadding = 10f, lineHeight = 10f)

        assertEquals(3, CardPages.pages(words(3), null, tiny, ::lineEnds).size)
    }

    @Test
    fun anEmptyTextStillMakesAPage() {
        assertEquals(listOf(CardPages.Page("", null)), pages("", null))
    }
}
