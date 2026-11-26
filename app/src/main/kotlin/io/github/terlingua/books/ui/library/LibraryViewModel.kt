package io.github.terlingua.books.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.terlingua.books.domain.BookRepository
import io.github.terlingua.books.domain.BookSummary
import io.github.terlingua.books.domain.Completion
import io.github.terlingua.books.domain.ProgressRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

data class LibraryItem(val book: BookSummary, val progress: Float)

data class LibraryUiState(val loading: Boolean = true, val items: List<LibraryItem> = emptyList(), val error: String? = null)

class LibraryViewModel(books: BookRepository, progress: ProgressRepository) : ViewModel() {
    val state: StateFlow<LibraryUiState> = combine(
        flow { emit(books.books() to books.sectionsByBook()) },
        progress.observeAll(),
    ) { (list, sections), progressBySlug ->
        LibraryUiState(
            loading = false,
            items = list.map { LibraryItem(it, Completion.fraction(sections[it.slug].orEmpty(), progressBySlug[it.slug])) },
        )
    }
        .catch { emit(LibraryUiState(loading = false, error = it.message ?: it::class.simpleName)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())
}
