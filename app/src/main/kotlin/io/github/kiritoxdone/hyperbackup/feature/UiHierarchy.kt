package io.github.kiritoxdone.hyperbackup.feature

import io.github.kiritoxdone.hyperbackup.domain.ScriptCommand
import kotlinx.serialization.Serializable

@Serializable enum class ToolGroup(val title: String, val summary: String) {
    APPS("应用与状态", "应用备份、恢复、应用状态和名单"),
    OTHER_DATA("其他数据", "Wi-Fi 与自定义文件夹"),
    TRANSFER("远端传输", "上传、远端浏览和恢复方式"),
    CONNECTION("连接与测速", "发现设备、检查连接和测速"),
    MAINTENANCE("检查与维护", "完整性检查、修复和清理"),
}

fun ScriptCommand.group(): ToolGroup = when (this) {
    ScriptCommand.BACKUP_APPS, ScriptCommand.BACKUP_UPDATED, ScriptCommand.RESTORE_APPS,
    ScriptCommand.RESTORE_SSAID_APPS, ScriptCommand.RESTORE_APP_STATE, ScriptCommand.INVENTORY -> ToolGroup.APPS
    ScriptCommand.BACKUP_FOLDERS, ScriptCommand.RESTORE_FOLDERS, ScriptCommand.BACKUP_WIFI, ScriptCommand.RESTORE_WIFI -> ToolGroup.OTHER_DATA
    ScriptCommand.REMOTE_RESTORE_STAGED, ScriptCommand.REMOTE_RESTORE_STREAM, ScriptCommand.UPLOAD_EXISTING, ScriptCommand.REMOTE_CATALOG -> ToolGroup.TRANSFER
    ScriptCommand.REMOTE_HEALTH, ScriptCommand.SMB_DISCOVER, ScriptCommand.SMB_DISCOVER_APPLY,
    ScriptCommand.WEBDAV_DISCOVER, ScriptCommand.WEBDAV_DISCOVER_ADVANCED, ScriptCommand.REMOTE_SPEED -> ToolGroup.CONNECTION
    ScriptCommand.REMOTE_CLEANUP, ScriptCommand.STATISTICS, ScriptCommand.VERIFY_ARCHIVES,
    ScriptCommand.VERIFY_METADATA, ScriptCommand.REBUILD_METADATA, ScriptCommand.CONVERT_NAMES -> ToolGroup.MAINTENANCE
}

@Serializable enum class ConfigGroup(val title: String, val summary: String) {
    CONTENT("备份与恢复内容", "应用数据、通信记录和关联目录"),
    SET("备份集方式", "新建或更新已有备份集"),
    TARGET("用户与来源", "用户、备份目录和应用范围"),
    SAFETY("恢复前副本", "覆盖前保存位置和保护选项"),
    COMPRESSION("压缩", "格式、等级和线程"),
    TRANSFER("传输", "流式传输、上传和本地保留"),
    SELECTION("名单与排除", "黑名单、系统白名单和运行中应用"),
    INSTALL("安装与恢复策略", "版本处理和低 target SDK 选项"),
    EXECUTION("运行与日志", "低电量、亮屏、日志和语言"),
    EXPERT("兼容与高级", "其他上游配置"),
}

fun configGroup(key: String): ConfigGroup = when {
    key in setOf("Backup_Mode", "Backup_user_data", "Backup_obb_data", "backup_media", "backup_messages", "backup_calllogs", "Custom_path", "media_recovery", "restore_messages", "restore_calllogs") -> ConfigGroup.CONTENT
    key == "Compression_method" || key.startsWith("Zstd_") -> ConfigGroup.COMPRESSION
    key in setOf("remote_stream", "remote_keep_local", "remote_upload_per_app", "remote_address_mode", "remote_smb_identity", "remote_webdav_identity") -> ConfigGroup.TRANSFER
    key in setOf("Background_apps_ignore", "blacklist", "blacklist_mode", "whitelist", "system", "list_location") -> ConfigGroup.SELECTION
    key in setOf("recovery_mode", "restore_allow_low_target_sdk") -> ConfigGroup.INSTALL
    key in setOf("low_battery_mode", "setDisplayPowerMode", "pc_auto_shutdown", "log_max_size_mb", "Shell_LANG", "mount_point") -> ConfigGroup.EXECUTION
    else -> ConfigGroup.EXPERT
}
