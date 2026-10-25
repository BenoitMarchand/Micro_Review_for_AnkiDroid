package com.orlikalabs.microreview.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.orlikalabs.microreview.LicenseText
import com.orlikalabs.microreview.R

/** A library the app ships with, and its license. Proper names, not translated. */
private class Library(val name: String, val license: String)

/** The libraries in the APK. Update it when a dependency is added or removed in `app/build.gradle.kts`. */
private val LIBRARIES = listOf(
    Library("AnkiDroid API (FlashCardsContract, AddContentApi)", "GNU Lesser General Public License v3.0"),
    Library("Android Jetpack: Core, Activity, ExifInterface, Lifecycle, WorkManager", "Apache License 2.0"),
    Library("Jetpack Compose: UI, Material 3, Material Icons", "Apache License 2.0"),
    Library("Kotlin standard library, kotlinx.coroutines", "Apache License 2.0"),
)

/** Settings › Help › Open-source licenses: the app's license (the project's LICENSE file) and the libraries'. */
@Composable
fun LicensesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    // Copied from the project's LICENSE at build time (copyLicense in app/build.gradle.kts).
    val appLicense = remember {
        runCatching { context.assets.open("LICENSE.txt").bufferedReader().use { LicenseText.reflow(it.readText()) } }
            .getOrDefault("")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.licenses_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleSmall)
                Text(appLicense, style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.licenses_libraries), style = MaterialTheme.typography.titleSmall)
                LIBRARIES.forEach {
                    Column {
                        Text(it.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            it.license,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}
