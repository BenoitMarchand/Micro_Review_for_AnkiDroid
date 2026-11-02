package com.orlikalabs.microreview

/**
 * Splits a card too long for the notification, which can't scroll, into pages read with "More". Kept free of
 * Android classes so it can be unit tested: the laid out lines of a text come from [lineEnds].
 */
object CardPages {
    /** What one page shows; a null text is hidden. */
    data class Page(val question: String?, val answer: String?)

    /**
     * The height available for the text, in any unit: [height] without the "More" button, which takes [moreButton];
     * each text also takes [textPadding] and a line takes [lineHeight].
     */
    data class Room(val height: Float, val moreButton: Float, val textPadding: Float, val lineHeight: Float) {
        /** How many lines fit with [texts] texts on the page (at least 1, so that a page always advances). */
        fun lines(texts: Int, more: Boolean): Int =
            (((height - (if (more) moreButton else 0f) - texts * textPadding) / lineHeight).toInt()).coerceAtLeast(1)
    }

    /**
     * The pages of a card: everything on one page if it fits. Otherwise the question's pages then the answer's; a
     * question short enough stays above each page of the answer, to keep it in sight.
     *
     * @param answer null while the answer is hidden
     * @param lineEnds where each laid out line of a text ends, as offsets in the text
     */
    fun pages(question: String, answer: String?, room: Room, lineEnds: (String) -> List<Int>): List<Page> {
        val questionEnds = lineEnds(question)
        if (answer == null) {
            if (questionEnds.size <= room.lines(1, more = false)) return listOf(Page(question, null))
            return split(question, questionEnds, room.lines(1, more = true)).map { Page(it, null) }
        }
        val answerEnds = lineEnds(answer)
        if (questionEnds.size + answerEnds.size <= room.lines(2, more = false)) return listOf(Page(question, answer))
        val sharedLines = room.lines(2, more = true)
        if (questionEnds.size <= sharedLines / 3) {
            return split(answer, answerEnds, sharedLines - questionEnds.size).map { Page(question, it) }
        }
        val lines = room.lines(1, more = true)
        return split(question, questionEnds, lines).map { Page(it, null) } +
            split(answer, answerEnds, lines).map { Page(null, it) }
    }

    /** The page to start on: the answer's first one, when it was just asked for with "Show answer". */
    fun firstAnswerPage(pages: List<Page>): Int = pages.indexOfFirst { it.answer != null }.coerceAtLeast(0)

    private fun split(text: String, lineEnds: List<Int>, linesPerPage: Int): List<String> {
        if (lineEnds.isEmpty()) return listOf(text)
        return lineEnds.chunked(linesPerPage).mapIndexed { index, chunk ->
            val start = if (index == 0) 0 else lineEnds[index * linesPerPage - 1]
            text.substring(start, chunk.last()).trim()
        }
    }
}
