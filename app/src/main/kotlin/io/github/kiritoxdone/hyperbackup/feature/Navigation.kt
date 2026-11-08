package io.github.kiritoxdone.hyperbackup.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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

@Serializable sealed interface Route : NavKey {
    @Serializable data object Main : Route
    @Serializable data object SelectApps : Route
    @Serializable data class AppDetail(val user: Int, val pkg: String) : Route
    @Serializable data object Config : Route
    @Serializable data object Confirm : Route
    @Serializable data object Locations : Route
    @Serializable data object AddLocation : Route
    @Serializable data object Tasks : Route
    @Serializable data class Task(val id: String) : Route
    @Serializable data class Editor(val taskId: String) : Route
    @Serializable data class Batch(val id: String) : Route
    @Serializable data class Record(val id: String) : Route
    @Serializable data object Tools : Route
    @Serializable data object About : Route
}

@Composable fun AppNavigation(vm: AppViewModel, state: AppState, initialTask: String?) {
    val backStack = rememberNavBackStack<Route>(Route.Main)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    fun push(route: Route) { if (backStack.lastOrNull() != route) backStack.add(route) }
    val back = { if (backStack.size > 1) backStack.removeLastOrNull(); Unit }
    LaunchedEffect(initialTask) { initialTask?.let { id -> runCatching { Wire.uuid(id) }.onSuccess { push(Route.Task(id)) } } }
    NavDisplay(backStack = backStack, onBack = back) {
        entry<Route.Main> {
            PageShell(listOf("备份", "应用", "备份库", "设置")[tab], bottom = {
                NavigationBar {
                    listOf(MiuixIcons.Home, MiuixIcons.GridView, MiuixIcons.Backup, MiuixIcons.Settings).forEachIndexed { index, icon ->
                        NavigationBarItem(tab == index, { tab = index }, icon, listOf("首页", "应用", "备份库", "设置")[index],
                            colors = NavigationBarDefaults.navigationBarItemColors(selectedContentColor = MiuixTheme.colorScheme.onSurface,
                                unselectedContentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary))
                    }
                }
            }, actions = {
                IconButton(onClick = { push(Route.Tasks) }) { Icon(MiuixIcons.Recent, "任务中心") }
            }) { padding ->
                when (tab) {
                    0 -> HomePage(state, vm, padding, ::push, { tab = 2 })
                    1 -> AppsPage(state, vm, padding, false, ::push)
                    2 -> LibraryPage(state, vm, padding, ::push)
                    3 -> SettingsPage(state, vm, padding, ::push)
                }
            }
        }
        entry<Route.SelectApps> {
            PageShell("选择应用", back, bottom = { BottomAction("下一步 · 已选 ${state.selectedApps.size}", state.selectedApps.isNotEmpty() && !state.busy) {
                vm.beginBackup(); if (vm.state.value.draft != null) push(Route.Config)
            } }) { AppsPage(state, vm, it, true, ::push) }
        }
        entry<Route.AppDetail> { route ->
            PageShell("应用详情", back) { AppDetailPage(state, vm, it, AppKey(route.user, route.pkg), ::push) }
        }
        entry<Route.Locations> { PageShell("备份位置", back) { LocationsPage(state, vm, it, ::push) } }
        entry<Route.AddLocation> { PageShell("添加位置", back) { AddLocationPage(state, vm, it, back) } }
        entry<Route.Config> { PageShell("${state.draft?.operation?.label().orEmpty()}设置", back) { ConfigPage(state, vm, it) { push(Route.Confirm) } } }
        entry<Route.Confirm> { PageShell("确认${state.plan?.draft?.operation?.label().orEmpty()}", back) { ConfirmPage(state, vm, it) { id ->
            while (backStack.size > 1) backStack.removeLastOrNull()
            push(Route.Task(id))
        } } }
        entry<Route.Editor> { route -> PageShell("引擎选择", back) { NativeEditorPage(state, vm, it, route.taskId, back) } }
        entry<Route.Tasks> { PageShell("任务中心", back) { TasksPage(state, vm, it, ::push) } }
        entry<Route.Task> { route -> PageShell("任务详情", back) { TaskPage(state, vm, it, route.id, ::push) } }
        entry<Route.Batch> { route -> PageShell("备份批次", back) { BatchPage(state, vm, it, route.id, ::push) } }
        entry<Route.Record> { route -> PageShell("备份版本", back) { RecordPage(state, vm, it, route.id, ::push) } }
        entry<Route.Tools> { PageShell("引擎功能", back) { ScriptToolsPage(state, vm, it, ::push) } }
        entry<Route.About> { PageShell("关于 HyperBackup", back) { AboutPage(it) } }
    }
}

@Composable fun PageShell(title: String, back: (() -> Unit)? = null, bottom: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    val scroll = MiuixScrollBehavior()
    Scaffold(Modifier.nestedScroll(scroll.nestedScrollConnection).imePadding(),
        topBar = { TopAppBar(title, scrollBehavior = scroll, navigationIcon = {
            if (back != null) IconButton(onClick = back) { Icon(MiuixIcons.Back, "返回") }
        }, actions = actions) }, bottomBar = bottom, content = content)
}
@Composable fun BottomAction(text: String, enabled: Boolean, action: () -> Unit) {
    Column(Modifier.navigationBarsPadding().padding(16.dp)) { PrimaryAction(text, enabled, action) }
}
fun LazyListScope.notices(state: AppState, vm: AppViewModel, navigate: ((Route) -> Unit)? = null) {
    if (state.busy) item("loading") { InfoCard("正在处理", "请稍候，提交后会先确认任务身份。") }
    state.error?.let { error -> item("error") { Card { InfoCard("需要处理", friendlyError(error)); TextButton("收起", { vm.clearError() }) } } }
    if (state.capabilities == null) item("root") { Card { InfoCard("Root 尚未连接", "授权后检查引擎、用户和可用应用。已缓存的备份清单仍可浏览。")
        TextButton("授权并检查", vm::connect, enabled = !state.busy) } }
    if (navigate != null) state.tasks.firstOrNull { it.state in setOf(TaskState.RUNNING, TaskState.STOP_REQUESTED) }?.let { task ->
        item("active") { Card(onClick = { navigate(Route.Task(task.id)) }) { InfoCard("${task.plan.draft.operation.label()}${task.state.label()}", "点击回到同一任务 · 项目 ${task.items.count { it.outcome == Outcome.SUCCEEDED }}/${task.items.size}") } }
    }
}
fun friendlyError(raw: String): String = when {
    raw.contains("ROOT_") -> "Root 未连接或授权失败。请在 Root 管理器中允许 HyperBackup，然后重新检查。\n$raw"
    raw.contains("WRITER") -> "仓库已有写入声明。需核对上一次任务；不会仅凭超时抢锁。\n$raw"
    raw.contains("SPACE") -> "本地暂存或备份位置空间不足。\n$raw"
    raw.contains("RECONCILIATION") -> "存在待核对任务，请进入任务中心检查。\n$raw"
    else -> raw
}
