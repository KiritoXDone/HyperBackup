package io.github.kiritoxdone.hyperbackup.data

import android.content.Context
import io.github.kiritoxdone.hyperbackup.domain.*
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.backupPreferences by preferencesDataStore("preferences")
class Preferences(context: Context) {
    private val store = context.backupPreferences
    val behavior = store.data.map { preferences -> preferences[stringPreferencesKey("app_behavior")]?.let { Wire.json.decodeFromString<AppBehavior>(it) } ?: AppBehavior() }
    val engine = store.data.map { preferences -> preferences[stringPreferencesKey("engine_preferences")]?.let { Wire.json.decodeFromString<EnginePreferences>(it) } ?: EnginePreferences() }
    suspend fun behavior(value: AppBehavior) { store.edit { it[stringPreferencesKey("app_behavior")] = Wire.json.encodeToString(AppBehavior.serializer(), value) } }
    suspend fun engine(value: EnginePreferences) { store.edit { it[stringPreferencesKey("engine_preferences")] = Wire.json.encodeToString(EnginePreferences.serializer(), value) } }
    suspend fun importSettings(value: SettingsArchive) { store.edit {
        it[stringPreferencesKey("app_behavior")] = Wire.json.encodeToString(AppBehavior.serializer(), value.behavior)
        it[stringPreferencesKey("engine_preferences")] = Wire.json.encodeToString(EnginePreferences.serializer(), value.engine)
    } }
    val theme = store.data.map { it[stringPreferencesKey("theme")] ?: "system" }
    suspend fun theme(value: String) { require(value in setOf("system", "light", "dark")); store.edit { it[stringPreferencesKey("theme")] = value } }
    suspend fun selectedDestination(): String? = store.data.first()[stringPreferencesKey("destination")]
    suspend fun selectDestination(id: String) { store.edit { it[stringPreferencesKey("destination")] = id } }
    suspend fun draft(): String? = store.data.first()[stringPreferencesKey("draft")]
    suspend fun draft(value: String) { store.edit { it[stringPreferencesKey("draft")] = value } }
}
