package io.github.kiritoxdone.hyperbackup.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.kiritoxdone.hyperbackup.*
import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.ui.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Edit
import top.yukonga.miuix.kmp.preference.*

@Composable fun LocationsPage(s: AppState, vm: AppViewModel, padding: PaddingValues, navigate: (Route) -> Unit) {
    PageList(padding) {
        if (s.destinations.isEmpty()) item { EmptyContent(HyperIcons.Folder, "还没有备份位置") }
        items(s.destinations, key = { it.id }) { destination -> SurfaceCard {
            RadioButtonPreference(destination.name, s.destinationId == destination.id, { vm.selectDestination(destination.id) },
                summary = destination.address, enabled = !s.busy)
            TextButton("编辑位置", { navigate(Route.EditLocation(destination.id)) }, Modifier.fillMaxWidth(), enabled = !s.busy)
        } }
        item { PrimaryAction("添加位置", !s.busy) { navigate(Route.AddLocation) } }
    }
}

@Composable fun AddLocationPage(s: AppState, vm: AppViewModel, padding: PaddingValues, done: () -> Unit,
    remoteOnly: Boolean = false, editId: String? = null) {
    val existing = s.destinations.find { it.id == editId }
    var protocol by rememberSaveable { mutableStateOf(existing?.protocol ?: if (remoteOnly) Protocol.WEBDAV else Protocol.LOCAL) }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: if (remoteOnly) "云端备份" else "手机备份") }
    var address by rememberSaveable { mutableStateOf(existing?.address ?: if (remoteOnly) "https://" else "/storage/emulated/0/HyperBackup") }
    var username by rememberSaveable { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var domain by rememberSaveable { mutableStateOf(existing?.domain.orEmpty()) }
    var http by rememberSaveable { mutableStateOf(existing?.allowHttp ?: false) }
    var create by rememberSaveable { mutableStateOf(false) }
    var secretLoaded by remember { mutableStateOf(editId == null) }
    var tested by remember { mutableStateOf(false) }
    LaunchedEffect(editId) { if (editId != null) runCatching { vm.connectionForEdit(editId) }
        .onSuccess { password = it.password; secretLoaded = true }.onFailure { vm.clearError(it.message) } }
    val types = if (remoteOnly) listOf(Protocol.WEBDAV, Protocol.SMB) else Protocol.entries
    fun connection(): Connection = Connection(Destination(id = existing?.id ?: Wire.id(), name = name.trim(), protocol = protocol,
        address = address.trim().trimEnd('/'), username = username, domain = domain, allowHttp = http,
        repoId = existing?.repoId?.takeIf { existing.address == address.trim().trimEnd('/') && existing.protocol == protocol }), password)
    val enabled = !s.busy && secretLoaded && name.isNotBlank() && address.isNotBlank()
    PageList(padding) {
        item { SectionLabel("协议") }
        item { Segments(types.map { if (it == Protocol.LOCAL) "本地" else it.name }, types.indexOf(protocol).coerceAtLeast(0), { index ->
            protocol = types[index]; tested = false
            address = when (protocol) { Protocol.LOCAL -> "/storage/emulated/0/HyperBackup"; Protocol.SMB -> "smb://"; Protocol.WEBDAV -> "https://" }
        }) }
        item { SectionLabel("连接") }
        item { SurfaceCard(insideMargin = PaddingValues(16.dp)) {
            TextField(name, { name = it }, Modifier.fillMaxWidth(), label = "名称", singleLine = true, colors = flatFieldColors(), insideMargin = DpSize(0.dp, 12.dp))
            Spacer(Modifier.height(4.dp))
            TextField(address, { address = it; tested = false }, Modifier.fillMaxWidth(), label = if (protocol == Protocol.LOCAL) "文件夹路径" else "服务器地址与路径", singleLine = true, colors = flatFieldColors(), insideMargin = DpSize(0.dp, 12.dp))
            if (protocol != Protocol.LOCAL) {
                Spacer(Modifier.height(4.dp))
                TextField(username, { username = it; tested = false }, Modifier.fillMaxWidth(), label = "用户名", singleLine = true, colors = flatFieldColors(), insideMargin = DpSize(0.dp, 12.dp))
                Spacer(Modifier.height(4.dp))
                TextField(password, { password = it; tested = false }, Modifier.fillMaxWidth(), label = "密码", singleLine = true, visualTransformation = PasswordVisualTransformation(), colors = flatFieldColors(), insideMargin = DpSize(0.dp, 12.dp))
            }
            if (protocol == Protocol.SMB) {
                Spacer(Modifier.height(4.dp))
                TextField(domain, { domain = it; tested = false }, Modifier.fillMaxWidth(), label = "域", singleLine = true, colors = flatFieldColors(), insideMargin = DpSize(0.dp, 12.dp))
            }
        } }
        item { SectionLabel("选项") }
        item { SurfaceCard {
            if (protocol == Protocol.WEBDAV) SwitchPreference(http, { http = it; tested = false }, "允许 HTTP", summary = "未加密连接")
            if (existing == null) SwitchPreference(create, { create = it }, "创建新仓库", summary = "需要空目录")
        } }
        if (tested) item { StatusBadge("连接正常") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("测试", { vm.testConnection(connection()) { tested = true } }, Modifier.weight(1f), enabled = enabled && !create, minHeight = 48.dp)
            TextButton("保存", { vm.saveDestination(connection(), create, done) }, Modifier.weight(1f), enabled = enabled,
                minHeight = 48.dp, colors = ButtonDefaults.textButtonColorsPrimary())
        } }
    }
}

@Composable private fun flatFieldColors() = TextFieldDefaults.textFieldColors(backgroundColor = Color.Transparent, labelColor = MiuixTheme.colorScheme.onSurfaceVariantSummary)
