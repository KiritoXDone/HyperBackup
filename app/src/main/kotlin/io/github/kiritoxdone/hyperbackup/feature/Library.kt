package io.github.kiritoxdone.hyperbackup.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
    var files by rememberSaveable { mutableStateOf(false) }
    var folderSource by rememberSaveable { mutableStateOf("") }
    val destination = s.destinations.find { it.id == s.destinationId }
    val records = s.repository?.records.orEmpty().filter { it.app.label.contains(query, true) || it.app.key.packageName.contains(query, true) }
    val groups = records.groupBy { it.batchId }.toList()
    PageList(padding) {
        item { Segments(listOf("本地", "云端"), if (destination?.protocol == null || destination.protocol == Protocol.LOCAL) 0 else 1, { index ->
            if (index == 1) navigate(Route.Cloud) else s.destinations.firstOrNull { it.protocol == Protocol.LOCAL }?.let { vm.selectDestination(it.id) } ?: navigate(Route.AddLocation)
        }) }
        item { Segments(listOf("应用", "文件"), if (files) 1 else 0, { files = it == 1 }) }
        item { SurfaceCard { ArrowPreference(destination?.name ?: "选择备份位置", summary = destination?.address, onClick = { navigate(Route.Locations) }) } }
        if (files) {
            if (destination != null && destination.protocol != Protocol.LOCAL) item { SurfaceCard {
                listOf(ScriptCommand.REMOTE_RESTORE_STAGED, ScriptCommand.REMOTE_RESTORE_STREAM).forEach { command ->
                    ArrowPreference(command.label, onClick = {
                        vm.beginScript(command)
                        if (vm.state.value.error == null) navigate(Route.Config)
                    })
                }
            } } else {
                item { TextField(folderSource, { folderSource = it }, Modifier.fillMaxWidth(), label = "文件夹备份集目录") }
                item { PrimaryAction("恢复设置", !s.busy && folderSource.isNotBlank()) {
                vm.beginScript(ScriptCommand.RESTORE_FOLDERS)
                if (vm.state.value.error == null) {
                    vm.changeDraft { d -> d.copy(script = requireNotNull(d.script).copy(workingDirectory = folderSource.trim()),
                        items = d.items.map { it.copy(scriptRoot = folderSource.trim()) }) }
                    navigate(Route.Config)
                }
                } }
            }
        } else {
            item { Search(query, { query = it }, "搜索备份或应用") }
            item { SectionLabel(if (s.repository == null) "备份集" else "备份集 · ${groups.size}") }
            if (groups.isEmpty()) item { EmptyContent(HyperIcons.Restore, if (query.isNotEmpty()) "没有匹配的备份" else "暂无备份集") }
            items(groups, key = { it.first }) { (id, batch) -> BackupSetRow(batch) { navigate(Route.Batch(id)) } }
            s.repository?.let { repo -> item {
                Text("${if (s.cached) "上次扫描" else "扫描时间"} · ${date(repo.scannedAt)}", Modifier.padding(horizontal = 16.dp),
                    style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            } }
            if (s.repository?.rejected?.isNotEmpty() == true) item { Text("${s.repository.rejected.size} 项不可用", Modifier.padding(horizontal = 16.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
        }
    }
}
@Composable private fun BackupSetRow(records: List<BackupRecord>, onClick: () -> Unit) {
    val first = records.first()
    val suffix = first.scriptRoot?.substringAfterLast('/')?.replace(Regex("^Backup_(?:zstd|tar)_\\d+_?"), "").orEmpty()
    val title = if (first.purpose == "PRE_RESTORE") "恢复前副本" else suffix.takeUnless { it.isBlank() || it.startsWith("HB_") } ?: "应用备份"
    SurfaceCard(Modifier.fillMaxWidth(), onClick = onClick, showIndication = true, pressFeedbackType = PressFeedbackType.Sink) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Icon(HyperIcons.Chevron, null, Modifier.size(20.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantActions)
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
    PageList(padding, spacing = 0.dp) {
        item { Text("已选 ${records.count { it.recordId in s.selectedRecords }} / ${records.size}", Modifier.padding(start = 16.dp, bottom = 12.dp),
            style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
        itemsIndexed(records, key = { _, it -> it.recordId }) { index, record ->
            GroupRow(index == 0, index == records.lastIndex, { vm.toggleRecord(record.recordId) }) {
                AppIcon(record.app)
                Column(Modifier.weight(1f)) {
                    Text(record.app.label, fontWeight = FontWeight.Medium)
                    Text(date(record.createdAt), style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    TextButton("版本详情", { navigate(Route.Record(record.recordId)) })
                }
                Checkbox(androidx.compose.ui.state.ToggleableState(record.recordId in s.selectedRecords), { vm.toggleRecord(record.recordId) })
            }
        }
        item { Box(Modifier.padding(top = 16.dp)) { PrimaryAction("恢复设置", records.any { it.recordId in s.selectedRecords } && !s.busy) {
            vm.beginRecords(records.filter { it.recordId in s.selectedRecords }, Operation.RESTORE); navigate(Route.Config)
        } } }
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
            item { SurfaceCard {
                ArrowPreference("下载并校验归档", onClick = { vm.beginRecords(listOf(record), Operation.VERIFY); navigate(Route.Config) })
                ArrowPreference("备份集维护", summary = "使用原引擎检查、修复和清理", onClick = { navigate(Route.Tools) })
            } }
        }
    }
}
