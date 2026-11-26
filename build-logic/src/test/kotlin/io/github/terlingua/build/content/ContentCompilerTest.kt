package io.github.terlingua.build.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.sql.DriverManager
import java.time.LocalDate

class ContentCompilerTest {

    @get:Rule val temp = TemporaryFolder()

    private val fixtures = File(System.getProperty("terlingua.fixtures") ?: "../app/src/test/resources/fixtures")

    private fun compile(content: File, date: String = "2026-01-01"): File {
        val out = temp.newFolder()
        ContentCompiler.compile(content, out, LocalDate.parse(date))
        return File(out, "terlingua/${ContentCompiler.DATABASE_NAME}")
    }

    private fun <T> query(db: File, sql: String, read: (java.sql.ResultSet) -> T): List<T> =
        DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { c ->
            c.createStatement().use { st ->
                st.executeQuery(sql).use { rs -> buildList { while (rs.next()) add(read(rs)) } }
            }
        }

    @Test
    fun compilesFixtureLibrary() {
        val db = compile(File(fixtures, "content"))
        assertEquals(listOf("sample-book"), query(db, "SELECT slug FROM books") { it.getString(1) })
        assertEquals(
            listOf("sample-book/basics/introduction", "sample-book/basics/reading-only", "sample-book/advanced/search-and-sql"),
            query(db, "SELECT key FROM sections ORDER BY position") { it.getString(1) },
        )
        val counts = query(db, "SELECT chapter_count, section_count, question_count, reading_minutes FROM books") {
            listOf(it.getInt(1), it.getInt(2), it.getInt(3), it.getInt(4))
        }.single()
        assertEquals(listOf(2, 3, 4), counts.take(3))
        assertTrue(counts[3] >= 1)
        assertEquals("[\"testing\",\"kotlin\"]", query(db, "SELECT tags_json FROM books") { it.getString(1) }.single())
        assertEquals(
            "terlingua/images/authors/sample-author.png",
            query(db, "SELECT avatar_asset FROM authors WHERE id = 'sample-author'") { it.getString(1) }.single(),
        )
        assertTrue(File(db.parentFile, "images/authors/sample-author.png").isFile)
        assertEquals(1, query(db, "PRAGMA user_version") { it.getInt(1) }.single())
    }

    @Test
    fun rendersMarkdownBlocksAndQuestions() {
        val db = compile(File(fixtures, "content"))
        val (body, questions, ids) = query(db, "SELECT body_json, questions_json, question_ids_json FROM sections WHERE slug = 'introduction'") {
            Triple(it.getString(1), it.getString(2), it.getString(3))
        }.single()
        listOf("\"t\":\"h\"", "\"t\":\"quote\"", "\"t\":\"ul\"", "\"t\":\"ol\"", "\"t\":\"table\"", "\"t\":\"hr\"", "\"t\":\"del\"", "\"t\":\"link\"").forEach {
            assertTrue("missing $it in $body", body.contains(it))
        }
        assertTrue(body.contains("\"lang\":\"kotlin\""))
        assertTrue(body.contains("{\"k\":\"kw\",\"v\":\"fun\"}"))
        assertTrue(body.contains("\"align\":[\"l\",\"r\"],\"head\":[[{\"t\":\"text\",\"v\":\"Name\"}],[{\"t\":\"text\",\"v\":\"Value\"}]]"))
        assertTrue(body.contains("\"t\":\"img\",\"src\":\"terlingua/images/authors/sample-author.png\""))
        assertEquals("[\"runtime\",\"properties\",\"q3\"]", ids)
        assertTrue(questions.contains("\"id\":\"properties\",\"type\":\"multiple\""))
        assertTrue(questions.contains("\"id\":\"q3\",\"type\":\"multiple\""))
        assertTrue(questions.contains("\"id\":\"runtime\",\"type\":\"single\""))
        assertTrue(questions.contains("{\"t\":\"strong\",\"c\":[{\"t\":\"text\",\"v\":\"offline\"}]}"))
        assertTrue(questions.contains("\"v\":\"Yes\""))
    }

    @Test
    fun fullTextSearchFindsSectionsChaptersAndBooks() {
        val db = compile(File(fixtures, "content"))
        val hits = query(db, "SELECT kind, section_id FROM search_index WHERE search_index MATCH 'zebrafish'") { it.getString(1) }
        assertEquals(listOf("section"), hits)
        assertEquals(listOf("chapter"), query(db, "SELECT kind FROM search_index WHERE search_index MATCH 'title:advanced'") { it.getString(1) })
        assertEquals(listOf("book"), query(db, "SELECT kind FROM search_index WHERE search_index MATCH 'tiny'") { it.getString(1) })
    }

    @Test
    fun skipsDraftsAndFutureBooksUntilTheirDate() {
        val db = compile(File(fixtures, "content"))
        assertTrue(query(db, "SELECT title FROM sections") { it.getString(1) }.none { it == "Draft Section" || it == "Hidden" })
        val later = compile(File(fixtures, "content"), date = "2999-01-01")
        assertEquals(listOf("future-book", "sample-book"), query(later, "SELECT slug FROM books ORDER BY position") { it.getString(1) })
    }

    @Test
    fun emptyContentProducesEmptyDatabase() {
        val db = compile(File(fixtures, "empty-content"))
        assertEquals(0, query(db, "SELECT COUNT(*) FROM books") { it.getInt(1) }.single())
        assertEquals(0, query(db, "SELECT COUNT(*) FROM search_index") { it.getInt(1) }.single())
    }

    @Test
    fun missingContentFoldersAreAnEmptyLibrary() {
        val db = compile(temp.newFolder("bare"))
        assertEquals(0, query(db, "SELECT COUNT(*) FROM books") { it.getInt(1) }.single())
    }
}
