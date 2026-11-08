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

@Composable fun ConfirmPage(s: AppState, vm: AppViewModel, padding: PaddingValues, done: (String) -> Unit) {
    val plan = s.plan
    PageList(padding) {
        notices(s, vm)
        if (plan == null) item { InfoCard("计划已失效", "返回设置重新检查；不会自动重放上次操作。") } else {
            item { InfoCard(if (plan.blockers.isEmpty()) "请确认操作范围" else "目前无法开始", "${plan.draft.script?.command?.label ?: plan.draft.operation.label()} · 检查于 ${date(plan.checkedAt)}") }
            items(plan.blockers) { InfoCard("阻断项", friendlyError(it)) }
            items(plan.warnings) { InfoCard("执行说明", it) }
            items(plan.estimates) { InfoCard("容量检查", it) }
            items(plan.draft.items, key = { it.record?.recordId ?: it.app.stableId }) { item ->
                val selected = plan.draft.script?.selectedApps.orEmpty().filter { it.userId == item.app.userId }
                val title = if (item.group) "用户 ${item.app.userId} · ${selected.size} 个应用" else
                    s.apps.find { it.key == item.app }?.label ?: plan.draft.script?.command?.label ?: item.app.packageName
                val names = if (item.group) selected.joinToString("、") { key -> s.apps.find { it.key == key }?.label ?: key.packageName } else ""
                InfoCard(title, "$names\n${if (plan.draft.operation == Operation.BACKUP) "来源" else "目标"}用户 ${item.app.userId}\n${item.parts.joinToString { it.label() }}".trim())
            }
            item { PrimaryAction(if (s.pendingSubmission) "正在确认是否已启动" else if (plan.draft.operation == Operation.DELETE) "确认永久删除" else "开始${plan.draft.operation.label()}",
                !s.busy && !s.pendingSubmission && plan.blockers.isEmpty() && s.tasks.none { it.state in setOf(TaskState.RUNNING, TaskState.STOP_REQUESTED, TaskState.NEEDS_RECONCILIATION) }) { vm.submit(done) } }
            if (s.pendingSubmission) item { TextButton("查询原任务状态", vm::refreshTasks, enabled = !s.busy) }
        }
    }
}
