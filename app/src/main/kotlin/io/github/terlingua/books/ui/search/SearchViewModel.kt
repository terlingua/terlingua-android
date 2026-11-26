package io.github.terlingua.books.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.terlingua.books.domain.BookRepository
import io.github.terlingua.books.domain.SearchHit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

data class SearchUiState(val query: String = "", val results: List<SearchHit> = emptyList(), val searched: Boolean = false)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val books: BookRepository) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val state: StateFlow<SearchUiState> = _query
        .debounce(200)
        .distinctUntilChanged()
        .mapLatest { q ->
            if (q.isBlank()) SearchUiState(q) else SearchUiState(q, runCatching { books.search(q) }.getOrDefault(emptyList()), searched = true)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) {
        _query.value = value
    }
}
