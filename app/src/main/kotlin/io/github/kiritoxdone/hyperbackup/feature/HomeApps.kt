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
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun HomePage(s: AppState, vm: AppViewModel, padding: PaddingValues, navigate: (Route) -> Unit, library: () -> Unit) {
    PageList(padding) {
        notices(s, vm, navigate)
        item {
            val last = s.tasks.firstOrNull { it.plan.draft.operation == Operation.BACKUP }
            StatusCard(if (last == null) "为下一次迁移做好准备" else "上次备份${last.state.label()}",
                if (last == null) "选择应用与备份位置，保存独立的备份版本。" else "${date(last.startedAt)} · ${last.items.size} 个应用",
                warning = last?.state in setOf(TaskState.PARTIAL, TaskState.FAILED, TaskState.NEEDS_RECONCILIATION), success = last?.state == TaskState.SUCCEEDED)
        }
        item { Card {
            ArrowPreference("备份应用", summary = "选择应用和要保存的数据", onClick = { navigate(Route.SelectApps) })
            ArrowPreference("恢复应用", summary = "从已有备份中选择版本", onClick = library)
            ArrowPreference("其他数据与高级操作", summary = "Wi-Fi、通信记录、文件夹、应用状态与维护", onClick = { navigate(Route.Tools) })
            ArrowPreference("备份位置", summary = s.destinations.find { it.id == s.destinationId }?.let { "${it.name} · ${it.protocol}" } ?: "尚未选择", onClick = { navigate(Route.Locations) })
        } }
        item { Card {
            ArrowPreference("最近任务", summary = s.tasks.firstOrNull()?.state?.label() ?: "还没有任务", onClick = { navigate(Route.Tasks) })
            ArrowPreference("运行环境", summary = s.capabilities?.let { "Root 已连接 · Android ${it.androidVersion} · ${it.abi}" } ?: "等待授权", onClick = vm::connect)
        } }
        item { InfoCard("保存到手机之外", "本地备份仍在手机中，刷机可能清除。需要保留时，请复制到 NAS 或其他设备。") }
    }
}

@Composable fun AppsPage(s: AppState, vm: AppViewModel, padding: PaddingValues, selection: Boolean, navigate: (Route) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(s.apps, query) { s.apps.filter { !ProtectionPolicy.protected(it) && (it.label.contains(query, true) || it.key.packageName.contains(query, true)) }.sortedBy { it.label.lowercase() } }
    PageList(padding) {
        notices(s, vm, if (selection) null else navigate)
        item { Search(query, { query = it }, "搜索名称或包名") }
        item { UserPicker(s, s.user, vm::selectUser) }
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${filtered.size} 个应用", Modifier.weight(1f), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            TextButton(if (selection) "全选当前筛选" else "选择", {
                if (selection) vm.toggleAll(filtered.map { it.key }) else navigate(Route.SelectApps)
            }, enabled = !s.busy)
        } }
        if (selection && s.selectedApps.any { it.userId != s.user }) item { InfoCard("跨用户选择", "其他用户已选 ${s.selectedApps.count { it.userId != s.user }} 项；提交前会列出全部来源。") }
        if (filtered.isEmpty()) item { InfoCard("没有可显示的应用", if (s.capabilities == null) "请先授权并扫描。" else "当前用户或筛选条件下没有普通应用。") }
        items(filtered, key = { it.key.stableId }) { app ->
            Card(onClick = { if (selection) vm.toggleApp(app.key) else navigate(Route.AppDetail(app.key.userId, app.key.packageName)) }) {
                Row(Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(app)
                    Column(Modifier.weight(1f)) {
                        Text(app.label.ifEmpty { app.key.packageName }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(app.key.packageName, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 2)
                    }
                    if (selection) Checkbox(androidx.compose.ui.state.ToggleableState(app.key in s.selectedApps), { vm.toggleApp(app.key) }) else Text("›", style = MiuixTheme.textStyles.title3)
                }
            }
        }
        if (s.apps.isNotEmpty()) item { Text("已隐藏 ${s.apps.count(ProtectionPolicy::protected)} 个系统或受保护应用。", color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
    }
}

@Composable fun AppIcon(app: AppInstance) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, app.key.packageName) {
        value = withContext(Dispatchers.IO) { runCatching { context.packageManager.getApplicationIcon(app.key.packageName).toBitmap(96, 96) }.getOrNull() }
    }
    bitmap?.let { Image(it.asImageBitmap(), null, Modifier.size(44.dp)) } ?: Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { Text(app.label.take(1).ifEmpty { "A" }, style = MiuixTheme.textStyles.title2) }
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
            item { InfoCard(app.label, "${app.key.packageName}\n用户 ${key.userId} · ${app.versionName} (${app.versionCode ?: "未知"})\n${app.apkPaths.size} 个 APK；数据大小会在执行前检查。") }
            item { PrimaryAction("选择备份", !s.busy) { if (key !in s.selectedApps) vm.toggleApp(key); navigate(Route.SelectApps) } }
            item { InfoCard("数据范围", "默认安装包、CE 与 DE 私有数据。外部应用数据、OBB 和媒体可选。应用状态尽力记录；硬件绑定密钥和登录状态不能保证迁移。") }
            val history = s.repository?.records.orEmpty().filter { it.app.key == key }
            items(history, key = { it.recordId }) { record -> Card { ArrowPreference("历史备份 · ${date(record.createdAt)}", summary = "来源 ${record.origin.label} · 用户 ${record.origin.userId}", onClick = { navigate(Route.Record(record.recordId)) }) } }
        }
    }
}
