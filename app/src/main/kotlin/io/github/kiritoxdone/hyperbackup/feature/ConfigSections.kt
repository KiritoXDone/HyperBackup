package io.github.kiritoxdone.hyperbackup.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kiritoxdone.hyperbackup.*
import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.ui.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.*

@Composable fun ConfigSectionPage(s: AppState, vm: AppViewModel, padding: PaddingValues, group: ConfigGroup, navigate: (Route) -> Unit) {
    val draft = s.draft ?: return
    val spec = draft.script ?: return
    fun update(change: (ScriptSpec) -> ScriptSpec) = vm.changeDraft { it.copy(script = change(requireNotNull(it.script))) }
    PageList(padding) {
        notices(s, vm)
        when (group) {
            ConfigGroup.SET -> {
                item { Card(Modifier.fillMaxWidth()) {
                    RadioButtonPreference("新建备份集", spec.setMode == BackupSetMode.NEW, { update { it.copy(setMode = BackupSetMode.NEW) } }, summary = "默认。保留已有备份，生成独立的新备份集。")
                    RadioButtonPreference("更新已有备份集", spec.setMode == BackupSetMode.UPDATE, { update { it.copy(setMode = BackupSetMode.UPDATE) } }, summary = "比较所选备份集并跳过未变化内容。")
                } }
                if (spec.setMode == BackupSetMode.UPDATE) item { TextField(spec.existingSet, { value -> update { it.copy(existingSet = value) } }, Modifier.fillMaxWidth(), label = "已有备份集路径或远端根名称") }
            }
            ConfigGroup.TARGET -> {
                if (spec.selectedApps.isNotEmpty()) item { InfoCard("已选应用", "${spec.selectedApps.size} 个应用\n来源用户：${spec.selectedApps.map { it.userId }.distinct().joinToString()}") }
                else {
                    item { SectionLabel(if (spec.command.restore) "目标用户" else "来源用户") }
                    item { Card(Modifier.fillMaxWidth()) {
                        s.capabilities?.users.orEmpty().forEach { user -> RadioButtonPreference("${user.name} · ${user.id}", user.id == spec.userId,
                            { update { it.copy(userId = user.id) }; vm.changeDraft { d -> d.copy(items = d.items.map { it.copy(app = it.app.copy(userId = user.id)) }) } }, enabled = user.unlocked) }
                    } }
                    item { TextField(spec.selectedPackages.joinToString("\n"), { text -> update { it.copy(selectedPackages = text.lines().flatMap { it.split(',') }.map(String::trim).filter(String::isNotEmpty).distinct()) } }, Modifier.fillMaxWidth(), label = "限定包名（可留空，执行时选择）") }
                    item { Card(Modifier.fillMaxWidth()) { SwitchPreference(spec.preserveExistingList, { checked -> update { it.copy(preserveExistingList = checked) } }, "采用已有清单", summary = "保留现有清单中的选择和标记。") } }
                }
                if (spec.command.restore || spec.command.group() == ToolGroup.MAINTENANCE || spec.command == ScriptCommand.UPLOAD_EXISTING) {
                    item { SectionLabel("备份来源") }
                    item { TextField(spec.workingDirectory, { value -> update { it.copy(workingDirectory = value) }; vm.changeDraft { d -> d.copy(items = d.items.map { it.copy(scriptRoot = value) }) } }, Modifier.fillMaxWidth(), label = "本地原生备份根目录") }
                }
            }
            ConfigGroup.SAFETY -> {
                item { Card(Modifier.fillMaxWidth()) {
                    SwitchPreference(draft.safetyCopy, { checked -> vm.changeDraft { it.copy(safetyCopy = checked) } }, "恢复前保存副本", summary = "副本保存成功后开始恢复。")
                    if (draft.safetyCopy) ArrowPreference("保存位置", summary = s.destinations.find { it.id == draft.safetyDestinationId }?.name ?: "请选择", onClick = { navigate(Route.ConfigLocation(true)) })
                } }
            }
            else -> {
                if (group == ConfigGroup.CONTENT && !spec.command.restore) item { Card(Modifier.fillMaxWidth()) {
                    SwitchPreference(spec.associatedWifi, { checked -> update { it.copy(associatedWifi = checked) } }, "关联 Wi-Fi 备份", summary = "与本次应用备份一起保存 Wi-Fi 配置。")
                } }
                val fields = s.configFields().filter { configGroup(it.key) == group }
                item { Card(Modifier.fillMaxWidth()) {
                    fields.forEach { field -> ArrowPreference(field.label, summary = fieldSummary(field, fieldValue(s, field)), onClick = { navigate(Route.ConfigField(field.key)) }) }
                } }
                if (fields.isEmpty()) item { InfoCard("暂无可用选项", "此操作没有对应配置，或尚未完成首页的运行检查。") }
            }
        }
    }
}

@Composable fun ConfigFieldPage(s: AppState, vm: AppViewModel, padding: PaddingValues, key: String, back: () -> Unit) {
    val field = s.configFields().find { it.key == key }
    if (field == null) { PageList(padding) { item { InfoCard("配置不可用", "返回并重新选择配置项。") } }; return }
    var value by rememberSaveable(key) { mutableStateOf(fieldValue(s, field)) }
    fun save(next: String) { vm.changeDraft { it.copy(script = requireNotNull(it.script).let { spec -> spec.copy(config = spec.config + (key to next)) }) }; back() }
    PageList(padding) {
        if (field.options.isNotEmpty()) item { Card(Modifier.fillMaxWidth()) {
            field.options.forEach { option -> RadioButtonPreference(option.getOrElse(1) { option[0] }, value == option[0], { save(option[0]) }) }
        } } else {
            item { TextField(value, { value = it }, Modifier.fillMaxWidth(), label = field.label,
                minLines = if (key in setOf("Custom_path", "blacklist", "whitelist", "system")) 4 else 1) }
            item { PrimaryAction("保存", !s.busy) { save(value) } }
        }
    }
}
