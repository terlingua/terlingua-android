package io.github.terlingua.books.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.terlingua.books.BuildConfig
import io.github.terlingua.books.R
import io.github.terlingua.books.feedback.rememberFeedbackClick
import io.github.terlingua.books.ui.components.BackButton
import io.github.terlingua.books.ui.components.focusRing

private const val SOURCE_URL = "https://github.com/terlingua/terlingua-android"

private data class Component(val name: String, val license: String)

private val components = listOf(
    Component("Kotlin, kotlinx.coroutines, kotlinx.serialization", "Apache-2.0"),
    Component("AndroidX: Activity, Core, Lifecycle, Compose, Material 3, Material 3 Adaptive", "Apache-2.0"),
    Component("AndroidX: Navigation 3, Room 3, SQLite, DataStore, CameraX", "Apache-2.0"),
    Component("SQLite (bundled, with FTS5)", "Public Domain"),
    Component("ZXing core", "Apache-2.0"),
    Component("Material icons", "Apache-2.0"),
    Component("Sound cues (original, generated for this project)", "AGPL-3.0-only"),
    Component("commonmark-java (build time)", "BSD-2-Clause"),
    Component("SnakeYAML Engine (build time)", "Apache-2.0"),
    Component("SQLite JDBC (build time)", "Apache-2.0"),
    Component("Gradle, Android Gradle Plugin (build time)", "Apache-2.0"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.about)) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(R.string.about_privacy), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
                    Text(stringResource(R.string.about_license_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
                    Text(stringResource(R.string.about_license_body), style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_source)) },
                    supportingContent = { Text(SOURCE_URL) },
                    modifier = Modifier.focusRing().clickable(onClick = rememberFeedbackClick { runCatching { uriHandler.openUri(SOURCE_URL) } }),
                )
            }
            item {
                Text(
                    stringResource(R.string.about_third_party),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                )
            }
            items(components) { c ->
                ListItem(headlineContent = { Text(c.name) }, supportingContent = { Text(c.license) })
            }
        }
    }
}
