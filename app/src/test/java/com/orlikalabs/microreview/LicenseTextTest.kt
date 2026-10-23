package com.orlikalabs.microreview

import org.junit.Assert.assertEquals
import org.junit.Test

class LicenseTextTest {

    @Test
    fun joinsTheLinesOfEachParagraph() {
        val text = "MIT License\r\n\r\nPermission is hereby granted,\r\nfree of charge.\r\n\r\nTHE SOFTWARE IS\r\nPROVIDED \"AS IS\".\r\n"
        assertEquals(
            "MIT License\n\nPermission is hereby granted, free of charge.\n\nTHE SOFTWARE IS PROVIDED \"AS IS\".",
            LicenseText.reflow(text),
        )
    }

    @Test
    fun keepsOneLinePerCopyrightHolder() {
        val text = "Copyright (c) 2024 A\nCopyright (c) 2026 B\n\nText"
        assertEquals("Copyright (c) 2024 A\nCopyright (c) 2026 B\n\nText", LicenseText.reflow(text))
    }
}
