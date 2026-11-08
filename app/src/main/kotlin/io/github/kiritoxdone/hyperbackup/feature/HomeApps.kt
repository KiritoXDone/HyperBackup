package io.github.kiritoxdone.hyperbackup.feature

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.kiritoxdone.hyperbackup.*
import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun HomePage(s: AppState, vm: AppViewModel, padding: PaddingValues, navigate: (Route) -> Unit, library: () -> Unit) {
    PageList(padding) {
        item("connection") { HomeStatusCard(s, vm::connect) }
        notices(s, vm)
        item { SectionLabel("备份与恢复") }
        item { Card(Modifier.fillMaxWidth()) {
            ArrowPreference("备份应用", onClick = { navigate(Route.SelectApps) })
            ArrowPreference("备份文件夹", onClick = {
                vm.beginScript(ScriptCommand.BACKUP_FOLDERS)
                if (vm.state.value.error == null && vm.state.value.draft?.script?.command == ScriptCommand.BACKUP_FOLDERS) navigate(Route.Config)
            })
            ArrowPreference("恢复备份", onClick = library)
            ArrowPreference("更多功能", onClick = { navigate(Route.Tools) })
        } }
        item { SectionLabel("备份位置") }
        item { Card(Modifier.fillMaxWidth()) {
            val destination = s.destinations.find { it.id == s.destinationId }
            ArrowPreference(destination?.name ?: "选择备份位置", summary = destination?.let { "${it.protocol} · ${it.address}" },
                onClick = { navigate(Route.Locations) })
        } }
        item { SectionLabel("最近任务") }
        item { Card(Modifier.fillMaxWidth()) {
            val task = s.tasks.firstOrNull()
            ArrowPreference(task?.let { "${it.plan.draft.operation.label()} · ${it.state.label()}" } ?: "还没有任务",
                summary = task?.let { date(it.startedAt) }, onClick = { navigate(task?.let { Route.Task(it.id) } ?: Route.Tasks) })
        } }
    }
}

@Composable fun AppsPage(s: AppState, vm: AppViewModel, padding: PaddingValues, selection: Boolean, navigate: (Route) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var showSystem by rememberSaveable { mutableStateOf(false) }
    val filtered = remember(s.apps, query, showSystem) { s.apps.filter { !ProtectionPolicy.protected(it) && (showSystem || !it.system) && (it.label.contains(query, true) || it.key.packageName.contains(query, true)) }.sortedBy { it.label.lowercase() } }
    PageList(padding) {
        notices(s, vm, if (selection) null else navigate)
        item { Search(query, { query = it }, "搜索名称或包名") }
        item {
            val users = s.capabilities?.users.orEmpty()
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                OverlaySpinnerPreference(items = users.map { DropdownItem(it.name) }, selectedIndex = users.indexOfFirst { it.id == s.user }.coerceAtLeast(0),
                    title = users.find { it.id == s.user }?.name ?: "用户", showValue = false, modifier = Modifier.weight(1f),
                    insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp), enabled = users.isNotEmpty() && !s.busy,
                    onSelectedIndexChange = { vm.selectUser(users[it].id) })
                OverlaySpinnerPreference(items = listOf(DropdownItem("用户应用"), DropdownItem("含系统应用")), selectedIndex = if (showSystem) 1 else 0,
                    title = if (showSystem) "全部应用" else "用户应用", showValue = false, modifier = Modifier.weight(1.25f),
                    insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp), onSelectedIndexChange = { showSystem = it == 1 })
                InlineAction(if (selection) "全选" else "选择", enabled = !s.busy && filtered.isNotEmpty()) {
                    if (selection) vm.toggleAll(filtered.map { it.key }) else navigate(Route.SelectApps)
                }
            }
        }
        if (selection && s.selectedApps.isNotEmpty()) item { Text("已选 ${s.selectedApps.size} 项", Modifier.padding(horizontal = 16.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
        if (filtered.isEmpty()) item { EmptyContent(MiuixIcons.GridView, if (s.capabilities == null) "未读取应用" else if (query.isNotEmpty()) "没有匹配的应用" else "暂无应用") }
        items(filtered, key = { it.key.stableId }) { app ->
            Card(onClick = { if (selection) vm.toggleApp(app.key) else navigate(Route.AppDetail(app.key.userId, app.key.packageName)) }) {
                Row(Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(app)
                    Column(Modifier.weight(1f)) {
                        Text(app.label.ifEmpty { app.key.packageName }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(app.key.packageName + if (app.system) " · 系统应用" else "", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 2)
                    }
                    if (selection) Checkbox(androidx.compose.ui.state.ToggleableState(app.key in s.selectedApps), { vm.toggleApp(app.key) }) else Text("›", style = MiuixTheme.textStyles.title3)
                }
            }
        }
        if (filtered.isNotEmpty()) item { Text("${filtered.size} 个应用", Modifier.padding(horizontal = 16.dp), style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
    }
}

@Composable fun AppIcon(app: AppInstance, modifier: Modifier = Modifier.size(44.dp)) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, app.key.packageName) {
        value = withContext(Dispatchers.IO) { runCatching { context.packageManager.getApplicationIcon(app.key.packageName).toBitmap(96, 96) }.getOrNull() }
    }
    bitmap?.let { Image(it.asImageBitmap(), null, modifier) } ?: Box(modifier, contentAlignment = Alignment.Center) { Text(app.label.take(1).ifEmpty { "A" }, style = MiuixTheme.textStyles.title2) }
}
@Composable fun UserPicker(s: AppState, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        s.capabilities?.users.orEmpty().forEach { user ->
            TextButton("${if (user.id == selected) "✓ " else ""}${user.name} · ${user.id}${if (!user.unlocked) "（锁定）" else ""}", { onSelect(user.id) }, enabled = user.unlocked && !s.busy)
        }
    }
}
@Composable fun AppDetailPage(s: AppState, vm: AppViewModel, padding: PaddingValues, key: AppKey, navigate: (Route) -> Unit) {
    val app = s.apps.find { it.key == key }
    PageList(padding) {
        notices(s, vm)
        if (app == null) item { InfoCard("应用信息不可用", "重新扫描该用户的应用后再打开。") } else {
            item { InfoCard(app.label, "${app.key.packageName}\n用户 ${key.userId} · ${app.versionName} (${app.versionCode ?: "未知"})\n${app.apkPaths.size} 个 APK") }
            item { PrimaryAction("选择备份", !s.busy) { if (key !in s.selectedApps) vm.toggleApp(key); navigate(Route.SelectApps) } }
            val history = s.repository?.records.orEmpty().filter { it.app.key == key }
            items(history, key = { it.recordId }) { record -> Card { ArrowPreference("历史备份 · ${date(record.createdAt)}", summary = "来源 ${record.origin.label} · 用户 ${record.origin.userId}", onClick = { navigate(Route.Record(record.recordId)) }) } }
        }
    }
}
