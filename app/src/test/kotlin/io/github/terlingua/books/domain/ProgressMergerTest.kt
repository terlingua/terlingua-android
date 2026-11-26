package io.github.terlingua.books.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressMergerTest {

    private fun q(id: String, correct: Boolean, selection: List<String>, at: Long) =
        QuestionProgress(id, correct, selection, selectedAt = at, updatedAt = at)

    private val local = BookProgress(
        bookSlug = "book",
        lastSectionKey = "book/a/one",
        updatedAt = 100,
        sections = listOf(
            SectionProgress("book/a/one", firstVisitedAt = 50, updatedAt = 100, questions = listOf(q("q1", true, listOf("a"), 90))),
            SectionProgress("book/a/two", firstVisitedAt = 70, updatedAt = 80),
        ),
    )

    private val remote = BookProgress(
        bookSlug = "book",
        lastSectionKey = "book/b/three",
        updatedAt = 200,
        sections = listOf(
            SectionProgress("book/a/one", firstVisitedAt = 20, updatedAt = 150, questions = listOf(q("q1", false, listOf("b"), 150), q("q2", true, listOf("c"), 140))),
            SectionProgress("book/b/three", firstVisitedAt = 190, updatedAt = 200),
        ),
    )

    @Test
    fun mergeFollowsConflictFreeRules() {
        val merged = ProgressMerger.merge(local, remote)
        val one = merged.section("book/a/one")!!
        assertEquals(20L, one.firstVisitedAt)
        assertEquals(150L, one.updatedAt)
        assertTrue("answeredCorrectly is sticky", one.question("q1")!!.answeredCorrectly)
        assertEquals("newest selection wins", listOf("b"), one.question("q1")!!.lastSelection)
        assertTrue(one.question("q2")!!.answeredCorrectly)
        assertEquals(setOf("book/a/one", "book/a/two", "book/b/three"), merged.sections.map { it.key }.toSet())
        assertEquals("book/b/three", merged.lastSectionKey)
        assertEquals(200L, merged.updatedAt)
    }

    @Test
    fun mergeIsCommutativeAndIdempotent() {
        assertEquals(ProgressMerger.merge(local, remote), ProgressMerger.merge(remote, local))
        val merged = ProgressMerger.merge(local, remote)
        assertEquals(merged, ProgressMerger.merge(merged, merged))
        assertEquals(merged, ProgressMerger.merge(merged, remote))
    }

    @Test
    fun diffCountsOnlyNewProgress() {
        val diff = ProgressMerger.diff(local, remote)
        assertEquals(1, diff.newlyVisitedSections)
        assertEquals(1, diff.newlyCorrectQuestions)
        assertEquals(2, diff.changedSelections)
        assertTrue(ProgressMerger.diff(ProgressMerger.merge(local, remote), remote).let { it.newlyCorrectQuestions == 0 && it.newlyVisitedSections == 0 })
    }

    @Test
    fun replaceDiffReportsLostAnswers() {
        val replace = ProgressMerger.diff(local, remote.copy(sections = emptyList()), ImportMode.Replace)
        assertEquals(1, replace.lostCorrectQuestions)
    }

    @Test
    fun completionRequiresAllQuestionsOrAVisit() {
        val withQuestions = SectionRef("book/a/one", "One", 1, listOf("q1", "q2"))
        val reading = SectionRef("book/a/two", "Two", 1, emptyList())
        val merged = ProgressMerger.merge(local, remote)
        assertTrue(Completion.isComplete(withQuestions, merged.section("book/a/one")))
        assertTrue(!Completion.isComplete(withQuestions, local.section("book/a/one")))
        assertTrue(Completion.isComplete(reading, local.section("book/a/two")))
        assertEquals(1f, Completion.fraction(listOf(withQuestions, reading), merged))
    }
}
