package io.github.terlingua.books.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuizAndSearchTest {

    private fun option(id: String, correct: Boolean) = QuestionOption(id, listOf(Inline.Text(id)), correct)

    @Test
    fun singleAnswerRequiresExactSelection() {
        val q = Question("q", multiple = false, prompt = emptyList(), explanation = emptyList(), options = listOf(option("a", false), option("b", true)))
        assertTrue(QuizEvaluator.isCorrect(q, setOf("b")))
        assertFalse(QuizEvaluator.isCorrect(q, setOf("a")))
        assertFalse(QuizEvaluator.isCorrect(q, emptySet()))
    }

    @Test
    fun multipleAnswerRequiresAllAndOnlyCorrectOptions() {
        val q = Question("q", multiple = true, prompt = emptyList(), explanation = emptyList(), options = listOf(option("a", true), option("b", false), option("c", true)))
        assertTrue(QuizEvaluator.isCorrect(q, setOf("a", "c")))
        assertFalse(QuizEvaluator.isCorrect(q, setOf("a")))
        assertFalse(QuizEvaluator.isCorrect(q, setOf("a", "b", "c")))
    }

    @Test
    fun ftsQueriesAreSanitisedPrefixQueries() {
        assertEquals("\"zebra\"* \"fish\"*", SearchQuery.toFts("Zebra   fish"))
        assertEquals("\"drop\"* \"table\"* \"or\"*", SearchQuery.toFts("\"drop table\" OR -- ; *"))
        assertEquals("\"café\"*", SearchQuery.toFts("café"))
        assertNull(SearchQuery.toFts("  *** \"\" "))
    }
}
