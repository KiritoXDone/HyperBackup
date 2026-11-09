package io.github.kiritoxdone.hyperbackup.feature

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.kiritoxdone.hyperbackup.*
import io.github.kiritoxdone.hyperbackup.ui.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun HomeStatusCard(s: AppState, connect: () -> Unit, users: () -> Unit, locations: () -> Unit) {
    val dark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val ready = s.connectionState == RootConnectionState.READY
    val failed = s.connectionState == RootConnectionState.UNAVAILABLE
    val background = when { ready -> MiuixTheme.colorScheme.surfaceContainer
        failed -> if (dark) Color(0xFF310808) else Color(0xFFF8E2E2)
        else -> if (dark) Color(0xFF3E2F1B) else Color(0xFFFFF0DB) }
    val accent = when { ready -> if (dark) Color(0xFF72D994) else Color(0xFF17702F)
        failed -> MiuixTheme.colorScheme.error; else -> if (dark) Color(0xFFFFD08A) else Color(0xFF805000) }
    val title = when (s.connectionState) { RootConnectionState.READY -> "Root 已连接"; RootConnectionState.CHECKING -> "正在检查 Root"
        RootConnectionState.UNAVAILABLE -> "Root 连接失效"; else -> "等待 Root 授权" }
    SurfaceCard(colors = CardDefaults.defaultColors(color = background)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(HyperIcons.Shield, color = accent, background = accent.copy(alpha = 0.10f))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Medium)
                Text(s.capabilities?.let { "Android ${it.androidVersion} · 用户 ${s.user}" } ?: if (s.busy) "请稍候" else "用户 ${s.user}",
                    Modifier.padding(top = 3.dp), style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            ActionIcon(HyperIcons.User, "切换用户", !s.busy, if (ready) users else connect)
        }
        val destination = s.destinations.find { it.id == s.destinationId }
        ArrowPreference("备份位置", summary = destination?.address ?: "选择备份位置", onClick = locations)
        if (!ready) TextButton(if (failed) "重新检查" else "授权并检查", connect,
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), enabled = !s.busy)
    }
}
