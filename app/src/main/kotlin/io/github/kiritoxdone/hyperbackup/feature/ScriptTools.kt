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

@Composable fun ScriptToolsPage(s: AppState, vm: AppViewModel, padding: PaddingValues, navigate: (Route) -> Unit) {
    PageList(padding) {
        notices(s, vm)
        item { InfoCard("原生引擎操作", "固定 backup_script v1117。业务流程由原引擎执行，必要选择与确认在任务页完成。") }
        items(ScriptCommand.entries, key = { it.wireName }) { command -> Card {
            ArrowPreference(command.label, summary = command.wireName, onClick = { vm.beginScript(command); if (vm.state.value.draft != null) navigate(Route.Config) })
        } }
    }
}

@Composable fun ScriptSetupPage(s: AppState, vm: AppViewModel, padding: PaddingValues, next: () -> Unit) {
    val draft = s.draft ?: return
    val spec = draft.script ?: return
    var expanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val defaults = if (spec.command.restore) s.scriptCatalog?.restoreDefaults.orEmpty() else s.scriptCatalog?.backupDefaults.orEmpty()
    fun update(change: (ScriptSpec) -> ScriptSpec) { vm.changeDraft { it.copy(script = change(requireNotNull(it.script))) } }
    val fields = s.scriptCatalog?.fields.orEmpty().filterNot { it.secret || it.key.endsWith("_user") || it.key.endsWith("_url") || it.key in setOf("remote_type", "user", "Output_path", "Backup_suffix", "list_location") }
        .filter { it.key in defaults || it.key in spec.config }
        .filter { it.key.contains(query, true) || it.label.contains(query, true) }
    PageList(padding) {
        notices(s, vm)
        item { InfoCard(spec.command.label, "${draft.items.size} 个执行单元 · 固定 ${Wire.UPSTREAM.take(12)}\n应用选择 ${spec.selectedPackages.size} 项。通信记录、Wi-Fi 和文件夹遵循下方源配置及引擎确认。") }
        item { DestinationPicker(s, draft.destinationId, "备份位置 / 远端连接") { id -> vm.changeDraft { it.copy(destinationId = id) } } }
        if (!spec.command.restore) item { Card {
            RadioButtonPreference("新建备份集（默认）", spec.setMode == BackupSetMode.NEW, { update { it.copy(setMode = BackupSetMode.NEW) } }, summary = "使用唯一后缀，保留已有集。")
            RadioButtonPreference("更新已有备份集", spec.setMode == BackupSetMode.UPDATE, { update { it.copy(setMode = BackupSetMode.UPDATE) } }, summary = "允许原引擎 fast-skip；更新不会保存修改前的内容版本。")
        } }
        if (spec.setMode == BackupSetMode.UPDATE) item { TextField(spec.existingSet, { value -> update { it.copy(existingSet = value) } }, Modifier.fillMaxWidth(), label = "已有 Backup_zstd_用户_后缀 路径或远端根名称") }
        if (spec.command.restore || spec.command in setOf(ScriptCommand.UPLOAD_EXISTING, ScriptCommand.STATISTICS, ScriptCommand.VERIFY_ARCHIVES, ScriptCommand.VERIFY_METADATA, ScriptCommand.REBUILD_METADATA, ScriptCommand.CONVERT_NAMES)) {
            item { TextField(spec.workingDirectory, { value -> update { it.copy(workingDirectory = value) }; vm.changeDraft { d -> d.copy(items = d.items.map { it.copy(scriptRoot = value) }) } }, Modifier.fillMaxWidth(), label = "原生备份根目录（本地）", enabled = !s.busy) }
            item { InfoCard("读取原脚本备份", "选择包含 appList.txt / app_details.json 的原生根目录。无需 App 的 record.json 或提交标记。远端模式可由原引擎扫描并选择。") }
        }
        if (spec.selectedPackages.isEmpty()) item { Card { SwitchPreference(spec.preserveExistingList, { checked -> update { it.copy(preserveExistingList = checked) } }, "采用该目录现有清单", summary = "只处理清单中的启用项；请核对注释、! 标记与目标用户。") } }
        item { InfoCard("目标 / 来源用户", "${spec.userId}；应用列表中的跨用户选择会分别执行。") }
        item { UserPicker(s, spec.userId) { user -> update { it.copy(userId = user) }; vm.changeDraft { d -> d.copy(items = d.items.map { it.copy(app = it.app.copy(userId = user)) }) } } }
        if (spec.command.restore) {
            item { Card { SwitchPreference(draft.safetyCopy, { value -> vm.changeDraft { it.copy(safetyCopy = value) } }, "应用恢复前副本", summary = "默认开启；保存未确认成功不覆盖。其他对象按源保护/合并语义处理。") } }
            if (draft.safetyCopy) item { DestinationPicker(s, draft.safetyDestinationId, "恢复前副本位置") { id -> vm.changeDraft { it.copy(safetyDestinationId = id) } } }
        }
        item { Card { SwitchPreference(expanded, { expanded = it }, "完整引擎配置", summary = "字段、值和默认值来自此 SHA 的设置模板。连接密码由位置管理单独保护。") } }
        if (expanded) {
            item { Search(query, { query = it }, "搜索配置") }
            items(fields, key = { it.key }) { field ->
                val value = spec.config[field.key] ?: defaults[field.key].orEmpty()
                Card(insideMargin = PaddingValues(16.dp)) {
                    Text(field.label)
                    Text(field.key)
                    if (field.options.isNotEmpty()) {
                        field.options.forEach { option -> RadioButtonPreference(option.getOrElse(1) { option[0] }, value == option[0], {
                            update { it.copy(config = it.config + (field.key to option[0])) }
                        }) }
                    } else TextField(value, { text -> update { it.copy(config = it.config + (field.key to text)) } }, Modifier.fillMaxWidth(), label = field.label)
                }
            }
        }
        item { PrimaryAction("预检并确认", !s.busy && s.capabilities != null) { vm.preflight(next) } }
    }
}

@Composable fun PromptCard(prompt: EnginePrompt, busy: Boolean, answer: (String) -> Unit) {
    var selected by rememberSaveable(prompt.id) { mutableStateOf(emptyList<Int>()) }
    var text by rememberSaveable(prompt.id) { mutableStateOf("") }
    Card(insideMargin = PaddingValues(16.dp)) {
        Text(prompt.title)
        if (prompt.mode == "input") {
            TextField(text, { text = it }, Modifier.fillMaxWidth(), label = "输入内容")
            TextButton("确认", { answer(text) }, enabled = !busy)
        } else {
            prompt.options.forEachIndexed { index, label ->
                if (prompt.mode == "multi") CheckboxPreference(title = label, checked = index + 1 in selected, onCheckedChange = { checked -> selected = if (checked) selected + (index + 1) else selected - (index + 1) })
                else TextButton(label, { answer((index + 1).toString()) }, enabled = !busy)
            }
            if (prompt.mode == "multi") TextButton("确认所选项", { answer(selected.joinToString(" ")) }, enabled = !busy && selected.isNotEmpty())
        }
        TextButton(prompt.cancel, { answer("0") }, enabled = !busy)
    }
}
