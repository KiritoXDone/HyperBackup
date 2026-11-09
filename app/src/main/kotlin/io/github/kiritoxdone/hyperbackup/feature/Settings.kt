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
    val context = LocalContext.current
    val themes = listOf("system", "light", "dark")
    PageList(padding) {
        item { SectionLabel("备份与恢复") }
        item { Card(Modifier.fillMaxWidth()) {
            ArrowPreference("备份位置", summary = s.destinations.find { it.id == s.destinationId }?.name,
                startAction = { LeadingIcon(MiuixIcons.Folder) }, onClick = { navigate(Route.Locations) })
            SettingsGroup.entries.forEach { group ->
                ArrowPreference(group.title, startAction = { LeadingIcon(group.icon()) }, onClick = { navigate(Route.Defaults(group)) })
            }
        } }
        item { SectionLabel("应用") }
        item { Card(Modifier.fillMaxWidth()) {
            OverlaySpinnerPreference(items = listOf(DropdownItem("跟随系统"), DropdownItem("浅色"), DropdownItem("深色")),
                selectedIndex = themes.indexOf(s.theme).coerceAtLeast(0), title = "主题", startAction = { LeadingIcon(MiuixIcons.Theme) },
                onSelectedIndexChange = { vm.theme(themes[it]) })
            ArrowPreference("应用行为", startAction = { LeadingIcon(MiuixIcons.Settings) }, onClick = { navigate(Route.AppBehavior) })
            ArrowPreference("配置管理", startAction = { LeadingIcon(MiuixIcons.Import) }, onClick = { navigate(Route.SettingsTransfer) })
            ArrowPreference("通知", startAction = { LeadingIcon(MiuixIcons.Notes) }, onClick = {
                context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName))
            })
            ArrowPreference("关于", summary = BuildConfig.VERSION_NAME, startAction = { LeadingIcon(MiuixIcons.Info) }, onClick = { navigate(Route.About) })
        } }
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
@Composable fun LocationsPage(s: AppState, vm: AppViewModel, padding: PaddingValues, navigate: (Route) -> Unit) {
    PageList(padding) {
        notices(s, vm)
        items(s.destinations, key = { it.id }) { d -> Card {
            RadioButtonPreference(title = d.name, summary = "${d.protocol} · ${d.address}\n${when { d.protocol == Protocol.LOCAL -> "仍在手机中，刷机可能清除"; d.allowHttp -> "HTTP · 未加密传输"; d.protocol == Protocol.SMB -> "SMB2/3 · 签名，传输加密未承诺"; else -> "HTTPS · 校验证书" }}",
                selected = s.destinationId == d.id, onClick = { vm.selectDestination(d.id) }, enabled = !s.busy)
        } }
        item { PrimaryAction("添加位置", !s.busy) { navigate(Route.AddLocation) } }
    }
}
@Composable fun AddLocationPage(s: AppState, vm: AppViewModel, padding: PaddingValues, done: () -> Unit) {
    var protocol by rememberSaveable { mutableStateOf(Protocol.LOCAL) }
    var name by rememberSaveable { mutableStateOf("手机备份") }
    var address by rememberSaveable { mutableStateOf("/storage/emulated/0/HyperBackup") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var domain by rememberSaveable { mutableStateOf("") }
    var http by rememberSaveable { mutableStateOf(false) }
    var create by rememberSaveable { mutableStateOf(false) }
    PageList(padding) {
        notices(s, vm)
        item { Card {
            Protocol.entries.forEach { type -> RadioButtonPreference(title = type.name, selected = protocol == type, onClick = {
                protocol = type; name = when (type) { Protocol.LOCAL -> "手机备份"; Protocol.SMB -> "家庭 NAS"; Protocol.WEBDAV -> "WebDAV" }
                address = when (type) { Protocol.LOCAL -> "/storage/emulated/0/HyperBackup"; Protocol.SMB -> "smb://"; Protocol.WEBDAV -> "https://" }
            }) }
        } }
        item { TextField(name, { name = it }, Modifier.fillMaxWidth(), label = "位置名称", singleLine = true) }
        item { TextField(address, { address = it }, Modifier.fillMaxWidth(), label = if (protocol == Protocol.LOCAL) "目录绝对路径" else if (protocol == Protocol.SMB) "smb://主机/共享名/目录" else "https://主机/目录", singleLine = true) }
        if (protocol != Protocol.LOCAL) {
            item { TextField(username, { username = it }, Modifier.fillMaxWidth(), label = "用户名", singleLine = true) }
            item { TextField(password, { password = it }, Modifier.fillMaxWidth(), label = "密码", singleLine = true, visualTransformation = PasswordVisualTransformation()) }
        }
        if (protocol == Protocol.SMB) item { TextField(domain, { domain = it }, Modifier.fillMaxWidth(), label = "域（可留空）", singleLine = true) }
        if (protocol == Protocol.WEBDAV) item { Card { SwitchPreference(http, { http = it }, "允许 HTTP", summary = "显式启用未加密传输；证书错误不会自动忽略。") } }
        item { Card { SwitchPreference(create, { create = it }, "创建新仓库", summary = "默认连接已有仓库。创建要求空目录；远端目录需先在服务器创建。") } }
        item { PrimaryAction(if (create) "检查并创建仓库" else "连接并读取清单", !s.busy && name.isNotBlank() && address.isNotBlank()) {
            vm.saveDestination(Connection(Destination(name = name.trim(), protocol = protocol, address = address.trim().trimEnd('/'), username = username, domain = domain, allowHttp = http), password), create, done)
        } }
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
        item { Card(Modifier.fillMaxWidth()) {
            ArrowPreference("项目主页", summary = "KiritoXDone / HyperBackup", onClick = { open("") })
            ArrowPreference("问题反馈", onClick = { open("/issues") })
            ArrowPreference("发行版本", onClick = { open("/releases") })
        } }
        item { Card(Modifier.fillMaxWidth()) {
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
        item { Card(Modifier.fillMaxWidth()) {
            listOf(
                Triple("backup_script", "v1117 · GPL-3.0-only", "https://github.com/YAWAsau/backup_script/tree/${Wire.UPSTREAM}"),
                Triple("Miuix", "0.9.4 · Apache-2.0", "https://github.com/compose-miuix-ui/miuix"),
                Triple("libsu", "6.0.0 · Apache-2.0", "https://github.com/topjohnwu/libsu"),
                Triple("KernelSU", "状态卡设计参考 · GPL-3.0", "https://github.com/tiann/KernelSU/tree/0c0fdb960178fa12d80d6cbcfdcb6ad8be6a865d")
            ).forEach { (name, version, url) -> ArrowPreference(name, summary = version, onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) }
        } }
    }
}
