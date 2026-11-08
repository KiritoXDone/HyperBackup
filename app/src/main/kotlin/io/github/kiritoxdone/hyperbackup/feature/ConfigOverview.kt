package io.github.kiritoxdone.hyperbackup.feature

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.kiritoxdone.hyperbackup.*
import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.ui.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.ArrowPreference

internal fun AppState.configDefaults(): Map<String, String> = if (draft?.script?.command?.restore == true && draft.script?.command?.wireName?.startsWith("remote_") != true)
    scriptCatalog?.restoreDefaults.orEmpty() else scriptCatalog?.backupDefaults.orEmpty()

internal fun AppState.configFields(): List<ScriptField> = scriptCatalog?.fields.orEmpty().filterNot {
    it.secret || it.key.endsWith("_user") || it.key.endsWith("_url") || it.key in setOf("remote_type", "user", "Output_path", "Backup_suffix")
}.filter { it.key in configDefaults() || it.key in draft?.script?.config.orEmpty() }

internal fun fieldValue(state: AppState, field: ScriptField): String = state.draft?.script?.config?.get(field.key) ?: state.configDefaults()[field.key].orEmpty()
internal fun fieldSummary(field: ScriptField, value: String): String {
    field.options.firstOrNull { it.firstOrNull() == value }?.getOrNull(1)?.let { return it }
    if (value.isBlank()) return "未设置"
    val lines = value.lines().filter { it.isNotBlank() && !it.trimStart().startsWith('#') }
    return if (value.contains('\n')) "${lines.size} 项" else value.take(70)
}

@Composable fun ConfigPage(s: AppState, vm: AppViewModel, padding: PaddingValues, navigate: (Route) -> Unit) {
    val draft = s.draft
    val spec = draft?.script
    PageList(padding) {
        notices(s, vm)
        if (draft == null || spec == null) item { InfoCard("尚未选择内容", "返回应用或备份库，选择本次操作。") } else {
            item { SectionLabel("本次操作") }
            item { Card(Modifier.fillMaxWidth()) {
                ArrowPreference("备份位置", summary = s.destinations.find { it.id == draft.destinationId }?.name ?: "请选择", onClick = { navigate(Route.ConfigLocation(false)) })
                if (spec.command in setOf(ScriptCommand.BACKUP_APPS, ScriptCommand.BACKUP_UPDATED, ScriptCommand.BACKUP_FOLDERS, ScriptCommand.BACKUP_WIFI)) {
                    ArrowPreference("备份集方式", summary = if (spec.setMode == BackupSetMode.NEW) "新建备份集" else "更新已有备份集", onClick = { navigate(Route.ConfigSection(ConfigGroup.SET)) })
                }
                ArrowPreference("用户与来源", summary = if (spec.selectedApps.isNotEmpty()) "${spec.selectedApps.size} 个应用 · ${spec.selectedApps.map { it.userId }.distinct().size} 个用户" else "用户 ${spec.userId}", onClick = { navigate(Route.ConfigSection(ConfigGroup.TARGET)) })
                if (spec.command == ScriptCommand.BACKUP_FOLDERS) ArrowPreference("文件夹", summary = spec.config["Custom_path"]?.lines()?.count { it.isNotBlank() }?.let { "$it 项" } ?: "未选择",
                    onClick = { navigate(Route.ConfigField("Custom_path")) })
                if (spec.command != ScriptCommand.BACKUP_FOLDERS && s.configFields().any { configGroup(it.key) == ConfigGroup.CONTENT }) ArrowPreference("数据内容", summary = "应用数据、通信记录与关联目录", onClick = { navigate(Route.ConfigSection(ConfigGroup.CONTENT)) })
                if (spec.command.restore) ArrowPreference("恢复前副本", summary = if (draft.safetyCopy) "已开启" else "本次关闭", onClick = { navigate(Route.ConfigSection(ConfigGroup.SAFETY)) })
            } }
            item { SectionLabel("其他选项") }
            item { Card(Modifier.fillMaxWidth()) {
                ArrowPreference("高级设置", summary = "压缩、传输、名单、执行与日志", onClick = { navigate(Route.AdvancedConfig) })
            } }
        }
    }
}

@Composable fun AdvancedConfigPage(s: AppState, vm: AppViewModel, padding: PaddingValues, navigate: (Route) -> Unit) {
    val groups = s.configFields().map { configGroup(it.key) }.distinct().filter { it != ConfigGroup.CONTENT }
    PageList(padding) {
        notices(s, vm)
        item { Card(Modifier.fillMaxWidth()) {
            ConfigGroup.entries.filter { it in groups }.forEach { group ->
                ArrowPreference(group.title, summary = group.summary, onClick = { navigate(Route.ConfigSection(group)) })
            }
        } }
    }
}

@Composable fun ConfigLocationPage(s: AppState, vm: AppViewModel, padding: PaddingValues, safety: Boolean, back: () -> Unit) {
    PageList(padding) {
        item { Card(Modifier.fillMaxWidth()) {
            s.destinations.forEach { destination ->
                top.yukonga.miuix.kmp.preference.RadioButtonPreference(destination.name,
                    destination.id == if (safety) s.draft?.safetyDestinationId else s.draft?.destinationId,
                    onClick = { vm.changeDraft { if (safety) it.copy(safetyDestinationId = destination.id) else it.copy(destinationId = destination.id) }; back() },
                    summary = "${destination.protocol} · ${destination.address}")
            }
        } }
        if (s.destinations.isEmpty()) item { InfoCard("还没有位置", "请先到设置中添加备份位置。") }
    }
}
