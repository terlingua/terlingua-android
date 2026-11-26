package io.github.terlingua.books.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.terlingua.books.R
import io.github.terlingua.books.domain.ProgressBackup
import io.github.terlingua.books.domain.ThemeMode
import io.github.terlingua.books.feedback.FeedbackEvent
import io.github.terlingua.books.feedback.FeedbackTextButton
import io.github.terlingua.books.feedback.LocalFeedback
import io.github.terlingua.books.feedback.rememberFeedbackClick
import io.github.terlingua.books.ui.components.BackButton
import io.github.terlingua.books.ui.components.focusRing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onShowQr: (String) -> Unit,
    onScanQr: () -> Unit,
    onAbout: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val books by viewModel.books.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val feedback = LocalFeedback.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var pickBook by rememberSaveable { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ProgressBackup.MIME_TYPE)) { uri ->
        uri?.let(viewModel::exportTo)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importFrom)
    }

    val exported = stringResource(R.string.export_done)
    LaunchedEffect(message) {
        when (val m = message) {
            SettingsMessage.Exported -> snackbar.showSnackbar(exported)
            is SettingsMessage.Failed -> snackbar.showSnackbar(context.resources.getString(R.string.export_failed, m.reason))
            null -> Unit
        }
        if (message != null) viewModel.consumeMessage()
    }
    val chooserTitle = stringResource(R.string.share_chooser)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings)) }, navigationIcon = { BackButton(onBack) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().testTag("settings"), contentPadding = padding) {
            item { Header(stringResource(R.string.settings_appearance)) }
            items(ThemeMode.entries) { mode ->
                val selected = settings.theme == mode
                ListItem(
                    headlineContent = { Text(stringResource(mode.label())) },
                    leadingContent = { RadioButton(selected = selected, onClick = null) },
                    modifier = Modifier
                        .focusRing()
                        .selectable(selected = selected, role = Role.RadioButton) {
                            feedback.perform(FeedbackEvent.Toggle)
                            viewModel.setTheme(mode)
                        }
                        .testTag("theme-${mode.name}"),
                )
            }
            item { Header(stringResource(R.string.settings_feedback)) }
            item {
                SwitchRow(stringResource(R.string.settings_sound), stringResource(R.string.settings_sound_summary), settings.sound) {
                    viewModel.setSound(it)
                }
            }
            item {
                SwitchRow(stringResource(R.string.settings_haptics), stringResource(R.string.settings_haptics_summary), settings.haptics) {
                    viewModel.setHaptics(it)
                }
            }
            item { Header(stringResource(R.string.settings_progress)) }
            item {
                ActionRow(stringResource(R.string.settings_export), stringResource(R.string.settings_export_summary)) {
                    exportLauncher.launch(viewModel.suggestedFileName())
                }
            }
            item {
                ActionRow(stringResource(R.string.settings_share), stringResource(R.string.settings_share_summary)) {
                    scope.launch {
                        val uri = viewModel.shareableUri()
                        val send = Intent(Intent.ACTION_SEND)
                            .setType(ProgressBackup.MIME_TYPE)
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        try {
                            context.startActivity(Intent.createChooser(send, chooserTitle))
                        } catch (_: ActivityNotFoundException) {
                            snackbar.showSnackbar(context.resources.getString(R.string.export_failed, "no app can receive files"))
                        }
                    }
                }
            }
            item {
                ActionRow(stringResource(R.string.settings_import), stringResource(R.string.settings_import_summary)) {
                    importLauncher.launch(arrayOf(ProgressBackup.MIME_TYPE, "application/octet-stream", "text/plain", "*/*"))
                }
            }
            item {
                ActionRow(stringResource(R.string.settings_qr_show), stringResource(R.string.settings_qr_summary)) { pickBook = true }
            }
            item { ActionRow(stringResource(R.string.settings_qr_scan), stringResource(R.string.settings_qr_summary), onScanQr) }
            item { Header(stringResource(R.string.about)) }
            item { ActionRow(stringResource(R.string.about), stringResource(R.string.about_privacy), onAbout) }
        }
    }

    if (pickBook) {
        AlertDialog(
            onDismissRequest = { pickBook = false },
            title = { Text(stringResource(R.string.settings_pick_book)) },
            text = {
                if (books.isEmpty()) {
                    Text(stringResource(R.string.settings_no_books))
                } else {
                    LazyColumn {
                        items(books, key = { it.slug }) { book ->
                            ListItem(
                                headlineContent = { Text(book.title) },
                                modifier = Modifier.focusRing().clickable(onClick = rememberFeedbackClick {
                                    pickBook = false
                                    onShowQr(book.slug)
                                }),
                            )
                        }
                    }
                }
            },
            confirmButton = { FeedbackTextButton(onClick = { pickBook = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun ThemeMode.label(): Int = when (this) {
    ThemeMode.System -> R.string.theme_system
    ThemeMode.Light -> R.string.theme_light
    ThemeMode.Dark -> R.string.theme_dark
    ThemeMode.Sepia -> R.string.theme_sepia
    ThemeMode.SolarizedLight -> R.string.theme_solarized_light
    ThemeMode.SolarizedDark -> R.string.theme_solarized_dark
    ThemeMode.HighContrast -> R.string.theme_high_contrast
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val feedback = LocalFeedback.current
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.focusRing().toggleable(value = checked, role = Role.Switch) {
            feedback.perform(FeedbackEvent.Toggle)
            onChange(it)
        },
    )
}

@Composable
private fun ActionRow(title: String, summary: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        modifier = Modifier.focusRing().clickable(onClick = rememberFeedbackClick(onClick = onClick)),
    )
}
