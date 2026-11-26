package io.github.terlingua.books.domain

data class Author(
    val id: String,
    val name: String,
    val email: String?,
    val bio: String?,
    val avatarAsset: String?,
    val socials: Map<String, String>,
)

data class BookSummary(
    val slug: String,
    val title: String,
    val summary: String,
    val authors: List<Author>,
    val published: String?,
    val updated: String?,
    val tags: List<String>,
    val imageAsset: String?,
    val chapterCount: Int,
    val sectionCount: Int,
    val questionCount: Int,
    val readingMinutes: Int,
)

data class SectionRef(
    val key: String,
    val title: String,
    val readingMinutes: Int,
    val questionIds: List<String>,
)

data class ChapterToc(val slug: String, val title: String, val summary: String, val sections: List<SectionRef>)

data class BookToc(val book: BookSummary, val chapters: List<ChapterToc>) {
    val sections: List<SectionRef> = chapters.flatMap { it.sections }
}

data class Question(
    val id: String,
    val multiple: Boolean,
    val prompt: List<Inline>,
    val explanation: List<Block>,
    val options: List<QuestionOption>,
) {
    val correctIds: Set<String> = options.filter { it.correct }.mapTo(LinkedHashSet()) { it.id }
}

data class QuestionOption(val id: String, val text: List<Inline>, val correct: Boolean)

data class SectionContent(
    val bookSlug: String,
    val ref: SectionRef,
    val chapterTitle: String,
    val blocks: List<Block>,
    val questions: List<Question>,
)

data class SearchHit(
    val kind: Kind,
    val bookSlug: String,
    val bookTitle: String,
    val sectionKey: String?,
    val title: String,
    val snippet: String,
) {
    enum class Kind { Book, Chapter, Section }

    companion object {
        /** STX/ETX markers wrap matched terms in [title] and [snippet]. */
        const val HIGHLIGHT_START = '\u0002'
        const val HIGHLIGHT_END = '\u0003'
    }
}

enum class ThemeMode { System, Light, Dark, Sepia, SolarizedLight, SolarizedDark, HighContrast }

data class Settings(
    val theme: ThemeMode = ThemeMode.System,
    val sound: Boolean = true,
    val haptics: Boolean = true,
)
