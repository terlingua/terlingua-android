package io.github.terlingua.books.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.terlingua.books.R
import io.github.terlingua.books.domain.SearchHit
import io.github.terlingua.books.feedback.rememberFeedbackClick
import io.github.terlingua.books.ui.components.BackButton
import io.github.terlingua.books.ui.components.MessageState
import io.github.terlingua.books.ui.components.focusRing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    query: String,
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onOpen: (SearchHit) -> Unit,
    onBack: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onBack) },
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier.fillMaxWidth().padding(end = 12.dp).focusRequester(focus).testTag("search-field"),
                    )
                },
            )
        },
    ) { padding ->
        when {
            query.isBlank() -> MessageState(Icons.Filled.Search, stringResource(R.string.search_prompt), null, Modifier.padding(padding))
            state.searched && state.results.isEmpty() && state.query == query ->
                MessageState(Icons.Filled.Search, stringResource(R.string.search_no_results, query), null, Modifier.padding(padding))
            else -> LazyColumn(Modifier.fillMaxSize().testTag("search-results"), contentPadding = padding) {
                items(state.results, key = { "${it.kind}-${it.bookSlug}-${it.sectionKey}-${it.title}" }) { hit ->
                    val primary = MaterialTheme.colorScheme.primary
                    ListItem(
                        overlineContent = {
                            Text(
                                stringResource(
                                    when (hit.kind) {
                                        SearchHit.Kind.Book -> R.string.search_kind_book
                                        SearchHit.Kind.Chapter -> R.string.search_kind_chapter
                                        SearchHit.Kind.Section -> R.string.search_kind_section
                                    },
                                ) + " · " + hit.bookTitle,
                            )
                        },
                        headlineContent = { Text(highlighted(hit.title, primary)) },
                        supportingContent = if (hit.snippet.isNotBlank()) {
                            { Text(highlighted(hit.snippet, primary), maxLines = 3) }
                        } else {
                            null
                        },
                        modifier = Modifier.focusRing().clickable(onClick = rememberFeedbackClick { onOpen(hit) }),
                    )
                }
            }
        }
    }
}

private fun highlighted(text: String, color: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    var bold = false
    val chunk = StringBuilder()
    fun flush() {
        if (chunk.isEmpty()) return
        if (bold) withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = color)) { append(chunk.toString()) } else append(chunk.toString())
        chunk.clear()
    }
    text.forEach { c ->
        when (c) {
            SearchHit.HIGHLIGHT_START -> { flush(); bold = true }
            SearchHit.HIGHLIGHT_END -> { flush(); bold = false }
            else -> chunk.append(c)
        }
    }
    flush()
}
