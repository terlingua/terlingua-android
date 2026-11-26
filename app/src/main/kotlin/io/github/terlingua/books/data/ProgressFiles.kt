package io.github.terlingua.books.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.terlingua.books.domain.ProgressBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset

/** Storage Access Framework and FileProvider I/O for portable progress files. */
class ProgressFiles(private val context: Context) {

    fun suggestedName(): String = "terlingua-${LocalDate.now(ZoneOffset.UTC)}${ProgressBackup.FILE_SUFFIX}"

    suspend fun write(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("Cannot open $uri")
        stream.use { it.write(text.toByteArray()) }
    }

    suspend fun read(uri: Uri): String = withContext(Dispatchers.IO) {
        val stream = context.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")
        stream.use { input ->
            val bytes = input.readNBytesCompat(MAX_BYTES + 1)
            if (bytes.size > MAX_BYTES) throw IOException("File is larger than ${MAX_BYTES / 1_000_000} MB")
            bytes.decodeToString()
        }
    }

    /** Writes a cache copy and returns a content:// URI that can be granted to the share target. */
    suspend fun shareableUri(text: String): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, suggestedName()).apply { writeText(text) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (out.size() < limit) {
            val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    companion object {
        const val MAX_BYTES = 5_000_000
    }
}
