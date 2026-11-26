package io.github.terlingua.books.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.terlingua.books.domain.Author
import io.github.terlingua.books.domain.BookSummary
import io.github.terlingua.books.domain.ThemeMode
import io.github.terlingua.books.ui.library.LibraryItem
import io.github.terlingua.books.ui.library.LibraryScreen
import io.github.terlingua.books.ui.library.LibraryUiState
import io.github.terlingua.books.ui.theme.TerlinguaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun showsHelpfulEmptyState() {
        compose.setContent {
            TerlinguaTheme(ThemeMode.Sepia) { LibraryScreen(LibraryUiState(loading = false), {}, {}, {}) }
        }
        compose.onNodeWithTag("library-empty").assertIsDisplayed()
        compose.onNodeWithText("No books yet").assertIsDisplayed()
    }

    @Test
    fun listsBooksWithProgressAndOpensThem() {
        val book = BookSummary(
            slug = "sample-book",
            title = "Sample Book",
            summary = "A tiny book.",
            authors = listOf(Author("sample-author", "Sample Author", null, null, null, emptyMap())),
            published = "2025-06-01",
            updated = null,
            tags = listOf("kotlin"),
            imageAsset = null,
            chapterCount = 2,
            sectionCount = 3,
            questionCount = 4,
            readingMinutes = 5,
        )
        var opened: String? = null
        compose.setContent {
            TerlinguaTheme(ThemeMode.HighContrast) {
                LibraryScreen(LibraryUiState(loading = false, items = listOf(LibraryItem(book, 0.5f))), { opened = it }, {}, {})
            }
        }
        compose.onNodeWithText("Sample Book").assertIsDisplayed()
        compose.onNodeWithText("Sample Author").assertIsDisplayed()
        compose.onNodeWithText("#kotlin").assertIsDisplayed()
        compose.onNodeWithText("50% complete").assertIsDisplayed()
        compose.onNodeWithTag("book-sample-book").performClick()
        assertEquals("sample-book", opened)
    }
}
