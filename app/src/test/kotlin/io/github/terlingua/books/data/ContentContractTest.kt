package io.github.terlingua.books.data

import io.github.terlingua.books.data.content.ContentJson
import io.github.terlingua.books.data.content.ContentSql
import io.github.terlingua.books.domain.Block
import io.github.terlingua.books.domain.Inline
import io.github.terlingua.books.domain.SearchHit
import io.github.terlingua.books.domain.SearchQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

/**
 * Integration test of the build-time content compiler and the app's queries: the fixture databases
 * are produced by build-logic from src/test/resources/fixtures and opened here with sqlite-jdbc.
 */
class ContentContractTest {

    private fun fixture(property: String): File {
        val path = System.getProperty(property)
        assumeTrue("$property not set (run through Gradle)", path != null)
        return File(path!!).also { assertTrue("missing $it", it.isFile) }
    }

    private fun <T> withDb(file: File, block: (Connection) -> T): T =
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use(block)

    private fun <T> Connection.rows(sql: String, vararg args: String, read: (ResultSet) -> T): List<T> =
        prepareStatement(sql).use { st ->
            args.forEachIndexed { i, a -> st.setString(i + 1, a) }
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(read(rs)) } }
        }

    @Test
    fun appQueriesMatchCompiledSchema() = withDb(fixture("terlingua.fixtureDb")) { db ->
        assertEquals(ContentSql.SCHEMA_VERSION, db.rows("PRAGMA user_version") { it.getInt(1) }.single())
        val books = db.rows(ContentSql.BOOKS) { listOf(it.getString(1), it.getString(2), ContentJson.strings(it.getString(4)).joinToString()) }
        assertEquals(listOf(listOf("sample-book", "Sample Book", "sample-author")), books)
        val author = db.rows(ContentSql.AUTHORS) { ContentJson.stringMap(it.getString(6)) }.single()
        assertEquals("sample-author", author["github"])
        assertEquals(2, db.rows(ContentSql.CHAPTERS, "sample-book") { it.getString(2) }.size)
        val sections = db.rows(ContentSql.SECTIONS_FOR_BOOK, "sample-book") { it.getString(2) to ContentJson.strings(it.getString(5)) }
        assertEquals(listOf("runtime", "properties", "q3"), sections.first().second)
        assertEquals(3, db.rows(ContentSql.ALL_SECTIONS) { it.getString(2) }.size)
    }

    @Test
    fun sectionJsonDecodesIntoRenderableBlocks() = withDb(fixture("terlingua.fixtureDb")) { db ->
        val (body, questions) = db.rows(ContentSql.SECTION, "sample-book/basics/introduction") { it.getString(7) to it.getString(8) }.single()
        val blocks = ContentJson.blocks(body)
        assertTrue(blocks.first() is Block.Heading)
        val code = blocks.filterIsInstance<Block.Code>().single()
        assertEquals("kotlin", code.lang)
        assertTrue(code.tokens.any { it.k == "kw" && it.v == "fun" })
        val table = blocks.filterIsInstance<Block.Table>().single()
        assertEquals(listOf("l", "r"), table.align)
        assertEquals(2, table.rows.size)
        assertTrue(blocks.any { it is Block.Quote } && blocks.any { it is Block.Image } && blocks.contains(Block.Rule))

        val quiz = ContentJson.questions(questions)
        assertEquals(listOf(false, true, true), quiz.map { it.multiple })
        assertEquals(setOf("offline", "open"), quiz[1].correctIds)
        assertTrue(quiz[0].prompt.any { it is Inline.Strong })
        assertTrue(quiz[0].explanation.isNotEmpty())
    }

    @Test
    fun ftsSearchReturnsHighlightedHits() = withDb(fixture("terlingua.fixtureDb")) { db ->
        val hits = db.rows(ContentSql.SEARCH, SearchQuery.toFts("zebra")!!) { listOf(it.getString(1), it.getString(4), it.getString(6)) }
        val section = hits.single()
        assertEquals("section", section[0])
        assertEquals("sample-book/basics/introduction", section[1])
        assertTrue(section[2].contains(SearchHit.HIGHLIGHT_START))
        assertTrue(db.rows(ContentSql.SEARCH, SearchQuery.toFts("FTS5 module")!!) { it.getString(4) }.contains("sample-book/advanced/search-and-sql"))
    }

    @Test
    fun emptyContentStillYieldsAValidDatabase() = withDb(fixture("terlingua.emptyFixtureDb")) { db ->
        assertTrue(db.rows(ContentSql.BOOKS) { it.getString(1) }.isEmpty())
        assertTrue(db.rows(ContentSql.SEARCH, "\"anything\"*") { it.getString(1) }.isEmpty())
    }
}
