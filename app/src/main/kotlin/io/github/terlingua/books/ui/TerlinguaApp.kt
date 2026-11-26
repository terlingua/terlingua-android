package io.github.terlingua.books.ui

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.terlingua.books.AppContainer
import io.github.terlingua.books.domain.Settings
import io.github.terlingua.books.feedback.ProvideFeedback
import io.github.terlingua.books.ui.about.AboutScreen
import io.github.terlingua.books.ui.book.BookScreen
import io.github.terlingua.books.ui.book.BookViewModel
import io.github.terlingua.books.ui.library.LibraryScreen
import io.github.terlingua.books.ui.library.LibraryViewModel
import io.github.terlingua.books.ui.navigation.AboutKey
import io.github.terlingua.books.ui.navigation.BookKey
import io.github.terlingua.books.ui.navigation.LibraryKey
import io.github.terlingua.books.ui.navigation.QrScanKey
import io.github.terlingua.books.ui.navigation.QrShareKey
import io.github.terlingua.books.ui.navigation.SearchKey
import io.github.terlingua.books.ui.navigation.SettingsKey
import io.github.terlingua.books.ui.search.SearchScreen
import io.github.terlingua.books.ui.search.SearchViewModel
import io.github.terlingua.books.ui.settings.SettingsScreen
import io.github.terlingua.books.ui.settings.SettingsViewModel
import io.github.terlingua.books.ui.theme.LocalIsDarkTheme
import io.github.terlingua.books.ui.theme.TerlinguaTheme
import io.github.terlingua.books.ui.transfer.ImportDialog
import io.github.terlingua.books.ui.transfer.ImportViewModel
import io.github.terlingua.books.ui.transfer.QrScanScreen
import io.github.terlingua.books.ui.transfer.QrShareScreen
import io.github.terlingua.books.ui.transfer.QrShareViewModel

@Composable
fun TerlinguaApp(container: AppContainer, onDarkThemeChanged: (Boolean) -> Unit) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
    TerlinguaTheme(settings.theme) {
        val dark = LocalIsDarkTheme.current
        LaunchedEffect(dark) { onDarkThemeChanged(dark) }
        ProvideFeedback(container.feedback, settings) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AppNavigation(container)
            }
        }
    }
}

@Composable
private fun AppNavigation(container: AppContainer) {
    val backStack = rememberNavBackStack(LibraryKey)
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val importViewModel = viewModel { ImportViewModel(container.progress, container.books, container.incomingImports) }
    val importState by importViewModel.state.collectAsStateWithLifecycle()
    val pop: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    fun go(key: NavKey) = backStack.add(key)

    NavDisplay(
        backStack = backStack,
        onBack = pop,
        // Esc behaves like Back on keyboards, including nested list/detail panes.
        modifier = Modifier.onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp && event.key == Key.Escape && backStack.size > 1) {
                backDispatcher?.onBackPressed()
                true
            } else {
                false
            }
        },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<LibraryKey> {
                val vm = viewModel { LibraryViewModel(container.books, container.progress) }
                val state by vm.state.collectAsStateWithLifecycle()
                LibraryScreen(
                    state = state,
                    onOpenBook = { go(BookKey(it)) },
                    onSearch = { go(SearchKey) },
                    onSettings = { go(SettingsKey) },
                )
            }
            entry<BookKey> { key ->
                val vm = viewModel { BookViewModel(key.slug, container.books, container.progress) }
                BookScreen(vm, key.sectionKey, onBack = pop, onShareQr = { go(QrShareKey(key.slug)) })
            }
            entry<SearchKey> {
                val vm = viewModel { SearchViewModel(container.books) }
                val query by vm.query.collectAsStateWithLifecycle()
                val state by vm.state.collectAsStateWithLifecycle()
                SearchScreen(query, state, vm::onQueryChange, onOpen = { go(BookKey(it.bookSlug, it.sectionKey)) }, onBack = pop)
            }
            entry<SettingsKey> {
                val vm = viewModel {
                    SettingsViewModel(container.settings, container.progress, container.files, container.incomingImports, container.books)
                }
                SettingsScreen(
                    viewModel = vm,
                    onBack = pop,
                    onShowQr = { go(QrShareKey(it)) },
                    onScanQr = { go(QrScanKey) },
                    onAbout = { go(AboutKey) },
                )
            }
            entry<AboutKey> { AboutScreen(onBack = pop) }
            entry<QrShareKey> { key ->
                val vm = viewModel { QrShareViewModel(key.slug, container.progress, container.books) }
                QrShareScreen(vm, onBack = pop)
            }
            entry<QrScanKey> {
                QrScanScreen(
                    onResult = { payload ->
                        pop()
                        container.incomingImports.value = payload
                    },
                    onBack = pop,
                )
            }
        },
    )
    ImportDialog(importState, onConfirm = importViewModel::confirm, onDismiss = importViewModel::dismiss)
}
