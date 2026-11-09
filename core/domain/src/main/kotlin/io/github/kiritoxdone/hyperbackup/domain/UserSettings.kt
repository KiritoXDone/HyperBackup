package io.github.kiritoxdone.hyperbackup.domain

import kotlinx.serialization.Serializable

@Serializable enum class AppSort { NAME, PACKAGE }
@Serializable enum class ScriptProfile { BACKUP, RESTORE }
@Serializable data class AppBehavior(
    val connectOnLaunch: Boolean = false,
    val keepScreenOnDuringTask: Boolean = false,
    val completionNotification: Boolean = true,
    val showPackageNames: Boolean = true,
    val showSystemApps: Boolean = false,
    val appSort: AppSort = AppSort.NAME, val sortDescending: Boolean = false,
    val dynamicColor: Boolean = false,
    val labels: Map<String, Set<String>> = emptyMap(),
)
@Serializable data class EnginePreferences(
    val backup: Map<String, String> = emptyMap(),
    val restore: Map<String, String> = emptyMap(),
    val associatedWifi: Boolean = true,
    val safetyCopy: Boolean = true,
)
@Serializable data class SettingsArchive(
    val schema: Int = 1, val upstream: String = Wire.UPSTREAM,
    val engine: EnginePreferences, val behavior: AppBehavior, val theme: String = "system",
)

object ScriptPreferences {
    val runtimeOwned = setOf("update", "cdn", "background_execution", "notification_enable", "rgb_a", "rgb_b", "rgb_c")
    val locationOwned = setOf("Output_path", "user", "remote_type", "smb_url", "smb_remote_user", "smb_remote_pass",
        "webdav_url", "webdav_remote_user", "webdav_remote_pass", "remote_smb_identity", "remote_webdav_identity")
    fun profile(command: ScriptCommand) = if (command.restore && !command.wireName.startsWith("remote_")) ScriptProfile.RESTORE else ScriptProfile.BACKUP
    fun defaults(catalog: ScriptCatalog, profile: ScriptProfile) = if (profile == ScriptProfile.BACKUP) catalog.backupDefaults else catalog.restoreDefaults
    fun editable(key: String) = key !in runtimeOwned && key !in locationOwned
    fun overrides(settings: EnginePreferences, profile: ScriptProfile) = if (profile == ScriptProfile.BACKUP) settings.backup else settings.restore
    fun effective(catalog: ScriptCatalog, settings: EnginePreferences, profile: ScriptProfile) = defaults(catalog, profile) + overrides(settings, profile)
    fun applyToNewDraft(draft: Draft, preferences: EnginePreferences): Draft {
        val spec = requireNotNull(draft.script)
        val saved = overrides(preferences, profile(spec.command)) +
            if (spec.command.restore && spec.command.wireName.startsWith("remote_")) preferences.restore else emptyMap()
        return draft.copy(safetyCopy = preferences.safetyCopy,
            script = spec.copy(config = saved + spec.config, associatedWifi = preferences.associatedWifi))
    }
    fun validate(catalog: ScriptCatalog, profile: ScriptProfile, key: String, value: String) {
        require(editable(key) && key in defaults(catalog, profile)) { "SETTING_NOT_EDITABLE:$key" }
        ScriptConfiguration.encode(mapOf(key to value), catalog.fields.map { it.key }.toSet())
        val options = catalog.fields.find { it.key == key }?.options.orEmpty()
        require(options.isEmpty() || options.any { it.firstOrNull() == value }) { "SETTING_VALUE_INVALID:$key" }
        if (value.isNotEmpty()) when (key) {
            "Zstd_level" -> require(value.toIntOrNull() in 1..22) { "压缩等级应为 1–22" }
            "Zstd_threads", "Zstd_small_threads" -> require(value.toIntOrNull() in 0..256) { "线程数应为 0–256" }
            "Zstd_small_max_bytes", "log_max_size_mb" -> require(value.toLongOrNull()?.let { it >= 0 } == true) { "请输入非负整数" }
        }
    }
    fun update(catalog: ScriptCatalog, settings: EnginePreferences, profile: ScriptProfile?, key: String, value: String): EnginePreferences {
        var result = settings
        val targets = ScriptProfile.entries.filter { profile == null || it == profile }.filter { key in defaults(catalog, it) }
        require(targets.isNotEmpty()) { "SETTING_NOT_EDITABLE:$key" }
        targets.forEach { target ->
            validate(catalog, target, key, value)
            result = if (target == ScriptProfile.BACKUP) result.copy(backup = result.backup + (key to value)) else result.copy(restore = result.restore + (key to value))
        }
        return result
    }
    fun validateArchive(catalog: ScriptCatalog, archive: SettingsArchive) {
        require(archive.theme in setOf("system", "light", "dark")) { "主题设置无效" }
        AppLabels.validate(archive.behavior.labels)
        require(archive.schema == 1 && archive.upstream == Wire.UPSTREAM) { "配置版本不匹配" }
        ScriptProfile.entries.forEach { profile -> overrides(archive.engine, profile).forEach { (key, value) -> validate(catalog, profile, key, value) } }
    }
}
