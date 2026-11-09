package io.github.kiritoxdone.hyperbackup.feature

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
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
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val tasks = s.tasks.filter { filter == 0 || (if (filter == 1) it.plan.draft.script?.command?.wireName?.startsWith("backup_") ?: (it.plan.draft.operation == Operation.BACKUP) else it.plan.draft.operation == Operation.RESTORE) }
    val groups = tasks.groupBy { java.time.Instant.ofEpochMilli(it.startedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
    PageList(padding, spacing = 0.dp) {
        item { Segments(listOf("全部", "备份", "恢复"), filter, { filter = it }) }
        if (tasks.isEmpty()) item { EmptyContent(HyperIcons.History, "还没有任务") }
        groups.forEach { (day, records) ->
            item { Box(Modifier.padding(top = 12.dp, bottom = 8.dp)) { SectionLabel(if (day == java.time.LocalDate.now()) "今天" else day.toString()) } }
            itemsIndexed(records, key = { _, it -> it.id }) { index, task ->
                GroupRow(index == 0, index == records.lastIndex, { navigate(Route.Task(task.id)) }) {
                    IconTile(if (task.plan.draft.operation == Operation.RESTORE) HyperIcons.Restore else HyperIcons.Backup)
                    Column(Modifier.weight(1f)) {
                        Text(taskTitle(task), fontWeight = FontWeight.Medium)
                        Text("${date(task.startedAt)} · ${taskDuration(task)}", Modifier.padding(top = 3.dp), style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    StatusBadge(task.state.label(), task.state in setOf(TaskState.FAILED, TaskState.PARTIAL, TaskState.NEEDS_RECONCILIATION), task.state == TaskState.SUCCEEDED)
                }
            }
        }
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
            item { TaskOverview(task) }
            if (task.error.isNotEmpty()) item { InfoCard("任务说明", friendlyError(task.error)) }
            task.editor?.let { editor -> item { PrimaryAction("打开引擎选择与账号映射", !s.busy) { navigate(Route.Editor(editor.taskId)) } } }
            task.prompt?.let { prompt -> item { PromptCard(prompt, s.busy) { value -> vm.answer(prompt, value) } } }
            item { InfoCard("分项计数", Outcome.entries.mapNotNull { status -> task.items.count { it.outcome == status }.takeIf { it > 0 }?.let { "${status.label()} $it" } }.joinToString(" · ")) }
            item { SectionLabel(if (task.state in setOf(TaskState.RUNNING, TaskState.STOP_REQUESTED)) "任务队列" else "处理结果") }
            items(task.items, key = { it.app.stableId }) { result ->
                SurfaceCard(insideMargin = PaddingValues(16.dp)) {
                    val app = s.apps.find { it.key == result.app }
                    val group = task.plan.draft.items.find { it.app == result.app }?.group == true
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (app != null && !group) AppIcon(app) else IconTile(if (group) HyperIcons.User else HyperIcons.Backup)
                        Column(Modifier.weight(1f)) {
                            Text(if (group) "用户 ${result.app.userId}" else app?.label ?: taskTitle(task), fontWeight = FontWeight.Medium)
                            Text(if (result.outcome == Outcome.RUNNING) phaseLabel(result.phase) else result.outcome.label(),
                                Modifier.padding(top = 3.dp), style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                    }
                    if (result.reason.isNotBlank()) Text(result.reason, Modifier.padding(top = 12.dp), style = MiuixTheme.textStyles.body2)
                    result.parts.forEach { part -> BasicComponent(title = part.kind.label(), summary = "${part.outcome.label()} ${part.reason}".trim()) }
                    result.safetyRecord?.let { ref -> TextButton("恢复前副本", { navigate(Route.Record(ref.recordId)) }) }
                }
            }
            if (task.state in setOf(TaskState.RUNNING, TaskState.STOP_REQUESTED)) item {
                PrimaryAction(if (task.state == TaskState.STOP_REQUESTED) "正在等待收尾" else "停止后续任务",
                    task.state == TaskState.RUNNING && !s.busy) { confirmStop = true }
            }
            if (task.state == TaskState.NEEDS_RECONCILIATION) item { SurfaceCard {
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
            item { TextButton("导出诊断结果", { export.launch("HyperBackup-${task.id}.json") }) }
        }
    }
    WindowDialog(show = confirmStop, title = "停止后续任务", summary = "当前步骤收尾后停止，已写入内容保留。", onDismissRequest = { confirmStop = false }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("继续任务", { confirmStop = false }, Modifier.weight(1f))
            TextButton("停止", { vm.stop(id); confirmStop = false }, Modifier.weight(1f), enabled = !s.busy)
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
    phase == "PENDING" -> "等待中"
    else -> "执行中"
}

@Composable private fun TaskOverview(task: TaskSnapshot) {
    val finished = task.items.count { it.outcome !in setOf(Outcome.PENDING, Outcome.RUNNING, Outcome.UNKNOWN) }
    val active = task.state in setOf(TaskState.RUNNING, TaskState.STOP_REQUESTED)
    var elapsed by remember(task) { mutableStateOf(taskDuration(task)) }
    LaunchedEffect(task) { while (active) { kotlinx.coroutines.delay(1000); elapsed = taskDuration(task) } }
    SurfaceCard(insideMargin = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(taskTitle(task), Modifier.weight(1f), style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.SemiBold)
            Text("$finished / ${task.items.size} 项", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        if (active) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp),
            progress = task.items.takeIf { it.isNotEmpty() }?.let { finished.toFloat() / it.size }, height = 8.dp)
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric(task.items.count { it.outcome == Outcome.SUCCEEDED }.toString(), "已完成", Modifier.weight(1f))
            Metric(task.items.count { it.outcome in setOf(Outcome.FAILED, Outcome.PARTIAL, Outcome.UNKNOWN) }.toString(), "异常", Modifier.weight(1f))
            Metric(elapsed, "用时", Modifier.weight(1f))
        }
    }
}
