package io.github.kiritoxdone.hyperbackup.domain

import kotlinx.serialization.Serializable

@Serializable enum class ScriptCommand(val wireName: String, val label: String, val restore: Boolean = false) {
    BACKUP_APPS("backup_apps", "备份应用"), BACKUP_UPDATED("backup_updated", "备份已更新应用"),
    BACKUP_FOLDERS("backup_folders", "备份自定义目录"), BACKUP_WIFI("backup_wifi", "备份 Wi-Fi"),
    RESTORE_APPS("restore_apps", "恢复应用", true), RESTORE_SSAID_APPS("restore_ssaid_apps", "恢复含 SSAID 的应用", true),
    RESTORE_APP_STATE("restore_app_state", "独立恢复应用状态", true), RESTORE_FOLDERS("restore_folders", "恢复自定义目录", true),
    RESTORE_WIFI("restore_wifi", "恢复 Wi-Fi", true), REMOTE_RESTORE_STAGED("remote_restore_staged", "远端下载后恢复", true),
    REMOTE_RESTORE_STREAM("remote_restore_stream", "远端流式恢复", true), UPLOAD_EXISTING("upload_existing", "独立上传已有备份"),
    REMOTE_CATALOG("remote_catalog", "读取远端备份清单"), REMOTE_CLEANUP("remote_cleanup", "清理远端已卸载应用"),
    REMOTE_HEALTH("remote_health", "远端健康检查"), SMB_DISCOVER("smb_discover", "发现局域网 SMB"),
    SMB_DISCOVER_APPLY("smb_discover_apply", "发现并试用 SMB 共享"), WEBDAV_DISCOVER("webdav_discover", "发现局域网 WebDAV"),
    WEBDAV_DISCOVER_ADVANCED("webdav_discover_advanced", "高级 WebDAV 发现"), REMOTE_SPEED("remote_speed", "远端测速（5 GiB）"),
    STATISTICS("statistics", "备份统计"), VERIFY_ARCHIVES("verify_archives", "归档完整性检查"),
    VERIFY_METADATA("verify_metadata", "JSON 结构检查"), REBUILD_METADATA("rebuild_metadata", "重建备份元数据"),
    CONVERT_NAMES("convert_names", "转换备份目录名称"), INVENTORY("inventory", "生成脚本应用清单"),
}

@Serializable enum class BackupSetMode { NEW, UPDATE }
@Serializable data class ScriptSpec(
    val command: ScriptCommand, val workingDirectory: String, val userId: Int,
    val config: Map<String, String> = emptyMap(), val selectedPackages: List<String> = emptyList(),
    val preserveExistingList: Boolean = false, val setMode: BackupSetMode = BackupSetMode.NEW, val existingSet: String = "",
    val selectedApps: List<AppKey> = emptyList(), val associatedWifi: Boolean = true,
)
@Serializable data class ScriptField(val key: String, val label: String, val options: List<List<String>> = emptyList(), val secret: Boolean = false)
@Serializable data class ScriptCatalog(val upstream: String = Wire.UPSTREAM, val commands: List<ScriptCommand> = ScriptCommand.entries, val fields: List<ScriptField>,
    val backupDefaults: Map<String, String> = emptyMap(), val restoreDefaults: Map<String, String> = emptyMap())
@Serializable data class EnginePrompt(val taskId: String, val id: String, val title: String, val options: List<String>, val cancel: String, val mode: String)
@Serializable data class PromptAnswer(val taskId: String, val id: String, val answer: String)
@Serializable data class EditorRow(val id: String, val label: String, val selected: Boolean, val apk: Boolean, val packageName: String)
@Serializable data class ScriptFormValue(val key: String, val value: String)
@Serializable data class EditorSnapshot(val taskId: String, val id: String, val rows: List<EditorRow>, val timeline: String?, val mode: String,
    val authSetup: Boolean = false, val fields: List<ScriptFormValue> = emptyList(), val restoreConfig: Boolean = false)
@Serializable data class EditorAnswer(val taskId: String, val id: String, val action: String, val body: String = "")

object ScriptConfiguration {
    fun encode(values: Map<String, String>, allowed: Set<String>): String = values.toSortedMap().entries.joinToString("") { (key, value) ->
        require(key in allowed && key.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) { "UNKNOWN_CONFIG_KEY" }
        require(value.length <= 65536 && '\u0000' !in value && '\r' !in value) { "INVALID_CONFIG_VALUE" }
        // This is the upstream literal codec. Its parser, not Shell source/eval, reads the file.
        val quoted = value.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$").replace("`", "\\`")
        "$key=\"$quoted\"\n"
    }
}
