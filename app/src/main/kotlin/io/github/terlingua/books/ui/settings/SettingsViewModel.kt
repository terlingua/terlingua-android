package io.github.terlingua.books.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.terlingua.books.data.ProgressFiles
import io.github.terlingua.books.domain.BookRepository
import io.github.terlingua.books.domain.BookSummary
import io.github.terlingua.books.domain.ProgressCodec
import io.github.terlingua.books.domain.ProgressRepository
import io.github.terlingua.books.domain.Settings
import io.github.terlingua.books.domain.SettingsRepository
import io.github.terlingua.books.domain.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SettingsMessage {
    data object Exported : SettingsMessage
    data class Failed(val reason: String) : SettingsMessage
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val progress: ProgressRepository,
    private val files: ProgressFiles,
    private val incomingImports: MutableStateFlow<String?>,
    books: BookRepository,
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    val books: StateFlow<List<BookSummary>> = flow { emit(books.books()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<SettingsMessage?>(null)
    val message: StateFlow<SettingsMessage?> = _message.asStateFlow()

    fun suggestedFileName(): String = files.suggestedName()

    fun setTheme(theme: ThemeMode) = launch { settingsRepository.setTheme(theme) }
    fun setSound(enabled: Boolean) = launch { settingsRepository.setSound(enabled) }
    fun setHaptics(enabled: Boolean) = launch { settingsRepository.setHaptics(enabled) }

    fun exportTo(uri: Uri) = launch {
        runCatching { files.write(uri, ProgressCodec.encode(progress.exportAll())) }
            .onSuccess { _message.value = SettingsMessage.Exported }
            .onFailure { _message.value = SettingsMessage.Failed(it.message ?: it::class.simpleName.orEmpty()) }
    }

    suspend fun shareableUri(): Uri = files.shareableUri(ProgressCodec.encode(progress.exportAll()))

    fun importFrom(uri: Uri) = launch {
        runCatching { files.read(uri) }
            .onSuccess { incomingImports.value = it }
            .onFailure { _message.value = SettingsMessage.Failed(it.message ?: it::class.simpleName.orEmpty()) }
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
