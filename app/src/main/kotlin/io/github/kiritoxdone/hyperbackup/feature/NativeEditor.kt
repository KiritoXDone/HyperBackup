package io.github.kiritoxdone.hyperbackup.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.kiritoxdone.hyperbackup.*
import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.ui.*
import kotlinx.serialization.json.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.*

@Composable fun NativeEditorPage(s: AppState, vm: AppViewModel, padding: PaddingValues, taskId: String, back: () -> Unit) {
    val editor = s.tasks.find { it.id == taskId }?.editor
    if (editor == null) { PageList(padding) { item { InfoCard("选择会话已结束", "返回任务页查看实际执行状态。"); TextButton("返回任务", back) } }; return }
    var selected by rememberSaveable(editor.id) { mutableStateOf(editor.rows.filter { it.selected }.map { it.id }) }
    var apkOnly by rememberSaveable(editor.id) { mutableStateOf(editor.rows.filter { it.apk }.map { it.id }) }
    var query by rememberSaveable(editor.id) { mutableStateOf("") }
    var timelineMode by rememberSaveable(editor.id) { mutableStateOf("match") }
    var target by rememberSaveable(editor.id) { mutableStateOf("") }
    var sources by rememberSaveable(editor.id) { mutableStateOf(emptyList<String>()) }
    var credentials by remember(editor.id) { mutableStateOf(editor.fields.associate { it.key to it.value }) }
    val timeline = remember(editor.timeline) { runCatching { Json.parseToJsonElement(editor.timeline.orEmpty()).jsonObject }.getOrNull() }
    val sourceRows = timeline?.get("sources")?.jsonArray.orEmpty().map { it.jsonObject }
    val targets = timeline?.get("targets")?.jsonArray.orEmpty().map { it.jsonObject }
    val mapsSelected = editor.rows.any { (it.packageName == "com.google.android.apps.maps" || it.id == "com.google.android.apps.maps") && it.id in selected && it.id !in apkOnly }
    val visible = editor.rows.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
    PageList(padding) {
        notices(s, vm)
        item { InfoCard(if (editor.authSetup) "重新填写连接凭据" else "引擎选择确认", "选择与保存由固定版原生接口校验。此页面不启动或安装原 SpeedBackup。") }
        if (editor.authSetup) {
            items(editor.fields, key = { it.key }) { field ->
                TextField(credentials[field.key].orEmpty(), { text -> credentials = credentials + (field.key to text) }, Modifier.fillMaxWidth(),
                    label = if (field.key.endsWith("_pass")) "连接密码" else "连接用户名", singleLine = true,
                    visualTransformation = if (field.key.endsWith("_pass")) PasswordVisualTransformation() else VisualTransformation.None)
            }
            item { PrimaryAction("确认凭据并继续", !s.busy) {
                val body = Wire.json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ScriptFormValue.serializer()), credentials.map { ScriptFormValue(it.key, it.value) })
                vm.answerEditor(editor, if (editor.restoreConfig) "auth-restore" else "auth-backup", body, back)
            } }
        } else {
            item { Search(query, { query = it }, "搜索名单") }
            item { Row {
                TextButton("全选筛选结果", { selected = (selected + visible.map { it.id }).distinct() }, enabled = !s.busy)
                TextButton("清空筛选结果", { selected = selected - visible.map { it.id }.toSet() }, enabled = !s.busy)
            } }
            items(visible, key = { it.id }) { row ->
                Card {
                    CheckboxPreference(title = row.label, summary = row.packageName, checked = row.id in selected,
                        onCheckedChange = { checked -> selected = if (checked) selected + row.id else selected - row.id }, enabled = !s.busy)
                    if (editor.mode in setOf("apps", "backup", "restore")) SwitchPreference(row.id in apkOnly,
                        { checked -> apkOnly = if (checked) apkOnly + row.id else apkOnly - row.id }, "仅 APK（!）", enabled = row.id in selected && !s.busy)
                }
            }
            if (mapsSelected && sourceRows.isNotEmpty()) {
                item { InfoCard("Maps Timeline 账号映射", "普通 Maps 应用数据与时间轴侧车分别处理。选择会绑定到当前备份和目标用户，原引擎在写入前重新验证账号。") }
                item { Card {
                    listOf("match" to "只恢复匹配的当前账号", "account" to "指定同一账号", "cross" to "一个来源映射到目标账号", "merge" to "多个来源合并到目标账号").forEach { (key, label) ->
                        RadioButtonPreference(label, timelineMode == key, { timelineMode = key; sources = emptyList(); target = "" })
                    }
                } }
                if (timelineMode != "match") {
                    items(targets, key = { "target-${it["id"]}" }) { account -> Card {
                        val id = account.getValue("id").jsonPrimitive.content
                        RadioButtonPreference("目标：${account.getValue("label").jsonPrimitive.content}", target == id, { target = id })
                    } }
                }
                if (timelineMode in setOf("cross", "merge")) {
                    items(sourceRows, key = { "source-${it["id"]}" }) { account -> Card {
                        val id = account.getValue("id").jsonPrimitive.content
                        CheckboxPreference(title = "来源：${account.getValue("label").jsonPrimitive.content}", checked = id in sources,
                            onCheckedChange = { checked -> sources = if (!checked) sources - id else if (timelineMode == "cross") listOf(id) else sources + id })
                    } }
                }
            }
            item { PrimaryAction("保存选择并继续 · ${selected.size} 项", !s.busy && selected.isNotEmpty()) {
                val body = editor.rows.joinToString("\n") { "${it.id}\t${if (it.id in selected) 1 else 0}\t${if (it.id in apkOnly && it.id in selected) 1 else 0}" } +
                    if (mapsSelected && sourceRows.isNotEmpty()) "\n@timeline\t$timelineMode\t$target\t${sources.joinToString(",")}" else ""
                vm.answerEditor(editor, "save", body, back)
            } }
        }
        item { TextButton("取消本次选择", { vm.answerEditor(editor, "cancel", done = back) }, enabled = !s.busy) }
    }
}
