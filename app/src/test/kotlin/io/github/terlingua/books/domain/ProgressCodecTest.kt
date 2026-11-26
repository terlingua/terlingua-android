package io.github.terlingua.books.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressCodecTest {

    private val book = BookProgress(
        bookSlug = "sample-book",
        lastSectionKey = "sample-book/basics/introduction",
        updatedAt = 1_700_000_000_000,
        sections = listOf(
            SectionProgress(
                key = "sample-book/basics/introduction",
                firstVisitedAt = 1_700_000_000_000,
                updatedAt = 1_700_000_000_500,
                questions = listOf(QuestionProgress("runtime", true, listOf("b"), 1_700_000_000_400, 1_700_000_000_400)),
            ),
        ),
    )

    @Test
    fun backupRoundTripsWithSchemaVersion() {
        val text = ProgressCodec.encode(ProgressBackup(exportedAt = 1L, books = listOf(book)))
        assertTrue(text.contains("\"schemaVersion\": 1"))
        assertTrue(text.contains("\"format\": \"terlingua-progress\""))
        assertEquals(listOf(book), ProgressCodec.decode(text).books)
    }

    @Test
    fun ignoresUnknownFieldsFromFutureMinorVersions() {
        val text = """{"schemaVersion":1,"format":"terlingua-progress","exportedAt":1,"extra":true,"books":[]}"""
        assertTrue(ProgressCodec.decode(text).books.isEmpty())
    }

    @Test(expected = ProgressFormatException::class)
    fun rejectsNewerSchema() {
        ProgressCodec.decode("""{"schemaVersion":2,"format":"terlingua-progress","exportedAt":1,"books":[]}""")
    }

    @Test(expected = ProgressFormatException::class)
    fun rejectsForeignJson() {
        ProgressCodec.decode("""{"hello":"world"}""")
    }

    @Test
    fun qrPayloadIsCompactAndRoundTrips() {
        val payload = ProgressCodec.encodeQr(book)
        assertTrue(payload.startsWith(ProgressCodec.QR_PREFIX))
        assertTrue(payload.length < ProgressCodec.QR_MAX_LENGTH)
        assertFalse(payload.contains('\n'))
        assertEquals(book, ProgressCodec.decodeQr(payload))
    }

    @Test(expected = ProgressFormatException::class)
    fun rejectsDamagedQrPayload() {
        ProgressCodec.decodeQr(ProgressCodec.QR_PREFIX + "not-base64!!")
    }
}
