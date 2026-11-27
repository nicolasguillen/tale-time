package com.nicolasguillen.taletime.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nicolasguillen.taletime.domain.Language
import com.nicolasguillen.taletime.domain.Settings
import com.nicolasguillen.taletime.domain.SettingsRepository
import com.nicolasguillen.taletime.domain.TextSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import okio.Path.Companion.toPath

const val SETTINGS_FILE_NAME = "taletime.preferences_pb"

/** Create only one per file and process: DataStore refuses two live instances on the same file. */
fun createPreferencesDataStore(
    path: String,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
): DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath(scope = scope, produceFile = { path.toPath() })

class DataStoreSettingsRepository(
    private val store: DataStore<Preferences>,
) : SettingsRepository {
    override val settings: Flow<Settings> = store.data.map { it.toSettings() }.distinctUntilChanged()

    override suspend fun update(transform: (Settings) -> Settings) {
        store.edit { prefs ->
            val current = prefs.toSettings()
            val next = transform(current)
            prefs[Keys.textSize] = next.textSize.name
            // Only a real choice is kept: the default stays a default, not a pick.
            if (next.language != current.language) prefs[Keys.language] = next.language.code
        }
    }

    private fun Preferences.toSettings(): Settings {
        val defaults = Settings()
        return Settings(
            textSize = TextSize.entries.firstOrNull { it.name == this[Keys.textSize] } ?: defaults.textSize,
            language = Language.of(this[Keys.language]) ?: defaults.language,
        )
    }

    private object Keys {
        val textSize = stringPreferencesKey("text_size")
        val language = stringPreferencesKey("language")
    }
}
