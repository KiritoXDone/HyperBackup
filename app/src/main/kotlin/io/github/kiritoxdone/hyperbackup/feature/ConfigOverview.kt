package io.github.kiritoxdone.hyperbackup.feature

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.preference.SwitchPreference
import androidx.compose.ui.Modifier
import io.github.kiritoxdone.hyperbackup.*
import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.ui.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.ArrowPreference

internal fun AppState.configDefaults(): Map<String, String> = if (draft?.script?.command?.restore == true && draft.script?.command?.wireName?.startsWith("remote_") != true)
    scriptCatalog?.restoreDefaults.orEmpty() else scriptCatalog?.backupDefaults.orEmpty()

internal fun AppState.configFields(): List<ScriptField> = scriptCatalog?.fields.orEmpty().filterNot {
    !ScriptPreferences.editable(it.key) || it.secret || it.key.endsWith("_user") || it.key.endsWith("_url") || it.key in setOf("remote_type", "user", "Output_path", "Backup_suffix")
}.filter { it.key in configDefaults() || it.key in draft?.script?.config.orEmpty() }

internal fun fieldValue(state: AppState, field: ScriptField): String = state.draft?.script?.config?.get(field.key) ?: state.configDefaults()[field.key].orEmpty()
internal fun fieldSummary(field: ScriptField, value: String): String {
    field.options.firstOrNull { it.firstOrNull() == value }?.getOrNull(1)?.let { return settingOption(it) }
    if (value.isBlank()) return "未设置"
    val lines = value.lines().filter { it.isNotBlank() && !it.trimStart().startsWith('#') }
    return if (value.contains('\n')) "${lines.size} 项" else value.take(70)
}

@Composable fun ConfigPage(s: AppState, vm: AppViewModel, padding: PaddingValues, navigate: (Route) -> Unit) {
    val draft = s.draft
    val spec = draft?.script
    val destination = s.destinations.find { it.id == draft?.destinationId }
    PageList(padding) {
        if (draft == null || spec == null) item { EmptyContent(HyperIcons.Backup, "尚未选择内容") } else {
            item { SectionLabel(if (spec.command.restore) "备份来源" else "保存位置") }
            if (!spec.command.restore) item { Segments(listOf("本地", "云端"), if (destination?.protocol == Protocol.LOCAL) 0 else 1, { navigate(Route.ConfigLocation(false)) }) }
            item { SurfaceCard {
                if (spec.command.restore) BasicComponent(title = destination?.name ?: "备份来源", summary = destination?.address)
                else ArrowPreference(destination?.name ?: "选择位置", summary = destination?.address, onClick = { navigate(Route.ConfigLocation(false)) })
            } }
            if (!spec.command.restore) {
                item { SectionLabel("压缩") }
                item { CompressionOptions(s, vm) }
            }
            item { SectionLabel(if (spec.command.restore) "恢复内容" else "备份内容") }
            item { SurfaceCard {
                ArrowPreference("用户与来源", summary = if (spec.selectedApps.isNotEmpty()) "${spec.selectedApps.size} 个应用 · ${spec.selectedApps.map { it.userId }.distinct().size} 个用户" else "用户 ${spec.userId}",
                    onClick = { navigate(Route.ConfigSection(ConfigGroup.TARGET)) })
                if (spec.command == ScriptCommand.BACKUP_FOLDERS) ArrowPreference("文件夹", summary = spec.config["Custom_path"]?.lines()?.count { it.isNotBlank() }?.let { "$it 项" } ?: "未选择",
                    onClick = { navigate(Route.ConfigField("Custom_path")) })
                if (spec.command != ScriptCommand.BACKUP_FOLDERS && s.configFields().any { configGroup(it.key) == ConfigGroup.CONTENT })
                    ArrowPreference("数据组成", onClick = { navigate(Route.ConfigSection(ConfigGroup.CONTENT)) })
                if (spec.command in setOf(ScriptCommand.RESTORE_APPS, ScriptCommand.RESTORE_SSAID_APPS))
                    SwitchPreference(spec.command == ScriptCommand.RESTORE_SSAID_APPS, { checked -> vm.changeDraft { d ->
                        d.copy(script = requireNotNull(d.script).copy(command = if (checked) ScriptCommand.RESTORE_SSAID_APPS else ScriptCommand.RESTORE_APPS))
                    } }, "恢复 SSAID", summary = "恢复后需重启")
                if (spec.command.restore) {
                    SwitchPreference(draft.safetyCopy, { checked -> vm.changeDraft { it.copy(safetyCopy = checked) } }, "恢复前保存副本")
                    if (draft.safetyCopy) ArrowPreference("副本位置", summary = s.destinations.find { it.id == draft.safetyDestinationId }?.name,
                        onClick = { navigate(Route.ConfigLocation(true)) })
                }
            } }
            item { SectionLabel("行为") }
            item { SurfaceCard {
                if (spec.command in setOf(ScriptCommand.BACKUP_APPS, ScriptCommand.BACKUP_UPDATED, ScriptCommand.BACKUP_FOLDERS, ScriptCommand.BACKUP_WIFI))
                    ArrowPreference("备份集方式", summary = if (spec.setMode == BackupSetMode.NEW) "新建备份集" else "更新已有备份集", onClick = { navigate(Route.ConfigSection(ConfigGroup.SET)) })
                ArrowPreference("高级设置", onClick = { navigate(Route.AdvancedConfig) })
            } }
        }
    }
}

@Composable private fun CompressionOptions(s: AppState, vm: AppViewModel) {
    val spec = s.draft?.script ?: return
    val field = s.configFields().find { it.key == "Compression_method" } ?: return
    val method = fieldValue(s, field)
    val rawLevel = spec.config["Zstd_level"] ?: s.configDefaults()["Zstd_level"].orEmpty()
    var level by remember(rawLevel) { mutableFloatStateOf(rawLevel.toFloatOrNull()?.coerceIn(1f, 22f) ?: 3f) }
    fun update(key: String, value: String) = vm.changeDraft { it.copy(script = requireNotNull(it.script).let { script -> script.copy(config = script.config + (key to value)) }) }
    SurfaceCard(insideMargin = PaddingValues(16.dp)) {
        if (field.options.isNotEmpty()) Segments(field.options.map { option -> settingOption(option.getOrElse(1) { option[0] }) },
            field.options.indexOfFirst { it[0] == method }.coerceAtLeast(0), { update(field.key, field.options[it][0]) })
        if (method.lowercase() != "tar") {
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("压缩等级", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Text(if (rawLevel.isEmpty()) "默认" else level.toInt().toString(), color = MiuixTheme.colorScheme.primary)
            }
            Slider(level, { level = it }, Modifier.fillMaxWidth(), valueRange = 1f..22f, steps = 20,
                onValueChangeFinished = { update("Zstd_level", level.toInt().toString()) })
            Row(Modifier.fillMaxWidth()) {
                Text("1 · 更快", Modifier.weight(1f), style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Text("22 · 更小", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
    }
}

@Composable fun AdvancedConfigPage(s: AppState, vm: AppViewModel, padding: PaddingValues, navigate: (Route) -> Unit) {
    val groups = s.configFields().map { configGroup(it.key) }.distinct().filter { it != ConfigGroup.CONTENT }
    PageList(padding) {
        notices(s, vm)
        item { SurfaceCard(Modifier.fillMaxWidth()) {
            ConfigGroup.entries.filter { it in groups }.forEach { group ->
                ArrowPreference(group.title, startAction = { LeadingIcon(group.icon()) }, onClick = { navigate(Route.ConfigSection(group)) })
            }
        } }
    }
}

@Composable fun ConfigLocationPage(s: AppState, vm: AppViewModel, padding: PaddingValues, safety: Boolean, back: () -> Unit) {
    PageList(padding) {
        item { SurfaceCard(Modifier.fillMaxWidth()) {
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
