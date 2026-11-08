package io.github.kiritoxdone.hyperbackup.feature

import android.content.Intent
import android.net.Uri
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

@Composable fun SettingsPage(s: AppState, vm: AppViewModel, padding: PaddingValues, navigate: (Route) -> Unit) {
    PageList(padding) {
        notices(s, vm, navigate)
        item { Card { ArrowPreference("备份位置管理", summary = "本地 / SMB / WebDAV", onClick = { navigate(Route.Locations) }) } }
        item { Card {
            listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (key, label) ->
                RadioButtonPreference(title = label, selected = s.theme == key, onClick = { vm.theme(key) })
            }
        } }
        item { Card {
            ArrowPreference("完整引擎功能", summary = "原生操作、配置、传输与维护", onClick = { navigate(Route.Tools) })
            ArrowPreference("任务与诊断", summary = "查看阶段、分项结果和待核对任务", onClick = { navigate(Route.Tasks) })
            ArrowPreference("关于与开源许可", summary = "HyperBackup ${BuildConfig.VERSION_NAME}", onClick = { navigate(Route.About) })
        } }
        item { InfoCard("备份内容未加密", "连接密码由 Android Keystore 保护，密码不会写入备份清单。备份文件本身没有口令加密；请妥善保管存储位置。") }
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
        item { InfoCard("连接原仓库", "重装应用后添加原路径并选择“连接已有仓库”，即可从清单重建索引。创建新仓库要求目录为空。") }
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
        item { PrimaryAction(if (create) "检查并创建仓库" else "连接并读取清单", !s.busy && s.capabilities != null && name.isNotBlank() && address.isNotBlank()) {
            vm.saveDestination(Connection(Destination(name = name.trim(), protocol = protocol, address = address.trim().trimEnd('/'), username = username, domain = domain, allowHttp = http), password), create, done)
        } }
    }
}
@Composable fun AboutPage(padding: PaddingValues) {
    val context = LocalContext.current
    PageList(padding) {
        item { InfoCard("HyperBackup ${BuildConfig.VERSION_NAME}", "独立 Android 应用\nAndroid 9+ · arm64 · Root\nGPL-3.0-only\nbackup_script v1117 / ${Wire.UPSTREAM.take(12)}\nMiuix 0.9.4 · libsu 6.0.0") }
        item { InfoCard("核验范围", "归档校验不代表免登录或适用于所有 ROM。请先用可丢弃应用验证当前设备。应用不自动卸载目标、不强制降级，不会自动重放结果未知的恢复。") }
        item { TextButton("源码、构建说明与开源许可", { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/KiritoXDone/HyperBackup"))) }) }
    }
}
