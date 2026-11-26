package io.github.terlingua.books.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.terlingua.books.R
import io.github.terlingua.books.feedback.FeedbackIconButton
import io.github.terlingua.books.feedback.rememberFeedbackClick
import io.github.terlingua.books.ui.components.Avatar
import io.github.terlingua.books.ui.components.LoadingState
import io.github.terlingua.books.ui.components.MessageState
import io.github.terlingua.books.ui.components.focusRing
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    onOpenBook: (String) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                scrollBehavior = scroll,
                actions = {
                    FeedbackIconButton(onClick = onSearch, modifier = Modifier.focusRing(CircleShape)) {
                        Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search))
                    }
                    FeedbackIconButton(onClick = onSettings, modifier = Modifier.focusRing(CircleShape)) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.error != null -> MessageState(
                Icons.Filled.Warning,
                stringResource(R.string.error_generic, state.error),
                null,
                Modifier.padding(padding),
            )
            state.items.isEmpty() -> MessageState(
                Icons.Filled.Info,
                stringResource(R.string.library_empty_title),
                stringResource(R.string.library_empty_body),
                Modifier.padding(padding).testTag("library-empty"),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 320.dp),
                modifier = Modifier.fillMaxSize().testTag("library-grid"),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(state.items, key = { it.book.slug }) { item -> BookCard(item, onOpenBook) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BookCard(item: LibraryItem, onOpen: (String) -> Unit) {
    val book = item.book
    ElevatedCard(
        onClick = rememberFeedbackClick { onOpen(book.slug) },
        modifier = Modifier.fillMaxWidth().focusRing().testTag("book-${book.slug}"),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleLarge)
            if (book.authors.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    book.authors.take(3).forEach { Avatar(it.avatarAsset, it.name, 24.dp) }
                    Text(
                        book.authors.joinToString { it.name },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (book.summary.isNotBlank()) {
                Text(book.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (book.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    book.tags.forEach { TagPill(it) }
                }
            }
            Text(
                stringResource(R.string.book_stats, book.chapterCount, book.sectionCount, book.readingMinutes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.book_progress, (item.progress * 100).roundToInt()), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun TagPill(tag: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            "#$tag",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
