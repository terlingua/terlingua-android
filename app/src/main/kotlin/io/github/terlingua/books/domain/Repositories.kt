package io.github.terlingua.books.domain

import kotlinx.coroutines.flow.Flow

interface BookRepository {
    suspend fun books(): List<BookSummary>
    suspend fun sectionsByBook(): Map<String, List<SectionRef>>
    suspend fun toc(bookSlug: String): BookToc?
    suspend fun section(sectionKey: String): SectionContent?
    suspend fun search(query: String): List<SearchHit>
}

interface ProgressRepository {
    fun observeAll(): Flow<Map<String, BookProgress>>
    fun observeBook(bookSlug: String): Flow<BookProgress?>
    suspend fun markVisited(bookSlug: String, sectionKey: String)
    suspend fun recordAnswer(bookSlug: String, sectionKey: String, questionId: String, selection: List<String>, correct: Boolean)
    suspend fun exportAll(): ProgressBackup
    suspend fun exportBook(bookSlug: String): BookProgress?
    suspend fun preview(backup: ProgressBackup): ImportPreview
    suspend fun apply(backup: ProgressBackup, mode: ImportMode)
}

interface SettingsRepository {
    val settings: Flow<Settings>
    suspend fun setTheme(theme: ThemeMode)
    suspend fun setSound(enabled: Boolean)
    suspend fun setHaptics(enabled: Boolean)
}

enum class ImportMode { Merge, Replace }

data class ImportPreview(
    val backup: ProgressBackup,
    val mergeDiffs: List<BookDiff>,
    val replaceDiffs: List<BookDiff>,
) {
    val hasChanges: Boolean get() = mergeDiffs.any { !it.isEmpty }
}
