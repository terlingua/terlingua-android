package io.github.terlingua.build.content

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.Types

/** Writes the read-only content database bundled into the APK. Keep in sync with the app's ContentSql. */
internal object DatabaseWriter {
    const val SCHEMA_VERSION = 1
    private const val WORDS_PER_MINUTE = 220

    private val SCHEMA = listOf(
        "CREATE TABLE meta(key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)",
        """CREATE TABLE authors(
            id TEXT PRIMARY KEY NOT NULL, name TEXT NOT NULL, email TEXT, bio TEXT,
            avatar_asset TEXT, socials_json TEXT NOT NULL)""",
        """CREATE TABLE books(
            id INTEGER PRIMARY KEY, slug TEXT NOT NULL UNIQUE, position INTEGER NOT NULL,
            title TEXT NOT NULL, summary TEXT NOT NULL, author_ids_json TEXT NOT NULL,
            published TEXT, updated TEXT, tags_json TEXT NOT NULL, image_asset TEXT,
            chapter_count INTEGER NOT NULL, section_count INTEGER NOT NULL, question_count INTEGER NOT NULL,
            word_count INTEGER NOT NULL, reading_minutes INTEGER NOT NULL)""",
        """CREATE TABLE chapters(
            id INTEGER PRIMARY KEY, book_id INTEGER NOT NULL REFERENCES books(id), slug TEXT NOT NULL,
            number INTEGER NOT NULL, position INTEGER NOT NULL, title TEXT NOT NULL, summary TEXT NOT NULL,
            UNIQUE(book_id, slug))""",
        """CREATE TABLE sections(
            id INTEGER PRIMARY KEY, book_id INTEGER NOT NULL REFERENCES books(id),
            chapter_id INTEGER NOT NULL REFERENCES chapters(id), key TEXT NOT NULL UNIQUE, slug TEXT NOT NULL,
            number INTEGER NOT NULL, position INTEGER NOT NULL, title TEXT NOT NULL, word_count INTEGER NOT NULL,
            reading_minutes INTEGER NOT NULL, question_ids_json TEXT NOT NULL, body_json TEXT NOT NULL,
            questions_json TEXT NOT NULL)""",
        "CREATE INDEX chapters_by_book ON chapters(book_id, position)",
        "CREATE INDEX sections_by_book ON sections(book_id, position)",
        """CREATE VIRTUAL TABLE search_index USING fts5(
            title, body, kind UNINDEXED, book_id UNINDEXED, section_id UNINDEXED,
            tokenize = 'unicode61 remove_diacritics 2')""",
    )

    fun write(library: Library, out: File, contentHash: String) {
        out.parentFile.mkdirs()
        out.delete()
        DriverManager.getConnection("jdbc:sqlite:${out.absolutePath}").use { db ->
            db.createStatement().use { st -> SCHEMA.forEach(st::execute) }
            db.autoCommit = false
            insertMeta(db, library, contentHash)
            insertAuthors(db, library.authors)
            insertBooks(db, library.books)
            db.commit()
            db.autoCommit = true
            db.createStatement().use { st ->
                st.execute("INSERT INTO search_index(search_index) VALUES('optimize')")
                st.execute("PRAGMA user_version = $SCHEMA_VERSION")
                st.execute("VACUUM")
            }
        }
    }

    private fun insertMeta(db: Connection, library: Library, contentHash: String) {
        db.prepareStatement("INSERT INTO meta(key, value) VALUES (?, ?)").use { st ->
            mapOf(
                "schema_version" to SCHEMA_VERSION.toString(),
                "content_hash" to contentHash,
                "book_count" to library.books.size.toString(),
            ).forEach { (k, v) ->
                st.setString(1, k)
                st.setString(2, v)
                st.executeUpdate()
            }
        }
    }

    private fun insertAuthors(db: Connection, authors: List<Author>) {
        db.prepareStatement("INSERT INTO authors(id, name, email, bio, avatar_asset, socials_json) VALUES (?, ?, ?, ?, ?, ?)").use { st ->
            for (a in authors) {
                st.setString(1, a.id)
                st.setString(2, a.name)
                st.setNullableString(3, a.email)
                st.setNullableString(4, a.bio)
                st.setNullableString(5, a.avatarAsset)
                st.setString(6, Json.encode(a.socials))
                st.executeUpdate()
            }
        }
    }

    private fun insertBooks(db: Connection, books: List<Book>) {
        val bookSt = db.prepareStatement(
            """INSERT INTO books(id, slug, position, title, summary, author_ids_json, published, updated, tags_json,
               image_asset, chapter_count, section_count, question_count, word_count, reading_minutes)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
        )
        val chapterSt = db.prepareStatement(
            "INSERT INTO chapters(id, book_id, slug, number, position, title, summary) VALUES (?, ?, ?, ?, ?, ?, ?)",
        )
        val sectionSt = db.prepareStatement(
            """INSERT INTO sections(id, book_id, chapter_id, key, slug, number, position, title, word_count,
               reading_minutes, question_ids_json, body_json, questions_json)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
        )
        val searchSt = db.prepareStatement(
            "INSERT INTO search_index(title, body, kind, book_id, section_id) VALUES (?, ?, ?, ?, ?)",
        )
        var chapterId = 0L
        var sectionId = 0L
        listOf(bookSt, chapterSt, sectionSt, searchSt).let { statements ->
            try {
                books.forEachIndexed { index, book ->
                    val bookId = index + 1L
                    val sections = book.sections
                    val words = sections.sumOf { it.wordCount }
                    bookSt.apply {
                        setLong(1, bookId)
                        setString(2, book.slug)
                        setInt(3, index)
                        setString(4, book.title)
                        setString(5, book.summary)
                        setString(6, Json.encode(book.authorIds))
                        setNullableString(7, book.date?.toString())
                        setNullableString(8, book.updated?.toString())
                        setString(9, Json.encode(book.tags))
                        setNullableString(10, book.imageAsset)
                        setInt(11, book.chapters.size)
                        setInt(12, sections.size)
                        setInt(13, sections.sumOf { it.questions.size })
                        setInt(14, words)
                        setInt(15, minutes(words))
                        executeUpdate()
                    }
                    search(searchSt, book.title, listOf(book.summary, book.tags.joinToString(" ")).joinToString("\n"), "book", bookId, null)

                    var position = 0
                    book.chapters.forEachIndexed { chapterIndex, chapter ->
                        val cid = ++chapterId
                        chapterSt.apply {
                            setLong(1, cid)
                            setLong(2, bookId)
                            setString(3, chapter.slug)
                            setInt(4, chapter.number)
                            setInt(5, chapterIndex)
                            setString(6, chapter.title)
                            setString(7, chapter.summary)
                            executeUpdate()
                        }
                        val firstSectionId = sectionId + 1
                        chapter.sections.forEach { section ->
                            val sid = ++sectionId
                            sectionSt.apply {
                                setLong(1, sid)
                                setLong(2, bookId)
                                setLong(3, cid)
                                setString(4, section.key)
                                setString(5, section.slug)
                                setInt(6, section.number)
                                setInt(7, position++)
                                setString(8, section.title)
                                setInt(9, section.wordCount)
                                setInt(10, minutes(section.wordCount))
                                setString(11, Json.encode(section.questions.map { it.id }))
                                setString(12, section.body.json)
                                setString(13, Json.encode(section.questions.map { it.toJson() }))
                                executeUpdate()
                            }
                            val questionText = section.questions.joinToString("\n") { q ->
                                (listOf(q.prompt.plainText) + q.options.map { it.text.plainText }).joinToString("\n")
                            }
                            search(searchSt, section.title, section.body.plainText + "\n" + questionText, "section", bookId, sid)
                        }
                        if (chapter.sections.isNotEmpty()) {
                            search(searchSt, chapter.title, chapter.summary, "chapter", bookId, firstSectionId)
                        }
                    }
                }
            } finally {
                statements.forEach(PreparedStatement::close)
            }
        }
    }

    private fun search(st: PreparedStatement, title: String, body: String, kind: String, bookId: Long, sectionId: Long?) {
        st.setString(1, title)
        st.setString(2, body)
        st.setString(3, kind)
        st.setLong(4, bookId)
        if (sectionId == null) st.setNull(5, Types.INTEGER) else st.setLong(5, sectionId)
        st.executeUpdate()
    }

    private fun minutes(words: Int) = if (words == 0) 0 else maxOf(1, (words + WORDS_PER_MINUTE - 1) / WORDS_PER_MINUTE)

    private fun PreparedStatement.setNullableString(index: Int, value: String?) {
        if (value == null) setNull(index, Types.VARCHAR) else setString(index, value)
    }
}
