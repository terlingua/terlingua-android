package io.github.terlingua.books.data.content

/** Queries against the build-time database (see build-logic DatabaseWriter). Shared with JVM contract tests. */
object ContentSql {
    const val ASSET_PATH = "terlingua/terlingua-books.db"
    const val SCHEMA_VERSION = 1

    const val BOOKS = """
        SELECT slug, title, summary, author_ids_json, published, updated, tags_json, image_asset,
               chapter_count, section_count, question_count, reading_minutes
        FROM books ORDER BY position"""

    const val AUTHORS = "SELECT id, name, email, bio, avatar_asset, socials_json FROM authors"

    const val CHAPTERS = """
        SELECT c.id, c.slug, c.title, c.summary FROM chapters c
        JOIN books b ON b.id = c.book_id WHERE b.slug = ? ORDER BY c.position"""

    const val SECTIONS_FOR_BOOK = """
        SELECT s.chapter_id, s.key, s.title, s.reading_minutes, s.question_ids_json FROM sections s
        JOIN books b ON b.id = s.book_id WHERE b.slug = ? ORDER BY s.position"""

    const val ALL_SECTIONS = """
        SELECT b.slug, s.key, s.title, s.reading_minutes, s.question_ids_json FROM sections s
        JOIN books b ON b.id = s.book_id ORDER BY b.position, s.position"""

    const val SECTION = """
        SELECT b.slug, c.title, s.key, s.title, s.reading_minutes, s.question_ids_json, s.body_json, s.questions_json
        FROM sections s JOIN books b ON b.id = s.book_id JOIN chapters c ON c.id = s.chapter_id
        WHERE s.key = ?"""

    /** Highlight markers char(2)/char(3) match SearchHit.HIGHLIGHT_START/END. */
    const val SEARCH = """
        SELECT search_index.kind, b.slug, b.title, s.key,
               highlight(search_index, 0, char(2), char(3)),
               snippet(search_index, 1, char(2), char(3), '…', 12)
        FROM search_index
        JOIN books b ON b.id = search_index.book_id
        LEFT JOIN sections s ON s.id = search_index.section_id
        WHERE search_index MATCH ?
        ORDER BY bm25(search_index, 4.0, 1.0)
        LIMIT 50"""
}
