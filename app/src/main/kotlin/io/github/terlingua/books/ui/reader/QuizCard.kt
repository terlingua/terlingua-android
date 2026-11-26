package io.github.terlingua.books.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.terlingua.books.R
import io.github.terlingua.books.domain.Question
import io.github.terlingua.books.feedback.FeedbackButton
import io.github.terlingua.books.feedback.FeedbackEvent
import io.github.terlingua.books.feedback.FeedbackOutlinedButton
import io.github.terlingua.books.feedback.FeedbackTextButton
import io.github.terlingua.books.feedback.LocalFeedback
import io.github.terlingua.books.ui.book.QuizUiState
import io.github.terlingua.books.ui.components.focusRing
import io.github.terlingua.books.ui.theme.LocalReaderColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuizCard(
    number: Int,
    question: Question,
    state: QuizUiState,
    solvedBefore: Boolean,
    onToggle: (String) -> Unit,
    onCheck: () -> Boolean,
    onRetry: () -> Unit,
    onReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val feedback = LocalFeedback.current
    val reader = LocalReaderColors.current
    val colors = MaterialTheme.colorScheme
    val showSolution = state.result == QuizUiState.Result.Correct || state.revealed

    OutlinedCard(modifier.fillMaxWidth().testTag("quiz-${question.id}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$number.", style = MaterialTheme.typography.titleMedium, color = colors.primary)
                InlineText(question.prompt, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (solvedBefore) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.quiz_solved_before), tint = reader.success)
                }
            }
            Text(
                stringResource(if (question.multiple) R.string.quiz_select_many else R.string.quiz_select_one),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                question.options.forEach { option ->
                    val selected = option.id in state.selection
                    val background = when {
                        showSolution && option.correct -> reader.successContainer
                        state.result == QuizUiState.Result.Incorrect && selected && !option.correct -> colors.errorContainer
                        else -> Color.Transparent
                    }
                    val toggle = {
                        feedback.perform(FeedbackEvent.Toggle)
                        onToggle(option.id)
                    }
                    val shape = RoundedCornerShape(10.dp)
                    val interaction = if (question.multiple) {
                        Modifier.toggleable(value = selected, enabled = !state.locked, role = Role.Checkbox, onValueChange = { toggle() })
                    } else {
                        Modifier.selectable(selected = selected, enabled = !state.locked, role = Role.RadioButton, onClick = toggle)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRing(shape)
                            .clip(shape)
                            .background(background)
                            .then(interaction)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag("option-${question.id}-${option.id}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (question.multiple) {
                            Checkbox(checked = selected, onCheckedChange = null, enabled = !state.locked)
                        } else {
                            RadioButton(selected = selected, onClick = null, enabled = !state.locked)
                        }
                        InlineText(option.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }

            val status = when {
                state.result == QuizUiState.Result.Correct -> stringResource(R.string.quiz_correct) to reader.success
                state.revealed -> stringResource(R.string.quiz_revealed) to colors.onSurfaceVariant
                state.result == QuizUiState.Result.Incorrect -> stringResource(R.string.quiz_incorrect) to colors.error
                else -> null
            }
            status?.let { (text, color) ->
                Text(
                    text,
                    color = color,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("quiz-status-${question.id}"),
                )
            }
            if (showSolution && question.explanation.isNotEmpty()) {
                BlocksColumn(
                    question.explanation,
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceContainerHigh)
                        .padding(12.dp),
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    state.locked -> FeedbackTextButton(onClick = onRetry, modifier = Modifier.focusRing()) { Text(stringResource(R.string.quiz_try_again)) }
                    state.result == QuizUiState.Result.Incorrect -> {
                        FeedbackButton(onClick = onRetry, modifier = Modifier.focusRing()) { Text(stringResource(R.string.quiz_try_again)) }
                        FeedbackOutlinedButton(onClick = onReveal, modifier = Modifier.focusRing()) { Text(stringResource(R.string.quiz_show_answer)) }
                    }
                    else -> {
                        // Check gives correct/incorrect cues instead of the generic tap cue.
                        Button(
                            onClick = { feedback.perform(if (onCheck()) FeedbackEvent.Correct else FeedbackEvent.Incorrect) },
                            enabled = state.selection.isNotEmpty(),
                            modifier = Modifier.focusRing().testTag("check-${question.id}"),
                        ) { Text(stringResource(R.string.quiz_check)) }
                        FeedbackTextButton(onClick = onReveal, modifier = Modifier.focusRing()) { Text(stringResource(R.string.quiz_show_answer)) }
                    }
                }
            }
        }
    }
}
