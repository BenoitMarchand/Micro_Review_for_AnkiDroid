package com.orlikalabs.microreview

import java.text.Normalizer

/**
 * The search bar of Settings: which settings match what the user typed. Every word typed must appear, ignoring
 * case and accents, in the setting's texts or its section's title (so a section title match shows the whole section).
 */
object SettingsSearch {
    private val ACCENTS = Regex("\\p{Mn}+")
    private val WORDS = Regex("\\s+")

    /** Lower case, without accents: "Écran" and "ecran" match. */
    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD).replace(ACCENTS, "").lowercase()

    /** Whether every word of [query] appears in [texts]. A blank query matches everything. */
    fun matches(query: String, texts: List<String>): Boolean {
        val words = normalize(query).split(WORDS).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val haystack = texts.joinToString(" ") { normalize(it) }
        return words.all { it in haystack }
    }

    /** The indexes of the [items] (each one's texts) of a section titled [section] that match [query]. */
    fun visibleItems(query: String, section: String, items: List<List<String>>): List<Int> =
        items.indices.filter { matches(query, listOf(section) + items[it]) }
}
