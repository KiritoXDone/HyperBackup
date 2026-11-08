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
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun LibraryPage(s: AppState, vm: AppViewModel, padding: PaddingValues, navigate: (Route) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val records = s.repository?.records.orEmpty().filter { it.app.label.contains(query, true) || it.app.key.packageName.contains(query, true) }
    val groups = records.groupBy { it.batchId }.toList()
    PageList(padding) {
        notices(s, vm, navigate)
        item { Card { ArrowPreference(s.destinations.find { it.id == s.destinationId }?.name ?: "添加备份位置", summary = s.destinations.find { it.id == s.destinationId }?.let { "${it.protocol} · ${it.address}" }, onClick = { navigate(Route.Locations) }) } }
        item { PrimaryAction("重新扫描仓库", !s.busy && s.capabilities != null && s.destinationId != null, vm::scan) }
        s.repository?.let { repo -> item { Text("${if (s.cached) "上次扫描结果（当前未核验在线状态）" else "清单已识别"} · ${date(repo.scannedAt)}\n${repo.records.size} 项；归档内容尚未逐项下载校验。", color = MiuixTheme.colorScheme.onSurfaceVariantSummary) } }
        item { Search(query, { query = it }, "搜索应用备份") }
        if (groups.isEmpty()) item { InfoCard("还没有备份清单", "选择位置并扫描。旧脚本与 DataBackup 格式不会自动导入。") }
        items(groups, key = { it.first }) { (id, batch) ->
            Card { ArrowPreference(if (batch.first().purpose == "PRE_RESTORE") "恢复前备份" else "应用备份", summary =
                "${if (batch.first().createdAt == 0L) "原生备份集" else date(batch.first().createdAt)}\n${batch.size} 个应用 · ${if (batch.all { it.scriptRoot != null }) "容量未索引" else size(batch.sumOf { r -> r.parts.sumOf { it.bytes } })}\n${batch.first().origin.label} · ${batch.map { it.origin.userId }.distinct().joinToString { "用户 $it" }}",
                onClick = { navigate(Route.Batch(id)) }) }
        }
        if (s.repository?.rejected?.isNotEmpty() == true) item { InfoCard("未提交或不可用条目", "${s.repository.rejected.size} 项未进入可恢复列表。扫描保留其证据，不自动清理。") }
    }
}
@Composable fun BatchPage(s: AppState, vm: AppViewModel, padding: PaddingValues, id: String, navigate: (Route) -> Unit) {
    val records = s.repository?.records.orEmpty().filter { it.batchId == id }
    PageList(padding) {
        notices(s, vm)
        item { InfoCard("${records.size} 项已识别", "选择恢复的应用版本。每个应用的来源用户会在恢复设置中显示，目标用户需要明确选择。") }
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
        if (record == null) item { InfoCard("请先连接原仓库", "当前索引中没有此版本。添加原备份位置并扫描，可重建清单。") } else {
            item { InfoCard(record.app.label, "${record.app.key.packageName}\n${date(record.createdAt)} · ${if (record.scriptRoot != null) "已识别，尚未完整校验" else record.completion.label()}\n来源 ${record.origin.label} / 用户 ${record.origin.userId}\n安装版本 ${record.app.versionCode ?: "未知"}") }
            items(record.parts, key = { it.kind }) { part -> InfoCard(part.kind.label(), "${part.status} · ${if (part.bytes == 0L && part.status == PartStatus.INCLUDED) "大小未索引" else size(part.bytes)}\n${part.reason}\n${part.sha256?.let { "SHA-256 $it" }.orEmpty()}") }
            item { PrimaryAction("选择此版本恢复", !s.busy) { vm.beginRecords(listOf(record), Operation.RESTORE); navigate(Route.Config) } }
            item { Card {
                ArrowPreference("下载并校验归档", summary = "核验所选内容的 SHA-256 与归档结构", onClick = { vm.beginRecords(listOf(record), Operation.VERIFY); navigate(Route.Config) })
                ArrowPreference("备份集维护", summary = "使用原引擎检查、修复和清理", onClick = { navigate(Route.Tools) })
            } }
        }
    }
}
