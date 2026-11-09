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
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun HomePage(s: AppState, vm: AppViewModel, padding: PaddingValues, navigate: (Route) -> Unit, library: () -> Unit) {
    PageList(padding) {
        item("connection") { HomeStatusCard(s, vm::connect) }
        notices(s, vm)
        item { SectionLabel("备份与恢复") }
        item { Card(Modifier.fillMaxWidth()) {
            ArrowPreference("备份应用", startAction = { LeadingIcon(MiuixIcons.GridView) }, onClick = { navigate(Route.SelectApps) })
            ArrowPreference("备份文件夹", startAction = { LeadingIcon(MiuixIcons.Folder) }, onClick = {
                vm.beginScript(ScriptCommand.BACKUP_FOLDERS)
                if (vm.state.value.error == null && vm.state.value.draft?.script?.command == ScriptCommand.BACKUP_FOLDERS) navigate(Route.Config)
            })
            ArrowPreference("恢复备份", startAction = { LeadingIcon(MiuixIcons.Reset) }, onClick = library)
            ArrowPreference("更多功能", startAction = { LeadingIcon(MiuixIcons.More) }, onClick = { navigate(Route.Tools) })
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
            ArrowPreference(task?.let { "${it.plan.draft.script?.command?.label ?: it.plan.draft.operation.label()} · ${it.state.label()}" } ?: "还没有任务",
                summary = task?.let { date(it.startedAt) }, onClick = { navigate(task?.let { Route.Task(it.id) } ?: Route.Tasks) })
        } }
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
