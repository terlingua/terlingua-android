package io.github.terlingua.books.data.content

import io.github.terlingua.books.domain.Author
import io.github.terlingua.books.domain.BookRepository
import io.github.terlingua.books.domain.BookSummary
import io.github.terlingua.books.domain.BookToc
import io.github.terlingua.books.domain.ChapterToc
import io.github.terlingua.books.domain.SearchHit
import io.github.terlingua.books.domain.SearchQuery
import io.github.terlingua.books.domain.SectionContent
import io.github.terlingua.books.domain.SectionRef
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ContentBookRepository(private val db: ContentDatabase) : BookRepository {

    private val cacheLock = Mutex()
    private var cachedBooks: List<BookSummary>? = null
    private var cachedSections: Map<String, List<SectionRef>>? = null

    override suspend fun books(): List<BookSummary> = cacheLock.withLock {
        cachedBooks ?: db.read { c ->
            val authors = c.query(ContentSql.AUTHORS) { st ->
                Author(
                    id = st.getText(0),
                    name = st.getText(1),
                    email = st.textOrNull(2),
                    bio = st.textOrNull(3),
                    avatarAsset = st.textOrNull(4),
                    socials = ContentJson.stringMap(st.getText(5)),
                )
            }.associateBy { it.id }
            c.query(ContentSql.BOOKS) { st ->
                BookSummary(
                    slug = st.getText(0),
                    title = st.getText(1),
                    summary = st.getText(2),
                    authors = ContentJson.strings(st.getText(3)).map { id ->
                        authors[id] ?: Author(id, id, null, null, null, emptyMap())
                    },
                    published = st.textOrNull(4),
                    updated = st.textOrNull(5),
                    tags = ContentJson.strings(st.getText(6)),
                    imageAsset = st.textOrNull(7),
                    chapterCount = st.getInt(8),
                    sectionCount = st.getInt(9),
                    questionCount = st.getInt(10),
                    readingMinutes = st.getInt(11),
                )
            }
        }.also { cachedBooks = it }
    }

    override suspend fun sectionsByBook(): Map<String, List<SectionRef>> = cacheLock.withLock {
        cachedSections ?: db.read { c ->
            c.query(ContentSql.ALL_SECTIONS) { st -> st.getText(0) to sectionRef(st, 1) }
        }.groupBy({ it.first }, { it.second }).also { cachedSections = it }
    }

    override suspend fun toc(bookSlug: String): BookToc? {
        val book = books().firstOrNull { it.slug == bookSlug } ?: return null
        return db.read { c ->
            val sections = c.query(ContentSql.SECTIONS_FOR_BOOK, { bindText(1, bookSlug) }) { st ->
                st.getLong(0) to sectionRef(st, 1)
            }.groupBy({ it.first }, { it.second })
            val chapters = c.query(ContentSql.CHAPTERS, { bindText(1, bookSlug) }) { st ->
                ChapterToc(st.getText(1), st.getText(2), st.getText(3), sections[st.getLong(0)].orEmpty())
            }
            BookToc(book, chapters)
        }
    }

    override suspend fun section(sectionKey: String): SectionContent? = db.read { c ->
        c.query(ContentSql.SECTION, { bindText(1, sectionKey) }) { st ->
            SectionContent(
                bookSlug = st.getText(0),
                chapterTitle = st.getText(1),
                ref = sectionRef(st, 2),
                blocks = ContentJson.blocks(st.getText(6)),
                questions = ContentJson.questions(st.getText(7)),
            )
        }.firstOrNull()
    }

    override suspend fun search(query: String): List<SearchHit> {
        val fts = SearchQuery.toFts(query) ?: return emptyList()
        return db.read { c ->
            c.query(ContentSql.SEARCH, { bindText(1, fts) }) { st ->
                SearchHit(
                    kind = when (st.getText(0)) {
                        "book" -> SearchHit.Kind.Book
                        "chapter" -> SearchHit.Kind.Chapter
                        else -> SearchHit.Kind.Section
                    },
                    bookSlug = st.getText(1),
                    bookTitle = st.getText(2),
                    sectionKey = st.textOrNull(3),
                    title = st.getText(4),
                    snippet = st.textOrNull(5).orEmpty(),
                )
            }
        }
    }

    private fun sectionRef(st: androidx.sqlite.SQLiteStatement, offset: Int) = SectionRef(
        key = st.getText(offset),
        title = st.getText(offset + 1),
        readingMinutes = st.getInt(offset + 2),
        questionIds = ContentJson.strings(st.getText(offset + 3)),
    )
}
