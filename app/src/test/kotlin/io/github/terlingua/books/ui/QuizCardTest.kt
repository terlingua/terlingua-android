package io.github.terlingua.books.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.terlingua.books.R
import io.github.terlingua.books.domain.Block
import io.github.terlingua.books.domain.Inline
import io.github.terlingua.books.domain.Question
import io.github.terlingua.books.domain.QuestionOption
import io.github.terlingua.books.domain.QuizEvaluator
import io.github.terlingua.books.domain.ThemeMode
import io.github.terlingua.books.ui.book.QuizUiState
import io.github.terlingua.books.ui.reader.QuizCard
import io.github.terlingua.books.ui.theme.TerlinguaTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuizCardTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun option(id: String, correct: Boolean) = QuestionOption(id, listOf(Inline.Text("Option $id")), correct)

    private fun setUp(question: Question) {
        var state by mutableStateOf(QuizUiState())
        compose.setContent {
            TerlinguaTheme(ThemeMode.Light) {
                QuizCard(
                    number = 1,
                    question = question,
                    state = state,
                    solvedBefore = false,
                    onToggle = { id ->
                        val selection = if (question.multiple) state.selection.let { if (id in it) it - id else it + id } else setOf(id)
                        state = state.copy(selection = selection, result = QuizUiState.Result.None)
                    },
                    onCheck = {
                        val ok = QuizEvaluator.isCorrect(question, state.selection)
                        state = state.copy(result = if (ok) QuizUiState.Result.Correct else QuizUiState.Result.Incorrect)
                        ok
                    },
                    onRetry = { state = QuizUiState() },
                    onReveal = { state = state.copy(revealed = true) },
                )
            }
        }
    }

    private val single = Question(
        id = "q1",
        multiple = false,
        prompt = listOf(Inline.Text("Pick b")),
        explanation = listOf(Block.Paragraph(listOf(Inline.Text("Because b.")))),
        options = listOf(option("a", false), option("b", true)),
    )

    @Test
    fun singleAnswerFlowWithRetry() {
        setUp(single)
        compose.onNodeWithTag("check-q1").assertIsNotEnabled()
        compose.onNodeWithTag("option-q1-a").performClick().assertIsSelected()
        compose.onNodeWithTag("check-q1").assertIsEnabled().performClick()
        compose.onNodeWithTag("quiz-status-q1").assertTextEquals(context.getString(R.string.quiz_incorrect))

        compose.onNodeWithText(context.getString(R.string.quiz_try_again)).performClick()
        compose.onNodeWithTag("option-q1-b").performClick()
        compose.onNodeWithTag("check-q1").performClick()
        compose.onNodeWithTag("quiz-status-q1").assertTextEquals(context.getString(R.string.quiz_correct))
        compose.onNodeWithText("Because b.").assertIsDisplayed()
        compose.onNodeWithTag("option-q1-a").assertIsNotEnabled()
    }

    @Test
    fun showAnswerRevealsExplanation() {
        setUp(single)
        compose.onNodeWithText(context.getString(R.string.quiz_show_answer)).performClick()
        compose.onNodeWithTag("quiz-status-q1").assertTextEquals(context.getString(R.string.quiz_revealed))
        compose.onNodeWithText("Because b.").assertIsDisplayed()
    }

    @Test
    fun multipleAnswerNeedsEveryCorrectOption() {
        setUp(single.copy(id = "q2", multiple = true, options = listOf(option("a", true), option("b", false), option("c", true))))
        compose.onNodeWithTag("option-q2-a").performClick()
        compose.onNodeWithTag("check-q2").performClick()
        compose.onNodeWithTag("quiz-status-q2").assertTextEquals(context.getString(R.string.quiz_incorrect))
        compose.onNodeWithText(context.getString(R.string.quiz_try_again)).performClick()
        compose.onNodeWithTag("option-q2-a").performClick()
        compose.onNodeWithTag("option-q2-c").performClick()
        compose.onNodeWithTag("check-q2").performClick()
        compose.onNodeWithTag("quiz-status-q2").assertTextEquals(context.getString(R.string.quiz_correct))
    }
}
