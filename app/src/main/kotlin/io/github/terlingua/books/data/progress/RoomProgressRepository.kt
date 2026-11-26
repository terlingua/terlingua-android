package io.github.terlingua.books.data.progress

import io.github.terlingua.books.domain.BookProgress
import io.github.terlingua.books.domain.ImportMode
import io.github.terlingua.books.domain.ImportPreview
import io.github.terlingua.books.domain.ProgressBackup
import io.github.terlingua.books.domain.ProgressMerger
import io.github.terlingua.books.domain.ProgressRepository
import io.github.terlingua.books.domain.QuestionProgress
import io.github.terlingua.books.domain.SectionProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class RoomProgressRepository(
    private val dao: ProgressDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : ProgressRepository {

    override fun observeAll(): Flow<Map<String, BookProgress>> =
        combine(dao.observeBooks(), dao.observeSections(), dao.observeQuestions(), ::assemble).distinctUntilChanged()

    override fun observeBook(bookSlug: String): Flow<BookProgress?> =
        observeAll().map { it[bookSlug] }.distinctUntilChanged()

    override suspend fun markVisited(bookSlug: String, sectionKey: String) {
        val now = clock()
        val section = dao.section(sectionKey)
        dao.recordVisit(
            BookStateEntity(bookSlug, sectionKey, now),
            SectionProgressEntity(sectionKey, bookSlug, section?.firstVisitedAt ?: now, now),
        )
    }

    override suspend fun recordAnswer(bookSlug: String, sectionKey: String, questionId: String, selection: List<String>, correct: Boolean) {
        val now = clock()
        val section = dao.section(sectionKey)
        val previous = dao.question(sectionKey, questionId)
        dao.recordAnswer(
            BookStateEntity(bookSlug, sectionKey, now),
            SectionProgressEntity(sectionKey, bookSlug, section?.firstVisitedAt ?: now, now),
            QuestionProgressEntity(
                sectionKey = sectionKey,
                questionId = questionId,
                bookSlug = bookSlug,
                answeredCorrectly = correct || previous?.answeredCorrectly == true,
                lastSelection = selection.joinToString(","),
                selectedAt = now,
                updatedAt = now,
            ),
        )
    }

    override suspend fun exportAll(): ProgressBackup =
        ProgressBackup(exportedAt = clock(), books = snapshot().values.sortedBy { it.bookSlug })

    override suspend fun exportBook(bookSlug: String): BookProgress? = snapshot()[bookSlug]

    override suspend fun preview(backup: ProgressBackup): ImportPreview {
        val local = snapshot()
        val incoming = backup.books.groupBy { it.bookSlug }.map { (_, copies) -> copies.reduce { a, b -> ProgressMerger.merge(a, b) } }
        return ImportPreview(
            backup = backup,
            mergeDiffs = incoming.map { ProgressMerger.diff(local[it.bookSlug], it, ImportMode.Merge) },
            replaceDiffs = incoming.map { ProgressMerger.diff(local[it.bookSlug], it, ImportMode.Replace) },
        )
    }

    override suspend fun apply(backup: ProgressBackup, mode: ImportMode) {
        val local = snapshot()
        val result = backup.books.groupBy { it.bookSlug }.map { (slug, copies) ->
            val incoming = copies.reduce { a, b -> ProgressMerger.merge(a, b) }
            if (mode == ImportMode.Replace) ProgressMerger.merge(null, incoming) else ProgressMerger.merge(local[slug], incoming)
        }
        dao.writeBooks(
            replaceSlugs = if (mode == ImportMode.Replace) result.map { it.bookSlug } else emptyList(),
            books = result.map { BookStateEntity(it.bookSlug, it.lastSectionKey, it.updatedAt) },
            sections = result.flatMap { b -> b.sections.map { SectionProgressEntity(it.key, b.bookSlug, it.firstVisitedAt, it.updatedAt) } },
            questions = result.flatMap { b ->
                b.sections.flatMap { s ->
                    s.questions.map {
                        QuestionProgressEntity(s.key, it.id, b.bookSlug, it.answeredCorrectly, it.lastSelection.joinToString(","), it.selectedAt, it.updatedAt)
                    }
                }
            },
        )
    }

    private suspend fun snapshot(): Map<String, BookProgress> =
        assemble(dao.allBooks(), dao.allSections(), dao.allQuestions())

    private fun assemble(
        books: List<BookStateEntity>,
        sections: List<SectionProgressEntity>,
        questions: List<QuestionProgressEntity>,
    ): Map<String, BookProgress> {
        val questionsBySection = questions.groupBy { it.sectionKey }
        val sectionsByBook = sections.groupBy { it.bookSlug }
        val slugs = (books.map { it.bookSlug } + sectionsByBook.keys).toSortedSet()
        val states = books.associateBy { it.bookSlug }
        return slugs.associateWith { slug ->
            val bookSections = sectionsByBook[slug].orEmpty().sortedBy { it.sectionKey }.map { s ->
                SectionProgress(
                    key = s.sectionKey,
                    firstVisitedAt = s.firstVisitedAt,
                    updatedAt = s.updatedAt,
                    questions = questionsBySection[s.sectionKey].orEmpty().sortedBy { it.questionId }.map { q ->
                        QuestionProgress(
                            id = q.questionId,
                            answeredCorrectly = q.answeredCorrectly,
                            lastSelection = q.lastSelection.split(',').filter { it.isNotEmpty() },
                            selectedAt = q.selectedAt,
                            updatedAt = q.updatedAt,
                        )
                    },
                )
            }
            BookProgress(
                bookSlug = slug,
                lastSectionKey = states[slug]?.lastSectionKey,
                updatedAt = states[slug]?.updatedAt ?: bookSections.maxOfOrNull { it.updatedAt } ?: 0L,
                sections = bookSections,
            )
        }
    }
}
