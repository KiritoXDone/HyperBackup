package io.github.kiritoxdone.hyperbackup.feature

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.kiritoxdone.hyperbackup.*
import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.ArrowPreference

@Composable fun TasksPage(s: AppState, vm: AppViewModel, padding: PaddingValues, navigate: (Route) -> Unit) {
    PageList(padding) {
        notices(s, vm)
        item { PrimaryAction("查询并核对任务", !s.busy && s.capabilities != null, vm::refreshTasks) }
        if (s.tasks.isEmpty()) item { InfoCard("还没有任务", "任务接受后会持久记录。返回或关闭页面不会创建第二个作业。") }
        items(s.tasks, key = { it.id }) { task -> Card { ArrowPreference("${task.plan.draft.operation.label()} · ${task.state.label()}",
            summary = "${date(task.startedAt)} · ${task.items.size} 项", onClick = { navigate(Route.Task(task.id)) }) } }
    }
}
@Composable fun TaskPage(s: AppState, vm: AppViewModel, padding: PaddingValues, id: String, navigate: (Route) -> Unit) {
    val task = s.tasks.find { it.id == id }
    var showLog by rememberSaveable(id) { mutableStateOf(false) }
    var confirmStop by rememberSaveable(id) { mutableStateOf(false) }
    var confirmAck by rememberSaveable(id) { mutableStateOf(false) }
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && task != null) scope.launch(Dispatchers.IO) {
            runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(Wire.json.encodeToString(TaskSnapshot.serializer(), task).toByteArray()) } }
                .onFailure { vm.clearError("导出失败：${it.message}") }
        }
    }
    PageList(padding) {
        notices(s, vm)
        if (task == null) item { InfoCard("正在确认任务状态", "先连接 Root 并查询历史记录，保持原 taskId，不重新提交恢复。")
            TextButton("查询任务", vm::refreshTasks, enabled = !s.busy && s.capabilities != null) } else {
            item { InfoCard("${task.plan.draft.operation.label()}${task.state.label()}",
                "${date(task.startedAt)}\n项目进度 ${task.items.count { it.outcome !in setOf(Outcome.PENDING, Outcome.RUNNING) }}/${task.items.size}") }
            if (task.error.isNotEmpty()) item { InfoCard("任务说明", friendlyError(task.error)) }
            task.editor?.let { editor -> item { PrimaryAction("打开引擎选择与账号映射", !s.busy) { navigate(Route.Editor(editor.taskId)) } } }
            task.prompt?.let { prompt -> item { PromptCard(prompt, s.busy) { value -> vm.answer(prompt, value) } } }
            item { InfoCard("分项计数", Outcome.entries.mapNotNull { status -> task.items.count { it.outcome == status }.takeIf { it > 0 }?.let { "${status.label()} $it" } }.joinToString(" · ")) }
            items(task.items, key = { it.app.stableId }) { result ->
                Card {
                    InfoCard(result.app.packageName, "用户 ${result.app.userId} · ${result.outcome.label()}\n${phaseLabel(result.phase)}\n${result.reason}")
                    result.parts.forEach { part -> InfoCard(part.kind.label(), "${part.outcome.label()} · ${part.reason}${if (part.written) "\n该组成已发生或可能发生写入" else ""}") }
                    result.safetyRecord?.let { ref -> TextButton("查看恢复前副本", { navigate(Route.Record(ref.recordId)) }) }
                }
            }
            if (task.state in setOf(TaskState.RUNNING, TaskState.STOP_REQUESTED)) item {
                if (confirmStop) Card {
                    InfoCard("停止后续项目", "当前步骤会到安全点并收尾。已经写入的数据不会自动回滚。")
                    TextButton("请求停止", { vm.stop(id); confirmStop = false }, enabled = !s.busy)
                    TextButton("返回任务", { confirmStop = false })
                } else TextButton(if (task.state == TaskState.STOP_REQUESTED) "正在等待收尾" else "停止任务", { confirmStop = true }, enabled = task.state == TaskState.RUNNING && !s.busy)
            }
            if (task.state == TaskState.NEEDS_RECONCILIATION) item { Card {
                InfoCard("先核对，再开始新任务", "检查目标应用安装、数据与恢复前副本。未知结果不会自动重试；解除阻断也不会把未知项目改记为成功。")
                TextButton("核对安装与仓库现状", { vm.reconcile(id) }, enabled = !s.busy)
                if (confirmAck) TextButton("已核对，检查保护收尾并解除阻断", { vm.acknowledge(id); confirmAck = false }, enabled = !s.busy)
                else TextButton("我已完成手动核对", { confirmAck = true }, enabled = !s.busy)
            } }
            items(task.evidence) { evidence -> InfoCard("核对证据", evidence) }
            if (task.state !in setOf(TaskState.RUNNING, TaskState.STOP_REQUESTED, TaskState.NEEDS_RECONCILIATION) && task.items.any { it.outcome in setOf(Outcome.FAILED, Outcome.PARTIAL, Outcome.CANCELLED) }) item {
                PrimaryAction("重新检查未完成项", !s.busy) { vm.retry(task); navigate(Route.Config) }
            }
            item { TextButton(if (showLog) "收起阶段记录" else "查看阶段记录", { showLog = !showLog }) }
            if (showLog) items(task.events, key = { it.sequence }) { event -> InfoCard("#${event.sequence} · ${phaseLabel(event.phase)}", "${date(event.timestamp)} · 应用项目 ${event.item + 1}") }
            item { TextButton("导出诊断结果", { export.launch("hyperbackup-${task.id}.json") }) }
        }
    }
}
fun phaseLabel(phase: String) = when {
    phase.startsWith("BACKUP_") -> "正在备份 ${phase.removePrefix("BACKUP_")}"
    phase.startsWith("RESTORE_") -> "正在恢复 ${phase.removePrefix("RESTORE_")}"
    phase.startsWith("VERIFY") -> "正在校验"
    phase == "DOWNLOAD" -> "正在逐应用下载"
    phase == "SAFETY_BACKUP" -> "正在保存恢复前副本"
    phase.startsWith("INSTALL") -> "正在处理安装"
    phase == "COMMITTED" -> "备份已提交，记录归档摘要"
    phase == "PUBLISH" -> "正在发布清单"
    phase == "COMPLETE" -> "处理与收尾完成"
    else -> phase
}
