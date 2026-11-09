package io.github.kiritoxdone.hyperbackup.feature

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.kiritoxdone.hyperbackup.*
import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.ui.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun HomePage(s: AppState, vm: AppViewModel, padding: PaddingValues, navigate: (Route) -> Unit, library: () -> Unit) {
    PageList(padding) {
        item { HomeStatusCard(s, vm::connect, { navigate(Route.Users) }, { navigate(Route.Locations) }) }
        item { SectionLabel("开始") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StartCard("备份", "应用 · 文件", HyperIcons.Backup, true, Modifier.weight(1f)) { navigate(Route.SelectApps) }
            StartCard("恢复", "本地 · 云端", HyperIcons.Restore, false, Modifier.weight(1f), library)
        } }
        item { SectionLabel("最近任务") }
        item {
            val task = s.tasks.firstOrNull()
            if (task == null) SurfaceCard(insideMargin = PaddingValues(16.dp)) {
                Text("还没有任务", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            } else SurfaceCard(onClick = { navigate(Route.Task(task.id)) }, insideMargin = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(taskTitle(task), Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    StatusBadge(task.state.label(), task.state in setOf(TaskState.FAILED, TaskState.PARTIAL, TaskState.NEEDS_RECONCILIATION))
                }
                Text(date(task.startedAt), Modifier.padding(top = 4.dp), style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric(task.items.count { it.outcome == Outcome.SUCCEEDED }.toString(), "完成项目", Modifier.weight(1f))
                    Metric(task.items.count { it.outcome in setOf(Outcome.FAILED, Outcome.PARTIAL, Outcome.UNKNOWN) }.toString(), "异常项目", Modifier.weight(1f))
                    Metric(taskDuration(task), "用时", Modifier.weight(1f))
                }
            }
        }
        s.storage?.let { disk -> item { SurfaceCard(insideMargin = PaddingValues(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("本机存储", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Text("${size(disk.total - disk.available)} / ${size(disk.total)}", style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp), progress = (1f - disk.available.toFloat() / disk.total).coerceIn(0f, 1f), height = 8.dp)
        } } }
    }
}

@Composable private fun StartCard(title: String, summary: String, icon: ImageVector, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    SurfaceCard(modifier, onClick = onClick, insideMargin = PaddingValues(16.dp),
        colors = CardDefaults.defaultColors(color = if (primary) colors.primary else colors.surfaceContainer,
            contentColor = if (primary) colors.onPrimary else colors.onSurfaceContainer)) {
        IconTile(icon, color = colors.primary, background = if (primary) colors.onPrimary else colors.tertiaryContainer)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.SemiBold)
        Text(summary, Modifier.padding(top = 3.dp), style = MiuixTheme.textStyles.footnote1,
            color = if (primary) colors.onPrimary else colors.onSurfaceVariantSummary)
    }
}

fun taskTitle(task: TaskSnapshot) = task.plan.draft.script?.command?.label ?: task.plan.draft.operation.label()
fun taskDuration(task: TaskSnapshot): String {
    if (task.state == TaskState.NEEDS_RECONCILIATION && task.finishedAt == null) return "—"
    val seconds = (((task.finishedAt ?: System.currentTimeMillis()) - task.startedAt).coerceAtLeast(0) / 1000)
    return if (seconds >= 3600) "${seconds / 3600}时${seconds / 60 % 60}分" else if (seconds >= 60) "${seconds / 60}分${seconds % 60}秒" else "${seconds}秒"
}
