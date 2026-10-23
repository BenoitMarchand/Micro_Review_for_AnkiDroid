package com.orlikalabs.microreview

import java.net.URLEncoder

/**
 * The details prefilled in a new GitHub issue. The URL opens the issue form `.github/ISSUE_TEMPLATE/bug_report.yml`,
 * which GitHub fills from the query parameters named after its field ids. The same details also go in the `body`
 * parameter, used when GitHub opens a blank issue instead (the form isn't on the default branch yet, for example).
 */
data class IssueReport(
    val device: String,
    val android: String,
    val appVersion: String,
    val ankiDroidVersion: String,
) {
    /** The new issue URL in [repository] (`owner/name`), or null when the build doesn't know the repository. */
    fun url(repository: String): String? {
        if (!REPOSITORY.matches(repository)) return null
        val fields = listOf(
            "template" to TEMPLATE,
            "device" to device,
            "android" to android,
            "app-version" to appVersion,
            "ankidroid-version" to ankiDroidVersion,
            "body" to body(),
        )
        return "https://github.com/$repository/issues/new?" +
            fields.joinToString("&") { (name, value) -> "$name=${encode(value)}" }
    }

    /** The text of a blank issue: the form's questions, then the details. */
    fun body(): String = """
        |### What happened?
        |
        |
        |
        |### Steps to reproduce
        |
        |
        |
        |### Device and versions
        |- Phone: $device
        |- Android: $android
        |- Micro Review for AnkiDroid: $appVersion
        |- AnkiDroid: $ankiDroidVersion
        """.trimMargin()

    companion object {
        const val TEMPLATE = "bug_report.yml"
        private val REPOSITORY = Regex("[A-Za-z0-9-]+/[A-Za-z0-9._-]+")

        // URLEncoder writes spaces as "+", which GitHub reads back as spaces.
        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    }
}
