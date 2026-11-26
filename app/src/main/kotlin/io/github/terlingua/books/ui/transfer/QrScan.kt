package io.github.terlingua.books.ui.transfer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import io.github.terlingua.books.R
import io.github.terlingua.books.domain.ProgressCodec
import io.github.terlingua.books.feedback.FeedbackButton
import io.github.terlingua.books.ui.components.BackButton
import io.github.terlingua.books.ui.components.MessageState
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScanScreen(onResult: (String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted && !asked) launcher.launch(Manifest.permission.CAMERA) }
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.qr_scan_title)) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        when {
            !hasCamera -> MessageState(Icons.Filled.Warning, stringResource(R.string.qr_no_camera), null, Modifier.padding(padding))
            !granted -> MessageState(Icons.Filled.Warning, stringResource(R.string.qr_camera_denied), null, Modifier.padding(padding)) {
                FeedbackButton(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text(stringResource(R.string.qr_grant)) }
            }
            else -> CameraPreview(onResult, Modifier.fillMaxSize().padding(padding))
        }
    }
}

@Composable
private fun CameraPreview(onResult: (String) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResult by rememberUpdatedState(onResult)
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(lifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { a ->
                a.setAnalyzer(executor, QrAnalyzer { text -> ContextCompat.getMainExecutor(context).execute { currentOnResult(text) } })
            }
        val selector = if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

/** Decodes the luminance (Y) plane with ZXing; no Google Play Services involved. */
private class QrAnalyzer(private val onFound: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = QRCodeReader()
    private val hints = mapOf(DecodeHintType.TRY_HARDER to true)
    @Volatile private var done = false

    override fun analyze(image: ImageProxy) {
        image.use {
            if (done) return
            val plane = image.planes[0]
            val buffer = plane.buffer
            val data = ByteArray(plane.rowStride * image.height)
            buffer.get(data, 0, minOf(buffer.remaining(), data.size))
            val source = PlanarYUVLuminanceSource(data, plane.rowStride, image.height, 0, 0, image.width, image.height, false)
            try {
                val text = reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
                if (text.startsWith(ProgressCodec.QR_PREFIX)) {
                    done = true
                    onFound(text)
                }
            } catch (_: ReaderException) {
                // No QR code in this frame.
            } finally {
                reader.reset()
            }
        }
    }
}
