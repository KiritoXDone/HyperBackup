package io.github.kiritoxdone.hyperbackup.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        SwitchPreference(s.behavior.showSystemApps, { value -> vm.changeBehavior { it.copy(showSystemApps = value) } }, "系统应用")
        SwitchPreference(s.behavior.showPackageNames, { value -> vm.changeBehavior { it.copy(showPackageNames = value) } }, "包名")
        TextButton("完成", { display = false }, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColorsPrimary())
    }
}

@Composable fun AppsPage(s: AppState, vm: AppViewModel, padding: PaddingValues, selection: Boolean, navigate: (Route) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var usersOpen by remember { mutableStateOf(false) }
    val filtered = remember(s.apps, query, s.behavior) { AppListProjection.visible(s.apps, query, s.behavior) }
    val users = s.capabilities?.users ?: listOf(AndroidUser(s.user, if (s.user == 0) "主用户" else "用户 ${s.user}", true))
    PageList(padding) {
        item { Search(query, { query = it }, "搜索名称或包名") }
        item { Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { usersOpen = true }, minWidth = 0.dp, minHeight = 48.dp,
                insideMargin = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(color = Color.Transparent, disabledColor = Color.Transparent)) {
                Text(users.find { it.id == s.user }?.name ?: "用户 ${s.user}", Modifier.widthIn(max = 190.dp),
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(MiuixIcons.Basic.ArrowUpDown, null, Modifier.padding(start = 7.dp).size(14.dp))
            }
            Spacer(Modifier.weight(1f))
            if (selection && s.selectedApps.isNotEmpty()) Text("${s.selectedApps.size}", color = MiuixTheme.colorScheme.primary)
            ActionIcon(if (selection) MiuixIcons.All else MiuixIcons.Edit, if (selection) "全选" else "选择应用", enabled = !s.busy && filtered.isNotEmpty()) {
                if (selection) vm.toggleAll(filtered.map { it.key }) else navigate(Route.SelectApps)
            }
        } }
        if (filtered.isEmpty()) item { EmptyContent(MiuixIcons.GridView, if (s.busy) "正在读取应用" else if (query.isNotEmpty()) "没有匹配的应用" else "暂无应用") }
        items(filtered, key = { it.key.stableId }) { app ->
            ApplicationRow(app, AppListProjection.presence(app.key, s.repository), s.behavior.showPackageNames,
                selection, app.key in s.selectedApps) {
                if (selection) vm.toggleApp(app.key) else navigate(Route.AppDetail(app.key.userId, app.key.packageName))
            }
        }
    }
    WindowDialog(show = usersOpen, title = "用户", onDismissRequest = { usersOpen = false }) {
        users.forEach { user -> RadioButtonPreference(user.name, user.id == s.user,
            { if (user.id != s.user) vm.selectUser(user.id); usersOpen = false }, enabled = user.unlocked && !s.busy) }
        TextButton("取消", { usersOpen = false }, Modifier.fillMaxWidth())
    }
}

@Composable private fun ApplicationRow(app: AppInstance, presence: BackupPresence, showPackage: Boolean,
    selection: Boolean, selected: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth(), onClick = onClick, pressFeedbackType = PressFeedbackType.Sink) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp).heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(app)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(app.label.ifBlank { app.key.packageName }, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (showPackage) Text(app.key.packageName, style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (selection) Checkbox(androidx.compose.ui.state.ToggleableState(selected), { onClick() })
            else {
                BackupBadge(presence)
                Icon(MiuixIcons.Basic.ArrowRight, null, Modifier.size(width = 10.dp, height = 16.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantActions)
            }
        }
    }
}
@Composable private fun BackupBadge(presence: BackupPresence) {
    val present = presence == BackupPresence.PRESENT
    Text(when (presence) { BackupPresence.PRESENT -> "已备份"; BackupPresence.ABSENT -> "未备份"; BackupPresence.UNKNOWN -> "未扫描" },
        Modifier.background(if (present) MiuixTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp), style = MiuixTheme.textStyles.footnote1,
        color = if (present) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary)
}
