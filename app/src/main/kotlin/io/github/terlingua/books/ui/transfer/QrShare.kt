package io.github.terlingua.books.ui.transfer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.github.terlingua.books.R
import io.github.terlingua.books.domain.BookRepository
import io.github.terlingua.books.domain.ProgressCodec
import io.github.terlingua.books.domain.ProgressRepository
import io.github.terlingua.books.ui.components.BackButton
import io.github.terlingua.books.ui.components.LoadingState
import io.github.terlingua.books.ui.components.MessageState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

sealed interface QrShareUiState {
    data object Loading : QrShareUiState
    data object NoProgress : QrShareUiState
    data object TooLarge : QrShareUiState
    data class Ready(val title: String, val code: ImageBitmap) : QrShareUiState
}

class QrShareViewModel(slug: String, progress: ProgressRepository, books: BookRepository) : ViewModel() {
    val state: StateFlow<QrShareUiState> = flow {
        val title = books.books().firstOrNull { it.slug == slug }?.title ?: slug
        val book = progress.exportBook(slug)
        emit(
            when {
                book == null -> QrShareUiState.NoProgress
                else -> encode(ProgressCodec.encodeQr(book))?.let { QrShareUiState.Ready(title, it) } ?: QrShareUiState.TooLarge
            },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), QrShareUiState.Loading)

    private fun encode(payload: String): ImageBitmap? {
        if (payload.length > ProgressCodec.QR_MAX_LENGTH) return null
        val matrix = try {
            QRCodeWriter().encode(
                payload,
                BarcodeFormat.QR_CODE,
                0,
                0,
                mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 2),
            )
        } catch (_: WriterException) {
            return null
        }
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix[i % matrix.width, i / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
        return Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrShareScreen(viewModel: QrShareViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.qr_title)) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        when (val s = state) {
            QrShareUiState.Loading -> LoadingState(Modifier.padding(padding))
            QrShareUiState.NoProgress -> MessageState(Icons.Filled.Info, stringResource(R.string.qr_no_progress), null, Modifier.padding(padding))
            QrShareUiState.TooLarge -> MessageState(Icons.Filled.Info, stringResource(R.string.qr_too_large), null, Modifier.padding(padding))
            is QrShareUiState.Ready -> Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(s.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                // A white quiet zone keeps the code scannable in dark themes.
                Box(Modifier.widthIn(max = 520.dp).fillMaxWidth().aspectRatio(1f).background(Color.White).padding(12.dp)) {
                    Image(s.code, contentDescription = stringResource(R.string.qr_title), filterQuality = FilterQuality.None, modifier = Modifier.fillMaxSize())
                }
                Text(stringResource(R.string.qr_instructions), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        }
    }
}
