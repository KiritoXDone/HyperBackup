package io.github.kiritoxdone.hyperbackup.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import io.github.kiritoxdone.hyperbackup.*
import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.ui.*
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.nav.core.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Serializable sealed interface Route : NavKey {
    @Serializable data object Main : Route
    @Serializable data object Library : Route
    @Serializable data object Cloud : Route
    @Serializable data object Users : Route
    @Serializable data object Labels : Route
    @Serializable data class LabelApps(val name: String) : Route
    @Serializable data class EditLocation(val id: String) : Route
    @Serializable data object AddCloud : Route
    @Serializable data object SelectApps : Route
    @Serializable data class AppDetail(val user: Int, val pkg: String) : Route
    @Serializable data object Config : Route
    @Serializable data object AdvancedConfig : Route
    @Serializable data class ConfigSection(val group: ConfigGroup) : Route
    @Serializable data class ConfigField(val key: String) : Route
    @Serializable data class ConfigLocation(val safety: Boolean) : Route
    @Serializable data object Confirm : Route
    @Serializable data object Locations : Route
    @Serializable data object AddLocation : Route
    @Serializable data object Tasks : Route
    @Serializable data class Task(val id: String) : Route
    @Serializable data class Editor(val taskId: String) : Route
    @Serializable data class Batch(val id: String) : Route
    @Serializable data class Record(val id: String) : Route
    @Serializable data object Tools : Route
    @Serializable data class ToolsInGroup(val group: ToolGroup) : Route
    @Serializable data object Runtime : Route
    @Serializable data object License : Route
    @Serializable data object OpenSource : Route
    @Serializable data class Defaults(val group: SettingsGroup) : Route
    @Serializable data class DefaultField(val key: String, val profile: ScriptProfile?) : Route
    @Serializable data object AppBehavior : Route
    @Serializable data object SettingsTransfer : Route
    @Serializable data object About : Route
}

@Composable fun AppNavigation(vm: AppViewModel, state: AppState, initialTask: String?) {
    val backStack = rememberNavBackStack<Route>(Route.Main)
    val tabStates = rememberSaveableStateHolder()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    fun push(route: Route) { vm.clearError(); if (backStack.lastOrNull() != route) backStack.add(route) }
    val back = { if (backStack.size > 1) backStack.removeLastOrNull(); Unit }
    LaunchedEffect(initialTask) { initialTask?.let { id -> runCatching { Wire.uuid(id) }.onSuccess { push(Route.Task(id)) } } }
    NavDisplay(backStack = backStack, onBack = back) {
        entry<Route.Main> {
            tabStates.SaveableStateProvider(tab) {
                PageShell(listOf("HyperBackup", "历史", "云端", "设置")[tab], bottom = {
                    NavigationBar(color = MiuixTheme.colorScheme.surfaceContainer) {
                        listOf(HyperIcons.Home, HyperIcons.History, HyperIcons.Cloud, HyperIcons.Settings).forEachIndexed { index, icon ->
                            NavigationBarItem(tab == index, { vm.clearError(); tab = index }, icon, listOf("首页", "历史", "云端", "设置")[index],
                                colors = NavigationBarDefaults.navigationBarItemColors(selectedContentColor = MiuixTheme.colorScheme.primary,
                                    unselectedContentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary))
                        }
                    }
                }, actions = {
                    when (tab) {
                        0 -> ActionIcon(MiuixIcons.Info, "关于 HyperBackup", onClick = { push(Route.About) })
                        1 -> ActionIcon(MiuixIcons.Refresh, "刷新任务", !state.busy, vm::refreshTasks)
                        2 -> ActionIcon(MiuixIcons.Add, "添加云端位置", onClick = { push(Route.AddCloud) })
                    }
                }) { padding ->
                    when (tab) {
                        0 -> HomePage(state, vm, padding, ::push) { push(Route.Library) }
                        1 -> TasksPage(state, vm, padding, ::push)
                        2 -> CloudPage(state, vm, padding, ::push)
                        3 -> SettingsPage(state, vm, padding, ::push)
                    }
                }
            }
        }
        entry<Route.SelectApps> {
            var files by rememberSaveable { mutableStateOf(false) }
            var paths by rememberSaveable { mutableStateOf(state.enginePreferences.backup["Custom_path"] ?: state.scriptCatalog?.backupDefaults?.get("Custom_path").orEmpty()) }
            var visible by remember { mutableStateOf<List<AppKey>>(emptyList()) }
            val targetStates = rememberSaveableStateHolder()
            PageShell("备份", back, subtitle = if (files) "文件夹" else "已选 ${state.selectedApps.size} 个应用", bottom = {
                SelectionActions(state, vm, files, paths, visible) { push(Route.Config) }
            }, actions = { if (!files) AppsToolbar(state, vm) }) {
                val padding = it
                targetStates.SaveableStateProvider(files) {
                    if (files) FileSelectionPage(padding, paths, { paths = it }, { files = it })
                    else AppsPage(state, vm, padding, true, ::push, onFiles = { files = true }, onVisibleApps = { visible = it })
                }
            }
        }
        entry<Route.Library> { PageShell("恢复", back, actions = {
            ActionIcon(MiuixIcons.Refresh, "刷新备份库", !state.busy, vm::scan)
        }) { LibraryPage(state, vm, it, ::push) } }
        entry<Route.Cloud> { PageShell("云端", back, actions = { ActionIcon(MiuixIcons.Add, "添加云端位置", onClick = { push(Route.AddCloud) }) }) { CloudPage(state, vm, it, ::push) } }
        entry<Route.Users> { PageShell("用户", back) { UsersPage(state, vm, it) } }
        entry<Route.Labels> { PageShell("标签与黑名单", back) { LabelsPage(state, vm, it, ::push) } }
        entry<Route.LabelApps> { route -> PageShell(route.name, back) { AppsPage(state, vm, it, true, ::push, label = route.name) } }
        entry<Route.AddCloud> { PageShell("添加云端位置", back) { AddLocationPage(state, vm, it, back, remoteOnly = true) } }
        entry<Route.EditLocation> { route -> PageShell("编辑位置", back) { AddLocationPage(state, vm, it, back, editId = route.id) } }
        entry<Route.AppDetail> { route ->
            PageShell("应用详情", back, showTitle = false) { AppDetailPage(state, vm, it, AppKey(route.user, route.pkg), ::push) }
        }
        entry<Route.Locations> { PageShell("备份位置", back) { LocationsPage(state, vm, it, ::push) } }
        entry<Route.AddLocation> { PageShell("添加位置", back) { AddLocationPage(state, vm, it, back) } }
        entry<Route.Config> {
            PageShell(state.draft?.script?.command?.label ?: "操作设置", back, bottom = {
                BottomAction("检查并继续", state.draft != null && !state.busy) { vm.preflight { push(Route.Confirm) } }
            }) { ConfigPage(state, vm, it, ::push) }
        }
        entry<Route.AdvancedConfig> { PageShell("高级设置", back) { AdvancedConfigPage(state, vm, it, ::push) } }
        entry<Route.ConfigSection> { route -> PageShell(route.group.title, back) { ConfigSectionPage(state, vm, it, route.group, ::push) } }
        entry<Route.ConfigField> { route -> PageShell(state.configFields().find { it.key == route.key }?.let(::settingLabel) ?: "编辑选项", back) { ConfigFieldPage(state, vm, it, route.key, back) } }
        entry<Route.ConfigLocation> { route -> PageShell(if (route.safety) "副本保存位置" else "选择位置", back) { ConfigLocationPage(state, vm, it, route.safety, back) } }
        entry<Route.Confirm> { PageShell("确认${(state.plan?.draft?.script?.command?.label ?: state.plan?.draft?.operation?.label()).orEmpty()}", back) { ConfirmPage(state, vm, it) { id ->
            while (backStack.size > 1) backStack.removeLastOrNull()
            push(Route.Task(id))
        } } }
        entry<Route.Editor> { route -> PageShell("选择内容", back) { NativeEditorPage(state, vm, it, route.taskId, back) } }
        entry<Route.Tasks> { PageShell("任务中心", back) { TasksPage(state, vm, it, ::push) } }
        entry<Route.Task> { route -> PageShell(state.tasks.find { it.id == route.id }?.state?.label() ?: "任务详情", back) { TaskPage(state, vm, it, route.id, ::push) } }
        entry<Route.Batch> { route -> PageShell("备份批次", back) { BatchPage(state, vm, it, route.id, ::push) } }
        entry<Route.Record> { route -> PageShell("备份版本", back) { RecordPage(state, vm, it, route.id, ::push) } }
        entry<Route.ToolsInGroup> { route -> PageShell(route.group.title, back) { ToolGroupPage(state, vm, it, route.group, ::push) } }
        entry<Route.Runtime> { PageShell("运行环境", back) { RuntimePage(state, it) } }
        entry<Route.Tools> { PageShell("高级操作", back) { ScriptToolsPage(state, vm, it, ::push) } }
        entry<Route.License> { PageShell("许可证", back) { LicensePage(it) } }
        entry<Route.OpenSource> { PageShell("开源组件", back) { OpenSourcePage(it) } }
        entry<Route.Defaults> { route -> PageShell(route.group.title, back) { EngineSettingsPage(state, vm, it, route.group, ::push) } }
        entry<Route.DefaultField> { route -> PageShell(state.scriptCatalog?.fields?.find { it.key == route.key }?.let(::settingLabel) ?: "编辑选项", back) {
            DefaultFieldPage(state, vm, it, route.key, route.profile, back)
        } }
        entry<Route.AppBehavior> { PageShell("应用行为", back) { AppBehaviorPage(state, vm, it) } }
        entry<Route.SettingsTransfer> { PageShell("配置管理", back) { SettingsTransferPage(state, vm, it) } }
        entry<Route.About> { PageShell("关于", back) { AboutPage(it, ::push) } }
    }
    val missingLocation = state.error == "请先添加备份位置"
    WindowDialog(show = state.error != null, title = if (missingLocation) "未添加备份位置" else "操作未完成",
        summary = if (missingLocation) null else state.error?.let(::friendlyError)?.take(240), onDismissRequest = { vm.clearError() }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (missingLocation) {
                TextButton("取消", { vm.clearError() }, Modifier.weight(1f))
                TextButton("添加位置", { vm.clearError(); push(Route.AddLocation) }, Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
            } else TextButton("确定", { vm.clearError() }, Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColorsPrimary())
        }
    }

}

@Composable fun PageShell(title: String, back: (() -> Unit)? = null, bottom: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}, subtitle: String? = null, showTitle: Boolean = true,
    content: @Composable (PaddingValues) -> Unit) {
    Scaffold(Modifier.fillMaxSize().imePadding(), topBar = {
        Column(Modifier.statusBarsPadding().fillMaxWidth()) {
            if (back != null) Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically) {
                ActionIcon(MiuixIcons.Back, "返回", onClick = back)
                Spacer(Modifier.weight(1f)); actions()
            }
            if (showTitle) Row(Modifier.fillMaxWidth().padding(start = 28.dp, end = 12.dp, top = if (back == null) 8.dp else 0.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp)
                    if (subtitle != null) Text(subtitle, Modifier.padding(top = 3.dp), style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                if (back == null) actions()
            }
        }
    }, bottomBar = bottom, content = content)
}
@Composable fun BottomAction(text: String, enabled: Boolean, action: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MiuixTheme.colorScheme.surfaceContainer).navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 10.dp)) { PrimaryAction(text, enabled, action) }
}
@Suppress("UNUSED_PARAMETER")
fun LazyListScope.notices(state: AppState, vm: AppViewModel, navigate: ((Route) -> Unit)? = null) {
    if (state.busy && state.connectionState != RootConnectionState.CHECKING) item("loading") {
        Text("正在处理…", Modifier.padding(horizontal = 16.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }

}
fun friendlyError(raw: String): String = when {
    raw.contains("ROOT_") -> "Root 未连接或授权失败。请在 Root 管理器中允许 HyperBackup，然后重新检查。\n$raw"
    raw.contains("WRITER") -> "仓库已有写入声明。需核对上一次任务；不会仅凭超时抢锁。\n$raw"
    raw.contains("SPACE") -> "本地暂存或备份位置空间不足。\n$raw"
    raw.contains("RECONCILIATION") -> "存在待核对任务，请进入任务中心检查。\n$raw"
    else -> raw
}
