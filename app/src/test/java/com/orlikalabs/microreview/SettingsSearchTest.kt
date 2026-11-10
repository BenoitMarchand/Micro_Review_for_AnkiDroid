package com.orlikalabs.microreview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    private val items = listOf(
        listOf("Cards per unlock", "1 card each time you unlock your phone"),
        listOf("Change the card at every unlock"),
        listOf("How to review", "Notification", "Open after unlock"),
    )

    @Test
    fun aBlankQueryShowsEverything() {
        assertEquals(listOf(0, 1, 2), SettingsSearch.visibleItems("", "Lock screen", items))
        assertEquals(listOf(0, 1, 2), SettingsSearch.visibleItems("   ", "Lock screen", items))
    }

    @Test
    fun onlyTheMatchingSettingsAreShown() {
        assertEquals(listOf(2), SettingsSearch.visibleItems("notif", "Lock screen", items))
        assertEquals(listOf(0, 1, 2), SettingsSearch.visibleItems("unlock", "Lock screen", items))
        assertEquals(emptyList<Int>(), SettingsSearch.visibleItems("widget", "Lock screen", items))
    }

    @Test
    fun aSectionTitleMatchShowsTheWholeSection() {
        assertEquals(listOf(0, 1, 2), SettingsSearch.visibleItems("lock screen", "Lock screen", items))
        // The words may come from the title and the setting.
        assertEquals(listOf(0), SettingsSearch.visibleItems("screen cards per", "Lock screen", items))
    }

    @Test
    fun everyWordMustMatch_inAnyOrder_ignoringCaseAndAccents() {
        assertTrue(SettingsSearch.matches("UNLOCK cards", listOf("Cards per unlock")))
        assertFalse(SettingsSearch.matches("cards widget", listOf("Cards per unlock")))
        assertTrue(SettingsSearch.matches("ecran", listOf("Écran de verrouillage")))
        assertTrue(SettingsSearch.matches("Écran", listOf("ecran")))
    }
}
