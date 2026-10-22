package com.ankidroid.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder

class IssueReportTest {

    private val report = IssueReport(
        device = "Acme Phone 7",
        android = "14 (API 34)",
        appVersion = "0.3.1",
        ankiDroidVersion = "2.20.1",
    )

    private fun parameters(url: String): Map<String, String> =
        url.substringAfter("?").split("&").associate {
            val (name, value) = it.split("=", limit = 2)
            name to URLDecoder.decode(value, "UTF-8")
        }

    @Test
    fun opensTheIssueFormWithTheFieldsFilledIn() {
        val url = report.url("owner/AnkiDroid-Companion")!!
        assertTrue(url.startsWith("https://github.com/owner/AnkiDroid-Companion/issues/new?template=bug_report.yml&"))
        assertTrue(url.contains("&device=Acme+Phone+7&android=14+%28API+34%29&app-version=0.3.1&ankidroid-version=2.20.1&"))
    }

    @Test
    fun aBlankIssueGetsTheDetailsInItsText() {
        val body = parameters(report.url("owner/repo")!!).getValue("body")
        assertEquals(report.body(), body)
        assertTrue(
            body.endsWith(
                "### Device and versions\n" +
                    "- Phone: Acme Phone 7\n" +
                    "- Android: 14 (API 34)\n" +
                    "- Micro Review for AnkiDroid: 0.3.1\n" +
                    "- AnkiDroid: 2.20.1",
            ),
        )
        assertTrue(body.startsWith("### What happened?\n"))
    }

    @Test
    fun encodesCharactersThatWouldBreakTheQuery() {
        val url = report.copy(device = "A&B=C #1").url("owner/repo")!!
        assertEquals("device=A%26B%3DC+%231", url.split("&")[1])
        assertEquals("A&B=C #1", parameters(url).getValue("device"))
    }

    @Test
    fun noUrlWithoutAValidRepository() {
        assertNull(report.url(""))
        assertNull(report.url("owner"))
        assertNull(report.url("https://github.com/owner/repo"))
    }
}
