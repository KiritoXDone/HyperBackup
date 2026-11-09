package io.github.kiritoxdone.hyperbackup.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Backup
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import androidx.compose.ui.unit.dp
import io.github.kiritoxdone.hyperbackup.*
import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.ui.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun LibraryPage(s: AppState, vm: AppViewModel, padding: PaddingValues, navigate: (Route) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val destination = s.destinations.find { it.id == s.destinationId }
    val records = s.repository?.records.orEmpty().filter { it.app.label.contains(query, true) || it.app.key.packageName.contains(query, true) }
    val groups = records.groupBy { it.batchId }.toList()
    PageList(padding) {
        item { LibraryLocationCard(s, vm, navigate) }
        item { Search(query, { query = it }, "搜索备份或应用") }
        item { Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (s.repository == null) "备份集" else "备份集 · ${groups.size}", Modifier.weight(1f), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            ActionIcon(MiuixIcons.Refresh, "刷新备份库", !s.busy && destination != null, vm::scan)
        } }
        if (groups.isEmpty()) item { EmptyContent(MiuixIcons.Backup, if (query.isNotEmpty()) "没有匹配的备份" else "暂无备份集") }
        items(groups, key = { it.first }) { (id, batch) -> BackupSetRow(batch) { navigate(Route.Batch(id)) } }
        s.repository?.let { repo -> item {
            Text("${if (s.cached) "上次扫描" else "扫描时间"} · ${date(repo.scannedAt)}", Modifier.padding(horizontal = 16.dp),
                style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        } }
        if (s.repository?.rejected?.isNotEmpty() == true) item { Text("${s.repository.rejected.size} 项不可用", Modifier.padding(horizontal = 16.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
    }
}
@Composable private fun BackupSetRow(records: List<BackupRecord>, onClick: () -> Unit) {
    val first = records.first()
    val suffix = first.scriptRoot?.substringAfterLast('/')?.replace(Regex("^Backup_(?:zstd|tar)_\\d+_?"), "").orEmpty()
    val title = if (first.purpose == "PRE_RESTORE") "恢复前副本" else suffix.takeUnless { it.isBlank() || it.startsWith("HB_") } ?: "应用备份"
    Card(Modifier.fillMaxWidth(), onClick = onClick, showIndication = true, pressFeedbackType = PressFeedbackType.Sink) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MiuixTheme.textStyles.title3, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("›", Modifier.padding(start = 12.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                records.take(3).forEach { record -> Box(Modifier.size(32.dp)) { AppIcon(record.app, Modifier.size(32.dp)) } }
                Text("${records.size} 个应用", Modifier.padding(start = 4.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            val users = records.map { it.origin.userId }.distinct()
            Text((if (users.all { it >= 0 }) users.joinToString { "用户 $it" } else "来源用户未知") +
                if (first.createdAt > 0) " · ${date(first.createdAt)}" else " · ${first.batchId.take(8)}",
                style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}
@Composable fun BatchPage(s: AppState, vm: AppViewModel, padding: PaddingValues, id: String, navigate: (Route) -> Unit) {
    val records = s.repository?.records.orEmpty().filter { it.batchId == id }
    PageList(padding) {
        notices(s, vm)
        item { Text("${records.size} 个应用", Modifier.padding(horizontal = 16.dp)) }
        items(records, key = { it.recordId }) { record -> Card {
            Row(Modifier.padding(16.dp)) {
                Checkbox(androidx.compose.ui.state.ToggleableState(record.recordId in s.selectedRecords), { vm.toggleRecord(record.recordId) })
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(record.app.label); Text("来源用户 ${record.origin.userId} · ${if (record.scriptRoot != null) "已识别，尚未完整校验" else record.completion.label()}", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    TextButton("版本详情", { navigate(Route.Record(record.recordId)) })
                }
            }
        } }
        item { PrimaryAction("选择恢复内容", records.any { it.recordId in s.selectedRecords } && !s.busy) {
            vm.beginRecords(records.filter { it.recordId in s.selectedRecords }, Operation.RESTORE); navigate(Route.Config)
        } }
    }
}
@Composable fun RecordPage(s: AppState, vm: AppViewModel, padding: PaddingValues, id: String, navigate: (Route) -> Unit) {
    val record = s.repository?.records?.find { it.recordId == id }
    PageList(padding) {
        notices(s, vm)
        if (record == null) item { InfoCard("备份未找到", "请重新扫描备份位置") } else {
            item { InfoCard(record.app.label, "${record.app.key.packageName}\n${date(record.createdAt)} · ${if (record.scriptRoot != null) "已识别，尚未完整校验" else record.completion.label()}\n来源 ${record.origin.label} / 用户 ${record.origin.userId}\n安装版本 ${record.app.versionCode ?: "未知"}") }
            items(record.parts, key = { it.kind }) { part -> InfoCard(part.kind.label(), "${part.status} · ${if (part.bytes == 0L && part.status == PartStatus.INCLUDED) "大小未索引" else size(part.bytes)}\n${part.reason}\n${part.sha256?.let { "SHA-256 $it" }.orEmpty()}") }
            item { PrimaryAction("选择此版本恢复", !s.busy) { vm.beginRecords(listOf(record), Operation.RESTORE); navigate(Route.Config) } }
            item { Card {
                ArrowPreference("下载并校验归档", onClick = { vm.beginRecords(listOf(record), Operation.VERIFY); navigate(Route.Config) })
                ArrowPreference("备份集维护", summary = "使用原引擎检查、修复和清理", onClick = { navigate(Route.Tools) })
            } }
        }
    }
}
