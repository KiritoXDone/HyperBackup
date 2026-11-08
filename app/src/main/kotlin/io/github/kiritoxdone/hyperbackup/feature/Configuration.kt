package io.github.kiritoxdone.hyperbackup.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kiritoxdone.hyperbackup.*
import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.ui.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun ConfigPage(s: AppState, vm: AppViewModel, padding: PaddingValues, next: () -> Unit) {
    if (s.draft?.script != null) { ScriptSetupPage(s, vm, padding, next); return }
    val draft = s.draft
    PageList(padding) {
        notices(s, vm)
        if (draft == null) item { InfoCard("还没有选择内容", "返回应用或备份库，选择本次要处理的内容。") } else {
            item { InfoCard("${draft.operation.label()} · ${draft.items.size} 个应用", if (draft.operation == Operation.DELETE) "将永久删除这些备份版本。删除账本会保留；释放空间不包含其他版本。" else "本次选择会在预检时冻结；开始后不能更改。") }
            item { DestinationPicker(s, draft.destinationId, "备份位置") { id -> vm.changeDraft { it.copy(destinationId = id, safetyDestinationId = if (it.safetyDestinationId == it.destinationId) id else it.safetyDestinationId) } } }
            if (draft.operation == Operation.BACKUP) {
                item { Card {
                    PartKind.entries.filter { it != PartKind.APP_STATE }.forEach { part ->
                        SwitchPreference(draft.items.all { part in it.parts }, { checked -> vm.changeDraft { d -> d.copy(items = d.items.map { item -> item.copy(parts = if (checked) item.parts + part else item.parts - part) }) } },
                            part.label(), enabled = !s.busy)
                    }
                } }
                item { Card {
                    SwitchPreference(draft.ignoreRunning, { checked -> vm.changeDraft { it.copy(ignoreRunning = checked) } }, "跳过运行中的应用", summary = "默认关闭；沿用引擎自动保护与停止策略。", enabled = !s.busy)
                    SwitchPreference(draft.blacklistSkip, { checked -> vm.changeDraft { it.copy(blacklistSkip = checked) } }, "黑名单整应用跳过", summary = "关闭时黑名单仅保存 APK。默认黑名单为空。", enabled = !s.busy)
                    TextField(draft.blacklist.joinToString(","), { text -> vm.changeDraft { it.copy(blacklist = text.split(',').map(String::trim).filter(String::isNotEmpty).toSet()) } }, label = "黑名单包名，逗号分隔", modifier = Modifier.padding(12.dp), enabled = !s.busy)
                } }
                item { InfoCard("归档设置", "Zstandard · 级别 3 · 每次独立备份。应用状态尽力记录。私有目录排除 .ota、cache、lib、code_cache、no_backup；外部目录另排除 Backup_*、cache、QQ、Telegram。") }
            }
            items(draft.items, key = { it.record?.recordId ?: it.app.stableId }) { item ->
                val record = s.repository?.records?.find { it.recordId == item.record?.recordId }
                Card(insideMargin = PaddingValues(16.dp)) {
                    Text(record?.app?.label ?: item.app.packageName, style = MiuixTheme.textStyles.title3)
                    Text(if (record != null) "${record.origin.label} · 来源用户 ${record.origin.userId}" else "来源用户 ${item.app.userId}")
                    if (draft.operation == Operation.RESTORE) {
                        Text("恢复到目标用户", Modifier.padding(top = 12.dp))
                        UserPicker(s, item.app.userId) { user -> vm.changeDraft { d -> d.copy(items = d.items.map { if (it == item) it.copy(app = it.app.copy(userId = user)) else it }) } }
                    }
                    if (record != null && draft.operation in setOf(Operation.RESTORE, Operation.VERIFY)) {
                        record.parts.filter { it.kind != PartKind.APP_STATE && it.status in setOf(PartStatus.INCLUDED, PartStatus.EMPTY) }.forEach { part ->
                            CheckboxPreference(checked = part.kind in item.parts, onCheckedChange = { checked -> vm.changeDraft { d -> d.copy(items = d.items.map {
                                if (it == item) it.copy(parts = if (checked) it.parts + part.kind else it.parts - part.kind) else it
                            }) } }, title = "${part.kind.label()} · ${size(part.bytes)}", enabled = !s.busy)
                        }
                    }
                }
            }
            if (draft.operation == Operation.RESTORE) {
                item { Card {
                    SwitchPreference(draft.safetyCopy, { checked -> vm.changeDraft { it.copy(safetyCopy = checked) } }, "恢复前备份", summary = "默认开启。副本确认提交后才覆盖；用于手动恢复，不自动回滚。", enabled = !s.busy)
                } }
                if (draft.safetyCopy) item { DestinationPicker(s, draft.safetyDestinationId, "恢复前副本保存位置") { id -> vm.changeDraft { it.copy(safetyDestinationId = id) } } }
                item { Card {
                    SwitchPreference(draft.restoreAppOps, { checked -> vm.changeDraft { d -> d.copy(restoreSsaid = checked, restorePermissions = checked, restoreAppOps = checked,
                        items = d.items.map { item -> item.copy(parts = if (checked) item.parts + PartKind.APP_STATE else item.parts - PartKind.APP_STATE) }) } }, "高级：应用状态恢复", summary = "默认关闭。统一恢复 SSAID、运行时权限与 AppOps；可能受 ROM 和目标用户限制。仅备份中已记录的状态可用。", enabled = !s.busy)
                } }
                item { InfoCard("覆盖前请核对", "恢复旧数据到较新版本可能不兼容。APK 更新明确失败时，若现有安装有效，引擎仍可能恢复数据；安装提交结果未知时整批停止。") }
            }
            item { PrimaryAction("检查并继续", !s.busy && s.capabilities != null && draft.items.all { it.parts.isNotEmpty() }) { vm.preflight(next) } }
        }
    }
}

@Composable fun DestinationPicker(s: AppState, selected: String, title: String, onChange: (String) -> Unit) {
    Card(insideMargin = PaddingValues(16.dp)) {
        Text(title, style = MiuixTheme.textStyles.title3)
        s.destinations.forEach { destination ->
            RadioButtonPreference(title = destination.name, summary = "${destination.protocol} · ${destination.address}", selected = destination.id == selected,
                onClick = { onChange(destination.id) }, enabled = !s.busy)
        }
        if (s.destinations.isEmpty()) Text("请在设置中添加位置。")
    }
}
@Composable fun ConfirmPage(s: AppState, vm: AppViewModel, padding: PaddingValues, done: (String) -> Unit) {
    val plan = s.plan
    PageList(padding) {
        notices(s, vm)
        if (plan == null) item { InfoCard("计划已失效", "返回设置重新检查；不会自动重放上次操作。") } else {
            item { StatusCard(if (plan.blockers.isEmpty()) "请确认${plan.draft.operation.label()}范围" else "目前无法开始", "${plan.draft.items.size} 项 · 检查于 ${date(plan.checkedAt)}", warning = plan.blockers.isNotEmpty() || plan.draft.operation in setOf(Operation.RESTORE, Operation.DELETE)) }
            items(plan.blockers) { InfoCard("阻断项", friendlyError(it)) }
            items(plan.warnings) { InfoCard("执行说明", it) }
            items(plan.estimates) { InfoCard("容量检查", it) }
            items(plan.draft.items, key = { it.record?.recordId ?: it.app.stableId }) { item ->
                InfoCard(item.app.packageName, "${if (plan.draft.operation == Operation.BACKUP) "来源" else "目标"}用户 ${item.app.userId}\n${item.parts.joinToString { it.label() }}")
            }
            item { PrimaryAction(if (s.pendingSubmission) "正在确认是否已启动" else if (plan.draft.operation == Operation.DELETE) "确认永久删除" else "开始${plan.draft.operation.label()}",
                !s.busy && !s.pendingSubmission && plan.blockers.isEmpty() && s.tasks.none { it.state in setOf(TaskState.RUNNING, TaskState.STOP_REQUESTED, TaskState.NEEDS_RECONCILIATION) }) { vm.submit(done) } }
            if (s.pendingSubmission) item { TextButton("查询原任务状态", vm::refreshTasks, enabled = !s.busy) }
        }
    }
}
