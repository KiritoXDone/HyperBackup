package io.github.kiritoxdone.hyperbackup.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kiritoxdone.hyperbackup.*
import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.ui.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.basic.ArrowUpDown
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.preference.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable fun AppsToolbar(s: AppState, vm: AppViewModel) {
    var sort by remember { mutableStateOf(false) }
    var display by remember { mutableStateOf(false) }
    ActionIcon(MiuixIcons.Sort, "应用排序", onClick = { sort = true })
    ActionIcon(MiuixIcons.More, "应用显示选项", onClick = { display = true })
    WindowDialog(show = sort, title = "排序", onDismissRequest = { sort = false }) {
        RadioButtonPreference("名称", s.behavior.appSort == AppSort.NAME, { vm.changeBehavior { it.copy(appSort = AppSort.NAME) } })
        RadioButtonPreference("包名", s.behavior.appSort == AppSort.PACKAGE, { vm.changeBehavior { it.copy(appSort = AppSort.PACKAGE) } })
        SwitchPreference(s.behavior.sortDescending, { value -> vm.changeBehavior { it.copy(sortDescending = value) } }, "降序")
        TextButton("完成", { sort = false }, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColorsPrimary())
    }
    WindowDialog(show = display, title = "显示", onDismissRequest = { display = false }) {
        SwitchPreference(s.behavior.showPackageNames, { value -> vm.changeBehavior { it.copy(showPackageNames = value) } }, "包名")
        TextButton("完成", { display = false }, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColorsPrimary())
    }
}

@Composable fun AppsPage(s: AppState, vm: AppViewModel, padding: PaddingValues, selection: Boolean, navigate: (Route) -> Unit,
    onFiles: (() -> Unit)? = null, label: String? = null, onVisibleApps: (List<AppKey>) -> Unit = {}) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableIntStateOf(if (s.behavior.showSystemApps) 0 else 1) }
    var filterLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var labelsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(s.behavior.labels.keys) { if (filterLabel !in s.behavior.labels) filterLabel = null }
    val filtered = remember(s.apps, query, s.behavior, category, filterLabel) {
        AppListProjection.visible(s.apps, query, s.behavior.copy(showSystemApps = category != 1)).filter {
            (category != 2 || it.system) && (filterLabel == null || it.key.stableId in s.behavior.labels[filterLabel].orEmpty())
        }
    }
    LaunchedEffect(filtered) { onVisibleApps(filtered.map { it.key }) }
    PageList(padding, spacing = 0.dp) {
        if (onFiles != null) item { Segments(listOf("应用", "文件"), 0, { if (it == 1) onFiles() }, Modifier.padding(bottom = 12.dp)) }
        item { Search(query, { query = it }, "搜索名称或包名") }
        item { Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("全部", "用户应用", "系统应用").forEachIndexed { index, text ->
                TextButton(text, { category = index }, minHeight = 48.dp,
                    colors = if (category == index) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors())
            }
            ActionIcon(HyperIcons.Tag, "按标签筛选", onClick = { labelsOpen = true })
        } }
        item { Row(Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("用户 ${s.user} · ${filtered.size} 个应用", Modifier.weight(1f), style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            ActionIcon(HyperIcons.User, "切换用户", !s.busy) { navigate(Route.Users) }
        } }
        if (filtered.isEmpty()) item { EmptyContent(MiuixIcons.GridView, if (s.busy) "正在读取应用" else if (query.isNotEmpty()) "没有匹配的应用" else "暂无应用") }
        itemsIndexed(filtered, key = { _, app -> app.key.stableId }) { index, app ->
            val selected = if (label == null) app.key in s.selectedApps else app.key.stableId in s.behavior.labels[label].orEmpty()
            fun toggle() { if (label == null) vm.toggleApp(app.key) else vm.toggleLabel(label, app.key) }
            GroupRow(index == 0, index == filtered.lastIndex, { if (selection) toggle() else navigate(Route.AppDetail(app.key.userId, app.key.packageName)) }) {
                IconButton(onClick = { navigate(Route.AppDetail(app.key.userId, app.key.packageName)) }, modifier = Modifier.size(48.dp).semantics { contentDescription = "应用详情：${app.label}" }) { AppIcon(app) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(app.label.ifBlank { app.key.packageName }, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (s.behavior.showPackageNames) Text(app.key.packageName, style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (selection) Checkbox(androidx.compose.ui.state.ToggleableState(selected), { toggle() })
                else Icon(HyperIcons.Chevron, null, Modifier.size(20.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantActions)
            }
        }
    }
    WindowDialog(show = labelsOpen, title = "标签", onDismissRequest = { labelsOpen = false }) {
        RadioButtonPreference("全部", filterLabel == null, { filterLabel = null; labelsOpen = false })
        s.behavior.labels.keys.forEach { name -> RadioButtonPreference(name, filterLabel == name, { filterLabel = name; labelsOpen = false }) }
        TextButton("管理标签", { labelsOpen = false; navigate(Route.Labels) }, Modifier.fillMaxWidth())
    }
}

@Composable fun SelectionActions(s: AppState, vm: AppViewModel, files: Boolean, paths: String, visible: List<AppKey>, done: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(MiuixTheme.colorScheme.surfaceContainer).navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!files) TextButton("全选", { vm.toggleAll(visible) }, enabled = visible.isNotEmpty() && !s.busy, minHeight = 48.dp)
        TextButton(if (files) "下一步" else "下一步 · ${s.selectedApps.size}", {
            if (files) {
                vm.beginScript(ScriptCommand.BACKUP_FOLDERS)
                if (vm.state.value.error == null) vm.changeDraft { it.copy(script = requireNotNull(it.script).let { spec -> spec.copy(config = spec.config + ("Custom_path" to paths.trim())) }) }
            } else vm.beginBackup()
            if (vm.state.value.error == null && vm.state.value.draft != null) done()
        }, Modifier.weight(1f), enabled = !s.busy && if (files) paths.isNotBlank() else s.selectedApps.isNotEmpty(),
            minHeight = 48.dp, colors = ButtonDefaults.textButtonColorsPrimary())
    }
}

@Composable fun FileSelectionPage(padding: PaddingValues, paths: String, onPaths: (String) -> Unit, onFiles: (Boolean) -> Unit) {
    PageList(padding) {
        item { Segments(listOf("应用", "文件"), 1, { onFiles(it == 1) }) }
        item { SectionLabel("备份文件夹") }
        item { TextField(paths, onPaths, Modifier.fillMaxWidth(), label = "文件夹路径（每行一个）", minLines = 5) }
    }
}
