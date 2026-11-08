package io.github.kiritoxdone.hyperbackup.feature

import io.github.kiritoxdone.hyperbackup.domain.*
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

@Serializable enum class SettingsGroup(val title: String, val profile: ScriptProfile?) {
    BACKUP("备份默认值", ScriptProfile.BACKUP), RESTORE("恢复默认值", ScriptProfile.RESTORE),
    COMPRESSION("压缩与传输", ScriptProfile.BACKUP), SELECTION("名单与排除", null), EXECUTION("运行与日志", null),
}
fun SettingsGroup.icon() = when (this) {
    SettingsGroup.BACKUP -> MiuixIcons.Backup
    SettingsGroup.RESTORE -> MiuixIcons.Reset
    SettingsGroup.COMPRESSION -> MiuixIcons.Send
    SettingsGroup.SELECTION -> MiuixIcons.ListView
    SettingsGroup.EXECUTION -> MiuixIcons.Notes
}
fun settingsGroup(key: String): SettingsGroup = when {
    key.startsWith("restore_") || key in setOf("recovery_mode", "media_recovery") -> SettingsGroup.RESTORE
    key == "Compression_method" || key.startsWith("Zstd_") || key.startsWith("remote_") -> SettingsGroup.COMPRESSION
    key in setOf("blacklist", "blacklist_mode", "whitelist", "system", "list_location", "mount_point", "Background_apps_ignore") -> SettingsGroup.SELECTION
    key in setOf("low_battery_mode", "pc_auto_shutdown", "Shell_LANG", "setDisplayPowerMode", "log_max_size_mb") -> SettingsGroup.EXECUTION
    else -> SettingsGroup.BACKUP
}
fun settingLabel(field: ScriptField): String = settingLabels[field.key] ?: field.label
private val settingLabels = mapOf(
    "Backup_Mode" to "备份内容", "Backup_user_data" to "私有数据", "Backup_obb_data" to "外部应用数据",
    "backup_media" to "关联文件夹", "backup_messages" to "短信与彩信", "backup_calllogs" to "通话记录",
    "Custom_path" to "文件夹", "Backup_suffix" to "备份集后缀", "recovery_mode" to "恢复范围",
    "media_recovery" to "恢复文件夹", "restore_messages" to "恢复短信与彩信", "restore_calllogs" to "恢复通话记录",
    "restore_allow_low_target_sdk" to "允许旧版应用", "Compression_method" to "压缩格式", "Zstd_level" to "压缩等级",
    "Zstd_threads" to "压缩线程", "Zstd_small_max_bytes" to "小文件阈值（字节）", "Zstd_small_threads" to "小文件线程",
    "Zstd_size_hint" to "压缩大小提示", "remote_stream" to "流式传输", "remote_keep_local" to "上传后本地文件",
    "remote_upload_per_app" to "逐应用上传", "remote_address_mode" to "远端地址", "blacklist" to "黑名单",
    "blacklist_mode" to "黑名单处理", "whitelist" to "预装应用白名单", "system" to "系统应用白名单",
    "list_location" to "自定义应用名单", "mount_point" to "排除挂载点", "Background_apps_ignore" to "运行中应用",
    "low_battery_mode" to "低电量策略", "pc_auto_shutdown" to "远端关机", "Shell_LANG" to "引擎语言",
    "setDisplayPowerMode" to "亮屏伪装", "log_max_size_mb" to "日志上限（MiB）",
)
fun settingOption(label: String): String = when (label) {
    "開啟" -> "开启"; "關閉" -> "关闭"; "留空（沿用原規則）", "留空（原預設）" -> "默认"
    "留空（開始時確認）", "留空（啟動時選擇）" -> "开始时询问"
    "留空（自動識別系統語言）" -> "跟随系统"; "繁體中文" -> "繁體中文"; "簡體中文" -> "简体中文"
    "低電量強制拒絕" -> "低电量时停止"; "無需提示，繼續執行" -> "继续执行"
    "僅備份 APK" -> "仅安装包"; "備份 APK 與資料" -> "安装包与数据"; "完全略過" -> "跳过"
    "繼續處理執行中的應用" -> "继续处理"; "略過執行中的應用" -> "跳过"
    "上傳成功後刪除本機檔案" -> "上传成功后删除"; "保留本機檔案" -> "保留"
    "全部恢復" -> "全部所选应用"; "僅恢復未安裝應用" -> "仅未安装应用"
    "手動地址" -> "手动地址"; "自動追蹤設備" -> "自动跟踪设备"
    "Zstandard 壓縮" -> "Zstandard"; "Tar 僅打包，不壓縮" -> "Tar"
    else -> label
}

fun ToolGroup.icon() = when (this) {
    ToolGroup.APPS -> MiuixIcons.GridView
    ToolGroup.OTHER_DATA -> MiuixIcons.Folder
    ToolGroup.TRANSFER -> MiuixIcons.Send
    ToolGroup.CONNECTION -> MiuixIcons.SearchDevice
    ToolGroup.MAINTENANCE -> MiuixIcons.Settings
}
fun ConfigGroup.icon() = when (this) {
    ConfigGroup.CONTENT -> MiuixIcons.Backup
    ConfigGroup.SET -> MiuixIcons.Folder
    ConfigGroup.TARGET -> MiuixIcons.GridView
    ConfigGroup.SAFETY -> MiuixIcons.Copy
    ConfigGroup.COMPRESSION -> MiuixIcons.Backup
    ConfigGroup.TRANSFER -> MiuixIcons.Send
    ConfigGroup.SELECTION -> MiuixIcons.ListView
    ConfigGroup.INSTALL -> MiuixIcons.Download
    ConfigGroup.EXECUTION -> MiuixIcons.Notes
    ConfigGroup.EXPERT -> MiuixIcons.Settings
}
