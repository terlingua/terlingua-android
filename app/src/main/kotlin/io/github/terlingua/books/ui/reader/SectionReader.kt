package io.github.terlingua.books.ui.reader

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.terlingua.books.R
import io.github.terlingua.books.domain.Question
import io.github.terlingua.books.domain.SectionContent
import io.github.terlingua.books.feedback.FeedbackOutlinedButton
import io.github.terlingua.books.feedback.FeedbackTonalButton
import io.github.terlingua.books.ui.book.BookViewModel
import io.github.terlingua.books.ui.book.QuizUiState
import io.github.terlingua.books.ui.components.focusRing

private val ReadingWidth = 760.dp

interface QuizActions {
    fun toggle(question: Question, optionId: String)
    fun check(question: Question): Boolean
    fun retry(question: Question)
    fun reveal(question: Question)
}

@Composable
fun SectionReader(
    content: SectionContent,
    quiz: Map<String, QuizUiState>,
    isSolved: (String) -> Boolean,
    actions: QuizActions,
    previousKey: String?,
    nextKey: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val key = content.ref.key
    key(key) {
        val listState = rememberLazyListState()
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .focusRequester(focus)
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyUp) return@onKeyEvent false
                    val target = when (event.key) {
                        Key.DirectionLeft -> previousKey
                        Key.DirectionRight -> nextKey
                        else -> null
                    }
                    target?.let(onNavigate) != null
                }
                .focusable()
                .testTag("reader-$key"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Column(Modifier.widthIn(max = ReadingWidth).fillMaxWidth()) {
                    Text(content.chapterTitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(content.ref.title, style = MaterialTheme.typography.headlineMedium)
                    if (content.ref.readingMinutes > 0) {
                        Text(
                            stringResource(R.string.section_minutes, content.ref.readingMinutes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(content.blocks) { block ->
                BlockView(block, Modifier.widthIn(max = ReadingWidth).fillMaxWidth())
            }
            if (content.questions.isNotEmpty()) {
                item(key = "quiz-title") {
                    Text(
                        stringResource(R.string.quiz_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.widthIn(max = ReadingWidth).fillMaxWidth().padding(top = 16.dp),
                    )
                }
                itemsIndexed(content.questions, key = { _, q -> "q-${q.id}" }) { index, question ->
                    QuizCard(
                        number = index + 1,
                        question = question,
                        state = quiz[BookViewModel.quizKey(key, question)] ?: QuizUiState(),
                        solvedBefore = isSolved(question.id),
                        onToggle = { actions.toggle(question, it) },
                        onCheck = { actions.check(question) },
                        onRetry = { actions.retry(question) },
                        onReveal = { actions.reveal(question) },
                        modifier = Modifier.widthIn(max = ReadingWidth),
                    )
                }
            }
            item(key = "pager") {
                Row(
                    Modifier.widthIn(max = ReadingWidth).fillMaxWidth().padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    previousKey?.let {
                        FeedbackOutlinedButton(onClick = { onNavigate(it) }, modifier = Modifier.focusRing()) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                            Text(stringResource(R.string.section_previous))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    nextKey?.let {
                        FeedbackTonalButton(onClick = { onNavigate(it) }, modifier = Modifier.focusRing().testTag("next-section")) {
                            Text(stringResource(R.string.section_next))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}
