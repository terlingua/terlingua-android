package io.github.terlingua.books.ui.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.terlingua.books.domain.BookProgress
import io.github.terlingua.books.domain.BookRepository
import io.github.terlingua.books.domain.BookToc
import io.github.terlingua.books.domain.Completion
import io.github.terlingua.books.domain.ProgressRepository
import io.github.terlingua.books.domain.Question
import io.github.terlingua.books.domain.QuizEvaluator
import io.github.terlingua.books.domain.SectionContent
import io.github.terlingua.books.domain.SectionRef
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookUiState(val loading: Boolean = true, val toc: BookToc? = null, val progress: BookProgress? = null) {
    val fraction: Float get() = Completion.fraction(toc?.sections.orEmpty(), progress)

    val resumeKey: String?
        get() = progress?.lastSectionKey?.takeIf { key -> toc?.sections?.any { it.key == key } == true }
            ?: toc?.sections?.firstOrNull()?.key

    fun isComplete(ref: SectionRef): Boolean = Completion.isComplete(ref, progress?.section(ref.key))

    fun isSolved(sectionKey: String, questionId: String): Boolean =
        progress?.section(sectionKey)?.question(questionId)?.answeredCorrectly == true

    fun neighbour(key: String, offset: Int): String? {
        val sections = toc?.sections ?: return null
        val index = sections.indexOfFirst { it.key == key }
        return if (index < 0) null else sections.getOrNull(index + offset)?.key
    }
}

data class SectionUiState(val key: String? = null, val loading: Boolean = false, val content: SectionContent? = null)

data class QuizUiState(val selection: Set<String> = emptySet(), val result: Result = Result.None, val revealed: Boolean = false) {
    enum class Result { None, Correct, Incorrect }

    val locked: Boolean get() = result == Result.Correct || revealed
}

@OptIn(ExperimentalCoroutinesApi::class)
class BookViewModel(
    private val slug: String,
    private val books: BookRepository,
    private val progress: ProgressRepository,
) : ViewModel() {

    private val toc = viewModelScope.async { books.toc(slug) }

    val state: StateFlow<BookUiState> = combine(flow { emit(toc.await()) }, progress.observeBook(slug)) { toc, p ->
        BookUiState(loading = false, toc = toc, progress = p)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookUiState())

    private val selected = MutableStateFlow<String?>(null)

    val section: StateFlow<SectionUiState> = selected.filterNotNull().transformLatest { key ->
        emit(SectionUiState(key, loading = true))
        emit(SectionUiState(key, loading = false, content = books.section(key)))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SectionUiState())

    private val _quiz = MutableStateFlow<Map<String, QuizUiState>>(emptyMap())
    val quiz: StateFlow<Map<String, QuizUiState>> = _quiz.asStateFlow()

    fun open(sectionKey: String) {
        if (selected.value == sectionKey) return
        selected.value = sectionKey
        viewModelScope.launch { progress.markVisited(slug, sectionKey) }
    }

    fun toggle(sectionKey: String, question: Question, optionId: String) = updateQuiz(sectionKey, question) { s ->
        if (s.locked) return@updateQuiz s
        val selection = when {
            !question.multiple -> setOf(optionId)
            optionId in s.selection -> s.selection - optionId
            else -> s.selection + optionId
        }
        s.copy(selection = selection, result = QuizUiState.Result.None)
    }

    /** Evaluates immediately and returns whether the answer was correct (used for feedback cues). */
    fun check(sectionKey: String, question: Question): Boolean {
        val current = _quiz.value[quizKey(sectionKey, question)] ?: QuizUiState()
        val correct = QuizEvaluator.isCorrect(question, current.selection)
        updateQuiz(sectionKey, question) {
            it.copy(result = if (correct) QuizUiState.Result.Correct else QuizUiState.Result.Incorrect)
        }
        viewModelScope.launch {
            progress.recordAnswer(slug, sectionKey, question.id, current.selection.sorted(), correct)
        }
        return correct
    }

    fun retry(sectionKey: String, question: Question) = updateQuiz(sectionKey, question) { QuizUiState() }

    fun reveal(sectionKey: String, question: Question) = updateQuiz(sectionKey, question) { it.copy(revealed = true) }

    private inline fun updateQuiz(sectionKey: String, question: Question, crossinline transform: (QuizUiState) -> QuizUiState) {
        val key = quizKey(sectionKey, question)
        _quiz.update { map -> map + (key to transform(map[key] ?: QuizUiState())) }
    }

    companion object {
        fun quizKey(sectionKey: String, question: Question) = "$sectionKey#${question.id}"
    }
}
