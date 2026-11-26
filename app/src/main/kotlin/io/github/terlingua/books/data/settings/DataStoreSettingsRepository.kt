package io.github.terlingua.books.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.terlingua.books.domain.Settings
import io.github.terlingua.books.domain.SettingsRepository
import io.github.terlingua.books.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {

    override val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            theme = p[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.System,
            sound = p[SOUND] ?: true,
            haptics = p[HAPTICS] ?: true,
        )
    }.distinctUntilChanged()

    override suspend fun setTheme(theme: ThemeMode) {
        store.edit { it[THEME] = theme.name }
    }

    override suspend fun setSound(enabled: Boolean) {
        store.edit { it[SOUND] = enabled }
    }

    override suspend fun setHaptics(enabled: Boolean) {
        store.edit { it[HAPTICS] = enabled }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val SOUND = booleanPreferencesKey("sound")
        val HAPTICS = booleanPreferencesKey("haptics")
    }
}
