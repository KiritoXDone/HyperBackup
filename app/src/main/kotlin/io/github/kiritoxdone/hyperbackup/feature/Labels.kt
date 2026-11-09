package io.github.kiritoxdone.hyperbackup.feature

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.kiritoxdone.hyperbackup.*
import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.ui.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

fun blacklistPackages(raw: String) = raw.lines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.distinct()

@Composable fun LabelsPage(s: AppState, vm: AppViewModel, padding: PaddingValues, navigate: (Route) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<String?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importLabels) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportLabels) }
    val defaults = s.scriptCatalog?.let { ScriptPreferences.effective(it, s.enginePreferences, ScriptProfile.BACKUP) }.orEmpty()
    val raw = defaults["blacklist"].orEmpty()
    val blocked = blacklistPackages(raw)
    PageList(padding, spacing = 0.dp) {
        item { Segments(listOf("标签", "黑名单"), tab, { tab = it }) }
        item { Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (tab == 0) "我的标签" else "黑名单", Modifier.weight(1f), style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            ActionIcon(MiuixIcons.Add, if (tab == 0) "添加标签" else "添加黑名单", !s.busy) { value = ""; adding = true }
        } }
        if (tab == 0) {
            val labels = s.behavior.labels.toList()
            if (labels.isEmpty()) item { EmptyContent(HyperIcons.Tag, "暂无标签") }
            itemsIndexed(labels, key = { _, pair -> pair.first }) { index, (name, members) ->
                GroupRow(index == 0, index == labels.lastIndex, { navigate(Route.LabelApps(name)) }) {
                    IconTile(HyperIcons.Tag)
                    Text(name, Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text("${members.size}", style = MiuixTheme.textStyles.body2)
                    ActionIcon(MiuixIcons.Delete, "删除标签 $name") { removing = name }
                }
            }
            item { Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton("导入标签", { importer.launch(arrayOf("application/json")) }, Modifier.weight(1f))
                TextButton("导出标签", { exporter.launch("HyperBackup-labels.json") }, Modifier.weight(1f))
            } }
        } else {
            item { SurfaceCard(Modifier.padding(bottom = 12.dp)) { ArrowPreference("黑名单处理", summary = s.scriptCatalog?.fields?.find { it.key == "blacklist_mode" }?.let {
                fieldSummary(it, defaults["blacklist_mode"].orEmpty()) }, onClick = { navigate(Route.DefaultField("blacklist_mode", null)) }) } }
            if (blocked.isEmpty()) item { EmptyContent(HyperIcons.Tag, "黑名单为空") }
            itemsIndexed(blocked, key = { _, pkg -> pkg }) { index, pkg -> GroupRow(index == 0, index == blocked.lastIndex, null) {
                val app = s.apps.find { it.key.packageName == pkg }
                if (app != null) AppIcon(app) else IconTile(HyperIcons.Backup)
                Column(Modifier.weight(1f)) {
                    Text(app?.label ?: pkg, fontWeight = FontWeight.Medium)
                    if (app != null) Text(pkg, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                ActionIcon(MiuixIcons.Delete, "移除 $pkg", !s.busy) {
                    vm.saveDefault(null, "blacklist", raw.lines().filter { it.substringBefore('#').trim() != pkg }.joinToString("\n"))
                }
            } }
        }
    }
    WindowDialog(show = adding, title = if (tab == 0) "添加标签" else "添加黑名单", onDismissRequest = { adding = false }) {
        TextField(value, { value = it }, Modifier.fillMaxWidth(), label = if (tab == 0) "标签名称" else "应用包名", singleLine = true)
        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("取消", { adding = false }, Modifier.weight(1f))
            TextButton("添加", {
                if (tab == 0) vm.addLabel(value) else runCatching { Wire.packageName(value.trim()) }.onSuccess {
                    vm.saveDefault(null, "blacklist", (raw.trimEnd() + "\n" + value.trim()).trim())
                }.onFailure { vm.clearError("请输入有效包名") }
                adding = false
            }, Modifier.weight(1f), enabled = value.isNotBlank(), colors = ButtonDefaults.textButtonColorsPrimary())
        }
    }
    WindowDialog(show = removing != null, title = "删除标签", summary = removing, onDismissRequest = { removing = null }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("取消", { removing = null }, Modifier.weight(1f))
            TextButton("删除", { removing?.let(vm::removeLabel); removing = null }, Modifier.weight(1f))
        }
    }
}
