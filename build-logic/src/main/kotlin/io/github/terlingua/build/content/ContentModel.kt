package io.github.terlingua.build.content

import java.time.LocalDate

internal data class Library(val authors: List<Author>, val books: List<Book>, val assets: Set<String>)

internal data class Author(
    val id: String,
    val name: String,
    val email: String?,
    val bio: String?,
    val avatarAsset: String?,
    val socials: Map<String, String>,
)

internal data class Book(
    val slug: String,
    val title: String,
    val summary: String,
    val authorIds: List<String>,
    val date: LocalDate?,
    val updated: LocalDate?,
    val tags: List<String>,
    val imageAsset: String?,
    val chapters: List<Chapter>,
) {
    val sections: List<Section> get() = chapters.flatMap { it.sections }
}

internal data class Chapter(
    val slug: String,
    val number: Int,
    val title: String,
    val summary: String,
    val sections: List<Section>,
)

internal data class Section(
    val key: String,
    val slug: String,
    val number: Int,
    val title: String,
    val body: Rendered,
    val questions: List<Question>,
) {
    val wordCount: Int get() = body.wordCount + questions.sumOf { it.wordCount }
}

internal enum class QuestionType(val id: String) { SINGLE("single"), MULTIPLE("multiple") }

internal data class Question(
    val id: String,
    val type: QuestionType,
    val prompt: Rendered,
    val explanation: Rendered?,
    val options: List<Option>,
) {
    val wordCount: Int get() = prompt.wordCount + options.sumOf { it.text.wordCount }

    fun toJson(): Map<String, Any?> = linkedMapOf(
        "id" to id,
        "type" to type.id,
        "prompt" to RawJson(prompt.json),
        "explanation" to explanation?.let { RawJson(it.json) },
        "options" to options.map {
            linkedMapOf("id" to it.id, "text" to RawJson(it.text.json), "correct" to it.correct)
        },
    )
}

internal data class Option(val id: String, val text: Rendered, val correct: Boolean)

/** Rendered Markdown: JSON block/inline tree plus plain text for search and word counts. */
internal data class Rendered(val json: String, val plainText: String) {
    val wordCount: Int get() = WORD.findAll(plainText).count()

    private companion object {
        val WORD = Regex("""[\p{L}\p{N}][\p{L}\p{N}'’_-]*""")
    }
}
