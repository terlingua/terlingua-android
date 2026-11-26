package io.github.terlingua.books.domain

/**
 * Conflict-free merge: `answeredCorrectly` is sticky (logical OR), the earliest `firstVisitedAt`
 * and the latest `updatedAt` win, and the selection with the newest `selectedAt` is kept.
 * The operation is commutative, associative and idempotent.
 */
object ProgressMerger {

    fun merge(local: BookProgress?, incoming: BookProgress): BookProgress {
        if (local == null) return incoming.normalized()
        require(local.bookSlug == incoming.bookSlug) { "Cannot merge different books" }
        val keys = (local.sections.map { it.key } + incoming.sections.map { it.key }).distinct().sorted()
        val newer = when {
            incoming.updatedAt != local.updatedAt -> if (incoming.updatedAt > local.updatedAt) incoming else local
            else -> if (incoming.lastSectionKey.orEmpty() > local.lastSectionKey.orEmpty()) incoming else local
        }
        return BookProgress(
            bookSlug = local.bookSlug,
            lastSectionKey = newer.lastSectionKey ?: listOfNotNull(local.lastSectionKey, incoming.lastSectionKey).maxOrNull(),
            updatedAt = maxOf(local.updatedAt, incoming.updatedAt),
            sections = keys.map { key -> mergeSection(local.section(key), incoming.section(key)) },
        )
    }

    private fun mergeSection(a: SectionProgress?, b: SectionProgress?): SectionProgress {
        if (a == null) return b!!.normalized()
        if (b == null) return a.normalized()
        val ids = (a.questions.map { it.id } + b.questions.map { it.id }).distinct().sorted()
        return SectionProgress(
            key = a.key,
            firstVisitedAt = listOfNotNull(a.firstVisitedAt, b.firstVisitedAt).minOrNull(),
            updatedAt = maxOf(a.updatedAt, b.updatedAt),
            questions = ids.map { id -> mergeQuestion(a.question(id), b.question(id)) },
        )
    }

    private fun mergeQuestion(a: QuestionProgress?, b: QuestionProgress?): QuestionProgress {
        if (a == null) return b!!
        if (b == null) return a
        val latest = when {
            a.selectedAt != b.selectedAt -> if (a.selectedAt > b.selectedAt) a else b
            else -> if (a.lastSelection.joinToString() >= b.lastSelection.joinToString()) a else b
        }
        return QuestionProgress(
            id = a.id,
            answeredCorrectly = a.answeredCorrectly || b.answeredCorrectly,
            lastSelection = latest.lastSelection,
            selectedAt = latest.selectedAt,
            updatedAt = maxOf(a.updatedAt, b.updatedAt),
        )
    }

    private fun BookProgress.normalized() = copy(
        schemaVersion = ProgressBackup.SCHEMA_VERSION,
        sections = sections.sortedBy { it.key }.map { it.normalized() },
    )

    private fun SectionProgress.normalized() = copy(questions = questions.sortedBy { it.id })

    fun diff(local: BookProgress?, incoming: BookProgress, mode: ImportMode = ImportMode.Merge): BookDiff {
        val result = if (mode == ImportMode.Merge) merge(local, incoming) else incoming
        val before = local?.sections?.associateBy { it.key }.orEmpty()
        var visited = 0
        var correct = 0
        var lostCorrect = 0
        var selections = 0
        result.sections.forEach { s ->
            val old = before[s.key]
            if (s.firstVisitedAt != null && old?.firstVisitedAt == null) visited++
            s.questions.forEach { q ->
                val oq = old?.question(q.id)
                if (q.answeredCorrectly && oq?.answeredCorrectly != true) correct++
                if (q.lastSelection != oq?.lastSelection.orEmpty() && q.lastSelection.isNotEmpty()) selections++
            }
        }
        if (mode == ImportMode.Replace) {
            val after = result.sections.associateBy { it.key }
            lostCorrect = before.values.sumOf { s ->
                s.questions.count { q -> q.answeredCorrectly && after[s.key]?.question(q.id)?.answeredCorrectly != true }
            }
        }
        return BookDiff(
            bookSlug = incoming.bookSlug,
            newlyVisitedSections = visited,
            newlyCorrectQuestions = correct,
            changedSelections = selections,
            lostCorrectQuestions = lostCorrect,
            isNewBook = local == null,
        )
    }
}

data class BookDiff(
    val bookSlug: String,
    val newlyVisitedSections: Int,
    val newlyCorrectQuestions: Int,
    val changedSelections: Int,
    val lostCorrectQuestions: Int = 0,
    val isNewBook: Boolean = false,
) {
    val isEmpty: Boolean
        get() = newlyVisitedSections == 0 && newlyCorrectQuestions == 0 && changedSelections == 0 && lostCorrectQuestions == 0
}
