package io.github.terlingua.books.ui.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.terlingua.books.domain.BookRepository
import io.github.terlingua.books.domain.ImportMode
import io.github.terlingua.books.domain.ImportPreview
import io.github.terlingua.books.domain.ProgressBackup
import io.github.terlingua.books.domain.ProgressCodec
import io.github.terlingua.books.domain.ProgressFormatException
import io.github.terlingua.books.domain.ProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data class Preview(val preview: ImportPreview, val titles: Map<String, String>) : ImportUiState
    data class Error(val message: String) : ImportUiState
    data object Done : ImportUiState
}

/** Reviews progress documents arriving from files, share intents or QR scans before applying them. */
class ImportViewModel(
    private val progress: ProgressRepository,
    private val books: BookRepository,
    private val incoming: MutableStateFlow<String?>,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            incoming.filterNotNull().collect { text ->
                incoming.value = null
                load(text)
            }
        }
    }

    private suspend fun load(text: String) {
        _state.value = try {
            val trimmed = text.trim()
            val backup = if (trimmed.startsWith(ProgressCodec.QR_PREFIX)) {
                ProgressBackup(exportedAt = clock(), books = listOf(ProgressCodec.decodeQr(trimmed)))
            } else {
                ProgressCodec.decode(trimmed)
            }
            ImportUiState.Preview(progress.preview(backup), books.books().associate { it.slug to it.title })
        } catch (e: ProgressFormatException) {
            ImportUiState.Error(e.message.orEmpty())
        }
    }

    fun confirm(mode: ImportMode) {
        val preview = (_state.value as? ImportUiState.Preview)?.preview ?: return
        viewModelScope.launch {
            _state.value = runCatching { progress.apply(preview.backup, mode) }
                .fold({ ImportUiState.Done }, { ImportUiState.Error(it.message ?: it::class.simpleName.orEmpty()) })
        }
    }

    fun dismiss() {
        _state.value = ImportUiState.Idle
    }
}
