package io.github.kiritoxdone.hyperbackup.domain

import org.junit.Assert.*
import org.junit.Test

class ScriptPreferencesTest {
    private val catalog = ScriptCatalog(fields = listOf(
        ScriptField("Backup_Mode", "mode", listOf(listOf("0", "apk"), listOf("1", "all"))),
        ScriptField("Zstd_level", "level"), ScriptField("low_battery_mode", "battery"),
        ScriptField("restore_messages", "sms"), ScriptField("smb_remote_pass", "password", secret = true)),
        backupDefaults = mapOf("Backup_Mode" to "1", "Zstd_level" to "6", "low_battery_mode" to "", "smb_remote_pass" to ""),
        restoreDefaults = mapOf("low_battery_mode" to "", "restore_messages" to "0"))
    @Test fun sharedSettingUpdatesBothProfilesAndSurvivesSerialization() {
        val settings = ScriptPreferences.update(catalog, EnginePreferences(), null, "low_battery_mode", "2")
        assertEquals("2", settings.backup["low_battery_mode"])
        assertEquals("2", settings.restore["low_battery_mode"])
        assertEquals(settings, Wire.json.decodeFromString<EnginePreferences>(Wire.json.encodeToString(EnginePreferences.serializer(), settings)))
    }
    @Test fun rejectsCredentialsUnknownKeysAndInvalidValues() {
        for ((key, value) in listOf("smb_remote_pass" to "secret", "unknown" to "1", "Zstd_level" to "23", "Backup_Mode" to "unsafe")) {
            assertThrows(IllegalArgumentException::class.java) { ScriptPreferences.update(catalog, EnginePreferences(), ScriptProfile.BACKUP, key, value) }
        }
    }
    @Test fun newPlanFreezesDefaultsAndKeepsExplicitTaskOverrides() {
        val raw = ScriptPlanScope.backup(Wire.id(), setOf(AppKey(0, "com.example.fixture")), 0, mapOf("Backup_Mode" to "0"))
        val preferences = EnginePreferences(backup = mapOf("Backup_Mode" to "1", "Zstd_level" to "9"), associatedWifi = false)
        val frozen = ScriptPreferences.applyToNewDraft(raw, preferences)
        assertEquals("0", frozen.script?.config?.get("Backup_Mode"))
        assertEquals("9", frozen.script?.config?.get("Zstd_level"))
        assertEquals(false, frozen.script?.associatedWifi)
        val updated = preferences.copy(backup = preferences.backup + ("Zstd_level" to "3"))
        assertEquals("3", ScriptPreferences.applyToNewDraft(raw, updated).script?.config?.get("Zstd_level"))
        assertEquals("9", frozen.script?.config?.get("Zstd_level"))
    }
    @Test fun importedSettingsMustMatchTheBundledEngine() {
        val archive = SettingsArchive(engine = EnginePreferences(), behavior = AppBehavior())
        ScriptPreferences.validateArchive(catalog, archive)
        assertThrows(IllegalArgumentException::class.java) { ScriptPreferences.validateArchive(catalog, archive.copy(upstream = "different")) }
        assertThrows(IllegalArgumentException::class.java) { ScriptPreferences.validateArchive(catalog, archive.copy(engine = EnginePreferences(backup = mapOf("smb_remote_pass" to "secret")))) }
    }
}
