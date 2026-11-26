package io.github.terlingua.build.content

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

class ContentValidationTest {

    @get:Rule val temp = TemporaryFolder()

    private fun content(vararg files: Pair<String, String>): File {
        val root = temp.newFolder("content")
        files.forEach { (path, text) -> File(root, path).apply { parentFile.mkdirs() }.writeText(text.trimIndent()) }
        return root
    }

    private fun errorsOf(root: File): List<String> = try {
        ContentLoader(root, LocalDate.parse("2026-01-01")).load()
        fail("Expected validation errors")
        emptyList()
    } catch (e: ContentValidationException) {
        e.errors
    }

    private fun section(questions: String) = """
        ---
        title: Section
        questions:
        $questions
        ---
        Body
    """

    private fun book(vararg extra: Pair<String, String>) = arrayOf(
        "books/demo/book.yml" to "title: Demo",
        "books/demo/01-start/chapter.yml" to "title: Start",
        *extra,
    )

    private fun assertError(root: File, fragment: String) {
        val errors = errorsOf(root)
        assertTrue("expected '$fragment' in $errors", errors.any { it.contains(fragment) })
    }

    @Test
    fun missingChapterYml() = assertError(
        content("books/demo/book.yml" to "title: Demo", "books/demo/01-start/01-a.md" to "---\ntitle: A\n---\n"),
        "missing chapter.yml",
    )

    @Test
    fun unnumberedFolder() = assertError(content(*book("books/demo/start/chapter.yml" to "title: X")), "is not numbered")

    @Test
    fun unnumberedFile() = assertError(content(*book("books/demo/01-start/intro.md" to "---\ntitle: A\n---\n")), "is not numbered")

    @Test
    fun invalidSlug() = assertError(content(*book("books/demo/02-Bad_Slug/chapter.yml" to "title: X")), "invalid slug 'Bad_Slug'")

    @Test
    fun invalidBookSlug() = assertError(content("books/Demo Book/book.yml" to "title: X"), "invalid book slug")

    @Test
    fun duplicateNumbers() = assertError(
        content(*book("books/demo/01-other/chapter.yml" to "title: Other")),
        "duplicate number 1",
    )

    @Test
    fun missingFrontMatter() = assertError(content(*book("books/demo/01-start/01-a.md" to "No front matter")), "missing YAML front matter")

    @Test
    fun duplicateQuestionIds() = assertError(
        content(*book("books/demo/01-start/01-a.md" to section("""
          - id: same
            prompt: One?
            options: [{text: a, correct: true}, {text: b}]
          - id: same
            prompt: Two?
            options: [{text: a, correct: true}, {text: b}]
        """))),
        "duplicate id",
    )

    @Test
    fun tooFewOptions() = assertError(
        content(*book("books/demo/01-start/01-a.md" to section("""
          - prompt: One?
            options: [{text: a, correct: true}]
        """))),
        "at least 2 options",
    )

    @Test
    fun noCorrectOption() = assertError(
        content(*book("books/demo/01-start/01-a.md" to section("""
          - prompt: One?
            options: [{text: a}, {text: b}]
        """))),
        "has no correct option",
    )

    @Test
    fun singleWithManyCorrect() = assertError(
        content(*book("books/demo/01-start/01-a.md" to section("""
          - prompt: One?
            type: single
            options: [{text: a, correct: true}, {text: b, correct: true}]
        """))),
        "is type 'single' but has 2 correct options",
    )

    @Test
    fun duplicateOptionIds() = assertError(
        content(*book("books/demo/01-start/01-a.md" to section("""
          - prompt: One?
            options: [{id: x, text: a, correct: true}, {id: x, text: b}]
        """))),
        "duplicate option id 'x'",
    )

    @Test
    fun malformedYaml() = assertError(content("books/demo/book.yml" to "title: [unclosed"), "invalid YAML")

    @Test
    fun reportsAllErrorsTogether() {
        val errors = errorsOf(
            content(
                "books/demo/book.yml" to "title: Demo",
                "books/demo/start/chapter.yml" to "title: X",
                "books/demo/01-ok/01-a.md" to "---\ntitle: A\n---\n",
            ),
        )
        assertTrue(errors.size >= 2)
    }

    @Test
    fun draftsAreNotValidated() {
        val root = content("books/demo/book.yml" to "title: Demo\ndraft: true", "books/demo/broken/chapter.yml" to "x: 1")
        assertTrue(ContentLoader(root, LocalDate.parse("2026-01-01")).load().books.isEmpty())
    }
}
