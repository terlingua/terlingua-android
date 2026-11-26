package io.github.terlingua.books.ui.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldDestinationItem
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.terlingua.books.R
import io.github.terlingua.books.domain.BookToc
import io.github.terlingua.books.domain.Question
import io.github.terlingua.books.feedback.FeedbackButton
import io.github.terlingua.books.feedback.FeedbackIconButton
import io.github.terlingua.books.feedback.rememberFeedbackClick
import io.github.terlingua.books.ui.components.AuthorCard
import io.github.terlingua.books.ui.components.BackButton
import io.github.terlingua.books.ui.components.LoadingState
import io.github.terlingua.books.ui.components.MessageState
import io.github.terlingua.books.ui.components.focusRing
import io.github.terlingua.books.ui.library.TagPill
import io.github.terlingua.books.ui.reader.QuizActions
import io.github.terlingua.books.ui.reader.SectionReader
import io.github.terlingua.books.ui.theme.LocalReaderColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun BookScreen(
    viewModel: BookViewModel,
    initialSection: String?,
    onBack: () -> Unit,
    onShareQr: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val section by viewModel.section.collectAsStateWithLifecycle()
    val quiz by viewModel.quiz.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val navigator = rememberListDetailPaneScaffoldNavigator<String>(
        initialDestinationHistory = listOfNotNull(
            ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.List),
            initialSection?.let { ThreePaneScaffoldDestinationItem(ListDetailPaneScaffoldRole.Detail, it) },
        ),
    )
    val selected = navigator.currentDestination?.contentKey
    val twoPane = navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded &&
        navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded

    LaunchedEffect(selected) { selected?.let(viewModel::open) }
    LaunchedEffect(twoPane, state.toc) {
        if (twoPane && selected == null) state.resumeKey?.let { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, it) }
    }
    val openSection: (String) -> Unit = { key -> scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, key) } }
    val actions = remember(viewModel, section.key) {
        val key = section.key.orEmpty()
        object : QuizActions {
            override fun toggle(question: Question, optionId: String) = viewModel.toggle(key, question, optionId)
            override fun check(question: Question) = viewModel.check(key, question)
            override fun retry(question: Question) = viewModel.retry(key, question)
            override fun reveal(question: Question) = viewModel.reveal(key, question)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.toc?.book?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    BackButton {
                        if (!twoPane && selected != null && navigator.canNavigateBack()) scope.launch { navigator.navigateBack() } else onBack()
                    }
                },
                actions = {
                    if (state.toc != null) {
                        FeedbackIconButton(onClick = onShareQr, modifier = Modifier.focusRing(CircleShape)) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share_progress_qr))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val toc = state.toc
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            toc == null -> MessageState(Icons.Filled.Info, stringResource(R.string.book_not_found), null, Modifier.padding(padding))
            else -> NavigableListDetailPaneScaffold(
                navigator = navigator,
                modifier = Modifier.padding(padding),
                listPane = {
                    AnimatedPane {
                        TableOfContents(toc, state, selected, onOpen = openSection)
                    }
                },
                detailPane = {
                    AnimatedPane {
                        val content = section.content
                        when {
                            selected == null -> MessageState(Icons.Filled.Info, stringResource(R.string.book_select_section), null)
                            section.loading || content == null || content.ref.key != selected -> LoadingState()
                            else -> SectionReader(
                                content = content,
                                quiz = quiz,
                                isSolved = { state.isSolved(content.ref.key, it) },
                                actions = actions,
                                previousKey = state.neighbour(content.ref.key, -1),
                                nextKey = state.neighbour(content.ref.key, 1),
                                onNavigate = openSection,
                            )
                        }
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TableOfContents(toc: BookToc, state: BookUiState, selected: String?, onOpen: (String) -> Unit) {
    val book = toc.book
    val reader = LocalReaderColors.current
    LazyColumn(Modifier.fillMaxSize().testTag("toc"), contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "header") {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (book.summary.isNotBlank()) Text(book.summary, style = MaterialTheme.typography.bodyLarge)
                if (book.tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        book.tags.forEach { TagPill(it) }
                    }
                }
                Text(
                    listOfNotNull(
                        stringResource(R.string.book_stats, book.chapterCount, book.sectionCount, book.readingMinutes),
                        book.published?.let { stringResource(R.string.book_published, it) },
                        book.updated?.let { stringResource(R.string.book_updated, it) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.book_progress, (state.fraction * 100).roundToInt()), style = MaterialTheme.typography.labelMedium)
                }
                state.resumeKey?.let { key ->
                    FeedbackButton(onClick = { onOpen(key) }, modifier = Modifier.focusRing().testTag("resume")) {
                        Text(stringResource(if (state.progress?.lastSectionKey != null) R.string.book_continue else R.string.book_start))
                    }
                }
                book.authors.forEach { AuthorCard(it) }
                Text(stringResource(R.string.book_contents), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
        }
        toc.chapters.forEach { chapter ->
            item(key = "chapter-${chapter.slug}") {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
                    Text(chapter.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    if (chapter.summary.isNotBlank()) {
                        Text(chapter.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(chapter.sections, key = { it.key }) { ref ->
                val isSelected = ref.key == selected
                ListItem(
                    headlineContent = { Text(ref.title) },
                    supportingContent = if (ref.readingMinutes > 0) {
                        { Text(stringResource(R.string.section_minutes, ref.readingMinutes)) }
                    } else {
                        null
                    },
                    trailingContent = if (state.isComplete(ref)) {
                        { Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.section_complete), tint = reader.success) }
                    } else {
                        null
                    },
                    colors = if (isSelected) {
                        ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    } else {
                        ListItemDefaults.colors()
                    },
                    modifier = Modifier
                        .focusRing()
                        .testTag("section-${ref.key}")
                        .clickable(onClick = rememberFeedbackClick { onOpen(ref.key) }),
                )
            }
        }
    }
}
