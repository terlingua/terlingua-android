package io.github.terlingua.books

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.terlingua.books.data.ProgressFiles
import io.github.terlingua.books.data.content.ContentBookRepository
import io.github.terlingua.books.data.content.ContentDatabase
import io.github.terlingua.books.data.progress.ProgressDatabase
import io.github.terlingua.books.data.progress.RoomProgressRepository
import io.github.terlingua.books.data.settings.DataStoreSettingsRepository
import io.github.terlingua.books.domain.BookRepository
import io.github.terlingua.books.domain.ProgressRepository
import io.github.terlingua.books.domain.SettingsRepository
import io.github.terlingua.books.feedback.FeedbackPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** Manual dependency graph; the app is small enough not to need a DI framework. */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val books: BookRepository = ContentBookRepository(ContentDatabase(app))

    private val progressDatabase: ProgressDatabase =
        Room.databaseBuilder<ProgressDatabase>(app, "terlingua-progress.db")
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

    val progress: ProgressRepository = RoomProgressRepository(progressDatabase.progressDao())

    val settings: SettingsRepository = DataStoreSettingsRepository(app.settingsStore)

    val files = ProgressFiles(app)

    val feedback = FeedbackPlayer(app)

    /** Progress documents received from other apps (share sheet, file managers) awaiting review. */
    val incomingImports = MutableStateFlow<String?>(null)
}
