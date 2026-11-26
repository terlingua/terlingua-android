package io.github.terlingua.books.ui.transfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.terlingua.books.R
import io.github.terlingua.books.domain.ImportMode
import io.github.terlingua.books.feedback.FeedbackEvent
import io.github.terlingua.books.feedback.FeedbackTextButton

@Composable
fun ImportDialog(state: ImportUiState, onConfirm: (ImportMode) -> Unit, onDismiss: () -> Unit) {
    when (state) {
        ImportUiState.Idle -> Unit
        is ImportUiState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.import_title)) },
            text = { Text(stringResource(R.string.import_error, state.message)) },
            confirmButton = { FeedbackTextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        )
        ImportUiState.Done -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.import_title)) },
            text = { Text(stringResource(R.string.import_done)) },
            confirmButton = { FeedbackTextButton(onClick = onDismiss, event = FeedbackEvent.Correct) { Text(stringResource(R.string.close)) } },
        )
        is ImportUiState.Preview -> {
            val preview = state.preview
            AlertDialog(
                modifier = Modifier.testTag("import-dialog"),
                onDismissRequest = onDismiss,
                title = { Text(stringResource(R.string.import_title)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!preview.hasChanges) Text(stringResource(R.string.import_nothing_new))
                        preview.mergeDiffs.zip(preview.replaceDiffs).forEach { (merge, replace) ->
                            Column {
                                val title = state.titles[merge.bookSlug]
                                Text(title ?: merge.bookSlug, style = MaterialTheme.typography.titleSmall)
                                when {
                                    title == null -> Text(stringResource(R.string.import_unknown_book), style = MaterialTheme.typography.labelSmall)
                                    merge.isNewBook -> Text(stringResource(R.string.import_new_book), style = MaterialTheme.typography.labelSmall)
                                }
                                Text(
                                    stringResource(R.string.import_book_line, merge.newlyVisitedSections, merge.newlyCorrectQuestions, merge.changedSelections),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                if (replace.lostCorrectQuestions > 0) {
                                    Text(
                                        stringResource(R.string.import_book_lost, replace.lostCorrectQuestions),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                        Text(stringResource(R.string.import_merge_explained), style = MaterialTheme.typography.bodySmall)
                        Text(stringResource(R.string.import_replace_explained), style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    FeedbackTextButton(onClick = { onConfirm(ImportMode.Merge) }, modifier = Modifier.testTag("import-merge")) {
                        Text(stringResource(R.string.import_merge))
                    }
                },
                dismissButton = {
                    Row {
                        FeedbackTextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                        FeedbackTextButton(onClick = { onConfirm(ImportMode.Replace) }, modifier = Modifier.testTag("import-replace")) {
                            Text(stringResource(R.string.import_replace), color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        }
    }
}
