package io.github.terlingua.books.domain

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class ProgressFormatException(message: String, cause: Throwable? = null) : Exception(message, cause)

object ProgressCodec {
    /** Prefix identifying a single-book QR payload: gzip-compressed JSON, Base64 encoded. */
    const val QR_PREFIX = "TLP1:"

    /** Byte-mode capacity of a version 40 QR code at error correction level L. */
    const val QR_MAX_LENGTH = 2953

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val pretty = Json(json) { prettyPrint = true }

    fun encode(backup: ProgressBackup): String = pretty.encodeToString(ProgressBackup.serializer(), backup)

    fun decode(text: String): ProgressBackup {
        val backup = try {
            json.decodeFromString(ProgressBackup.serializer(), text.trim().removePrefix("﻿"))
        } catch (e: SerializationException) {
            throw ProgressFormatException("Not a Terlingua progress file", e)
        } catch (e: IllegalArgumentException) {
            throw ProgressFormatException("Not a Terlingua progress file", e)
        }
        if (backup.format != ProgressBackup.FORMAT) throw ProgressFormatException("Unknown format '${backup.format}'")
        if (backup.schemaVersion > ProgressBackup.SCHEMA_VERSION) {
            throw ProgressFormatException("This file needs a newer app version (schema ${backup.schemaVersion})")
        }
        return backup
    }

    fun encodeQr(book: BookProgress): String {
        val raw = json.encodeToString(BookProgress.serializer(), book).toByteArray()
        val gz = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(raw) } }.toByteArray()
        return QR_PREFIX + Base64.getEncoder().withoutPadding().encodeToString(gz)
    }

    fun decodeQr(payload: String): BookProgress {
        if (!payload.startsWith(QR_PREFIX)) throw ProgressFormatException("Not a Terlingua progress QR code")
        return try {
            val gz = Base64.getDecoder().decode(payload.removePrefix(QR_PREFIX).trim())
            val raw = GZIPInputStream(ByteArrayInputStream(gz)).use { it.readBytes() }
            json.decodeFromString(BookProgress.serializer(), raw.decodeToString()).also {
                if (it.schemaVersion > ProgressBackup.SCHEMA_VERSION) throw ProgressFormatException("Newer schema ${it.schemaVersion}")
            }
        } catch (e: ProgressFormatException) {
            throw e
        } catch (e: Exception) {
            throw ProgressFormatException("Damaged progress QR code", e)
        }
    }
}

object QuizEvaluator {
    fun isCorrect(question: Question, selection: Set<String>): Boolean = selection == question.correctIds
}

object SearchQuery {
    private val TERM = Regex("""[\p{L}\p{N}_]+""")

    /** Converts free text into a safe FTS5 prefix query, or null when nothing searchable remains. */
    fun toFts(input: String): String? {
        val terms = TERM.findAll(input.lowercase()).map { it.value }.distinct().take(8).toList()
        return terms.takeIf { it.isNotEmpty() }?.joinToString(" ") { "\"$it\"*" }
    }
}
