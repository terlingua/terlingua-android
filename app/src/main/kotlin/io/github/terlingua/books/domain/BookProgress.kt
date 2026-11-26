package io.github.terlingua.books.domain

import kotlinx.serialization.Serializable

/** Portable `.terlingua-progress.json` document. Timestamps are epoch milliseconds (UTC). */
@Serializable
data class ProgressBackup(
    val schemaVersion: Int = SCHEMA_VERSION,
    val format: String = FORMAT,
    val exportedAt: Long,
    val books: List<BookProgress>,
) {
    companion object {
        const val SCHEMA_VERSION = 1
        const val FORMAT = "terlingua-progress"
        const val FILE_SUFFIX = ".terlingua-progress.json"
        const val MIME_TYPE = "application/json"
    }
}

@Serializable
data class BookProgress(
    val schemaVersion: Int = ProgressBackup.SCHEMA_VERSION,
    val bookSlug: String,
    val lastSectionKey: String? = null,
    val updatedAt: Long,
    val sections: List<SectionProgress> = emptyList(),
) {
    fun section(key: String): SectionProgress? = sections.firstOrNull { it.key == key }
}

@Serializable
data class SectionProgress(
    val key: String,
    val firstVisitedAt: Long? = null,
    val updatedAt: Long,
    val questions: List<QuestionProgress> = emptyList(),
) {
    fun question(id: String): QuestionProgress? = questions.firstOrNull { it.id == id }
}

@Serializable
data class QuestionProgress(
    val id: String,
    val answeredCorrectly: Boolean = false,
    val lastSelection: List<String> = emptyList(),
    val selectedAt: Long = 0,
    val updatedAt: Long,
)

object Completion {
    /** A section is complete when every question was answered correctly, or—without questions—once visited. */
    fun isComplete(ref: SectionRef, progress: SectionProgress?): Boolean =
        if (ref.questionIds.isEmpty()) progress?.firstVisitedAt != null
        else ref.questionIds.all { progress?.question(it)?.answeredCorrectly == true }

    fun fraction(sections: List<SectionRef>, progress: BookProgress?): Float {
        if (sections.isEmpty() || progress == null) return 0f
        return sections.count { isComplete(it, progress.section(it.key)) }.toFloat() / sections.size
    }
}
