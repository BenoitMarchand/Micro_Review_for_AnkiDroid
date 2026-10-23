package com.orlikalabs.microreview

/** Display of a license file written for a terminal (lines cut at ~80 columns) on a phone screen. */
object LicenseText {
    /**
     * Joins the lines of each paragraph so the text wraps to the screen width. Paragraphs are separated by blank
     * lines; a paragraph whose lines all start with "Copyright" keeps one line per copyright holder.
     */
    fun reflow(text: String): String = text.replace("\r\n", "\n").trim()
        .split(Regex("\n\\s*\n"))
        .joinToString("\n\n") { paragraph ->
            val lines = paragraph.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.all { it.startsWith("Copyright") }) lines.joinToString("\n") else lines.joinToString(" ")
        }
}
