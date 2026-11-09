package io.github.kiritoxdone.hyperbackup.feature

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.kiritoxdone.hyperbackup.*
import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.ui.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*

@Composable fun SettingsPage(s: AppState, vm: AppViewModel, padding: PaddingValues, navigate: (Route) -> Unit) {
    val themes = listOf("system", "light", "dark")
    PageList(padding) {
        item { SectionLabel("环境") }
        item { SurfaceCard {
            ArrowPreference("Root", summary = if (s.connectionState == RootConnectionState.READY) "已连接" else "未连接",
                onClick = { navigate(Route.Runtime) })
            ArrowPreference("用户", summary = s.capabilities?.users?.joinToString { it.name } ?: "用户 ${s.user}", onClick = { navigate(Route.Users) })
        } }
        item { SectionLabel("备份与恢复") }
        item { SurfaceCard {
            ArrowPreference("备份位置", summary = s.destinations.find { it.id == s.destinationId }?.address, onClick = { navigate(Route.Locations) })
            listOf(SettingsGroup.BACKUP, SettingsGroup.RESTORE, SettingsGroup.COMPRESSION).forEach { group ->
                ArrowPreference(group.title, onClick = { navigate(Route.Defaults(group)) })
            }
            SwitchPreference(s.behavior.showSystemApps, { value -> vm.changeBehavior { it.copy(showSystemApps = value) } }, "显示系统应用")
        } }
        item { SectionLabel("管理") }
        item { SurfaceCard {
            ArrowPreference("标签与黑名单", onClick = { navigate(Route.Labels) })
            ArrowPreference("名单与排除", onClick = { navigate(Route.Defaults(SettingsGroup.SELECTION)) })
            ArrowPreference("云端位置", onClick = { navigate(Route.Cloud) })
            ArrowPreference("配置管理", onClick = { navigate(Route.SettingsTransfer) })
            ArrowPreference("运行与日志", onClick = { navigate(Route.Defaults(SettingsGroup.EXECUTION)) })
            ArrowPreference("更多功能", onClick = { navigate(Route.Tools) })
        } }
        item { SectionLabel("外观与行为") }
        item { SurfaceCard {
            OverlaySpinnerPreference(items = listOf(DropdownItem("跟随系统"), DropdownItem("浅色"), DropdownItem("深色")),
                selectedIndex = themes.indexOf(s.theme).coerceAtLeast(0), title = "主题", onSelectedIndexChange = { vm.theme(themes[it]) })
            SwitchPreference(s.behavior.dynamicColor, { value -> vm.changeBehavior { it.copy(dynamicColor = value) } }, "动态配色")
            ArrowPreference("引擎语言", onClick = { navigate(Route.DefaultField("Shell_LANG", null)) })
            SwitchPreference(s.behavior.completionNotification, { value -> vm.changeBehavior { it.copy(completionNotification = value) } }, "任务完成通知")
            ArrowPreference("应用行为", onClick = { navigate(Route.AppBehavior) })
        } }
        item { SectionLabel("关于") }
        item { SurfaceCard { ArrowPreference("关于 HyperBackup", summary = BuildConfig.VERSION_NAME, onClick = { navigate(Route.About) }) } }
    }
}
@Composable fun RuntimePage(s: AppState, padding: PaddingValues) {
    PageList(padding) {
        item { SectionLabel("环境信息") }
        item { InfoCard("Root 授权", when (s.connectionState) {
            RootConnectionState.READY -> "已连接"
            RootConnectionState.CHECKING -> "正在检查"
            RootConnectionState.UNAVAILABLE -> "连接不可用，请在首页重新检查"
            else -> "尚未检查，可在首页申请授权"
        }) }
        s.capabilities?.let { caps -> item { InfoCard("系统", "Android ${caps.androidVersion} · ${caps.abi}\n${caps.users.joinToString { it.name }}") } }
        item { InfoCard("引擎版本", "backup_script v1117\n${Wire.UPSTREAM}") }
        s.connectionError?.let { error -> item { InfoCard("诊断详情", error) } }
    }
}
@Composable fun AboutPage(padding: PaddingValues, navigate: (Route) -> Unit) {
    val context = LocalContext.current
    val icon = remember(context) { context.packageManager.getApplicationIcon(context.packageName).toBitmap(192, 192).asImageBitmap() }
    fun open(path: String) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/KiritoXDone/HyperBackup$path"))) }
    PageList(padding) {
        item { Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(icon, null, Modifier.size(80.dp))
            Text("HyperBackup", Modifier.padding(top = 16.dp), fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", Modifier.padding(top = 4.dp), color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceVariantSummary)
        } }
        item { SurfaceCard(Modifier.fillMaxWidth()) {
            ArrowPreference("项目主页", summary = "KiritoXDone / HyperBackup", onClick = { open("") })
            ArrowPreference("问题反馈", onClick = { open("/issues") })
            ArrowPreference("发行版本", onClick = { open("/releases") })
        } }
        item { SurfaceCard(Modifier.fillMaxWidth()) {
            ArrowPreference("许可证", summary = "GPL-3.0-only", onClick = { navigate(Route.License) })
            ArrowPreference("开源组件", onClick = { navigate(Route.OpenSource) })
            ArrowPreference("运行环境", onClick = { navigate(Route.Runtime) })
        } }
    }
}
@Composable fun LicensePage(padding: PaddingValues) {
    val context = LocalContext.current
    val license = remember { context.assets.open("LICENSE.txt").bufferedReader().use { it.readText() } }
    PageList(padding) { item { Text(license, Modifier.padding(horizontal = 12.dp)) } }
}
@Composable fun OpenSourcePage(padding: PaddingValues) {
    val context = LocalContext.current
    PageList(padding) {
        item { SurfaceCard(Modifier.fillMaxWidth()) {
            listOf(
                Triple("backup_script", "v1117 · GPL-3.0-only", "https://github.com/YAWAsau/backup_script/tree/${Wire.UPSTREAM}"),
                Triple("Miuix", "0.9.4 · Apache-2.0", "https://github.com/compose-miuix-ui/miuix"),
                Triple("libsu", "6.0.0 · Apache-2.0", "https://github.com/topjohnwu/libsu"),
                Triple("KernelSU", "状态卡设计参考 · GPL-3.0", "https://github.com/tiann/KernelSU/tree/0c0fdb960178fa12d80d6cbcfdcb6ad8be6a865d")
            ).forEach { (name, version, url) -> ArrowPreference(name, summary = version, onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) }
        } }
    }
}
