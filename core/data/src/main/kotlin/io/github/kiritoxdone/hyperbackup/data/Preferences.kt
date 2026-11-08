package io.github.kiritoxdone.hyperbackup.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.backupPreferences by preferencesDataStore("preferences")
class Preferences(context: Context) {
    private val store = context.backupPreferences
    val theme = store.data.map { it[stringPreferencesKey("theme")] ?: "system" }
    suspend fun theme(value: String) { require(value in setOf("system", "light", "dark")); store.edit { it[stringPreferencesKey("theme")] = value } }
    suspend fun selectedDestination(): String? = store.data.first()[stringPreferencesKey("destination")]
    suspend fun selectDestination(id: String) { store.edit { it[stringPreferencesKey("destination")] = id } }
    suspend fun draft(): String? = store.data.first()[stringPreferencesKey("draft")]
    suspend fun draft(value: String) { store.edit { it[stringPreferencesKey("draft")] = value } }
}
