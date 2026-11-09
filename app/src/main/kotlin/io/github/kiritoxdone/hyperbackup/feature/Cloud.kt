package io.github.kiritoxdone.hyperbackup.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kiritoxdone.hyperbackup.*
import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.ui.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.preference.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun CloudPage(s: AppState, vm: AppViewModel, padding: PaddingValues, navigate: (Route) -> Unit) {
    val accounts = s.destinations.filter { it.protocol != Protocol.LOCAL }
    PageList(padding) {
        if (accounts.isEmpty()) item { EmptyContent(HyperIcons.Cloud, "还没有云端位置", "添加位置") { navigate(Route.AddCloud) } }
        else item { SectionLabel("账户") }
        items(accounts, key = { it.id }) { account ->
            val status = if (s.connectionState == RootConnectionState.READY) s.connectionTests[account.id] ?: DestinationState.UNCHECKED else DestinationState.UNCHECKED
            SurfaceCard(insideMargin = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile(HyperIcons.Cloud)
                    Column(Modifier.weight(1f)) {
                        Text(account.name, fontWeight = FontWeight.Medium)
                        Text(account.protocol.name, Modifier.padding(top = 3.dp), style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    ActionIcon(MiuixIcons.Edit, "编辑 ${account.name}", !s.busy) { navigate(Route.EditLocation(account.id)) }
                }
                Text(account.address, Modifier.padding(top = 14.dp), style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(when (status) { DestinationState.READY -> "已连接"; DestinationState.CHECKING -> "连接中"; DestinationState.FAILED -> "连接失败"; else -> "未检查" }, status == DestinationState.FAILED, status == DestinationState.READY)
                    Spacer(Modifier.weight(1f))
                    ActionIcon(MiuixIcons.Refresh, "测试 ${account.name}", !s.busy) { vm.testDestination(account.id) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton("备份到此处", { vm.selectDestination(account.id) { navigate(Route.SelectApps) } }, Modifier.weight(1f), enabled = !s.busy,
                        colors = ButtonDefaults.textButtonColorsPrimary())
                    TextButton("恢复", { vm.selectDestination(account.id) { navigate(Route.Library) } }, Modifier.weight(1f), enabled = !s.busy)
                }
            }
        }
        if (accounts.isNotEmpty()) item { PrimaryAction("添加位置", !s.busy) { navigate(Route.AddCloud) } }
    }
}

@Composable fun UsersPage(s: AppState, vm: AppViewModel, padding: PaddingValues) {
    val users = s.capabilities?.users ?: listOf(AndroidUser(s.user, "用户 ${s.user}", true))
    PageList(padding) {
        item { SurfaceCard {
            users.forEach { user -> RadioButtonPreference(user.name, s.user == user.id,
                { if (s.user != user.id) vm.selectUser(user.id) }, summary = "ID ${user.id}" + if (user.unlocked) "" else " · 已锁定",
                enabled = user.unlocked && !s.busy) }
        } }
        if (s.capabilities == null) item { PrimaryAction("连接 Root 并读取用户", !s.busy, vm::connect) }
    }
}
