package io.github.kiritoxdone.hyperbackup.feature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.kiritoxdone.hyperbackup.*
import io.github.kiritoxdone.hyperbackup.domain.Protocol
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun LibraryLocationCard(s: AppState, vm: AppViewModel, navigate: (Route) -> Unit) {
    val destination = s.destinations.find { it.id == s.destinationId }
    val status = when {
        destination == null -> "未选择"
        s.destinationState == DestinationState.CHECKING -> "连接中"
        s.destinationState == DestinationState.FAILED -> "连接失败"
        s.connectionState != RootConnectionState.READY -> "未连接"
        s.destinationState == DestinationState.READY -> "已连接"
        else -> "未检查"
    }
    val type = when (destination?.protocol) { Protocol.LOCAL -> "本地"; Protocol.SMB -> "SMB"; Protocol.WEBDAV -> "WebDAV"; null -> null }
    Card(Modifier.fillMaxWidth()) {
        OverlaySpinnerPreference(title = destination?.name ?: "备份位置", summary = listOfNotNull(type, status).joinToString(" · "),
            items = s.destinations.map { DropdownItem(it.name) } + DropdownItem("添加位置"),
            selectedIndex = s.destinations.indexOfFirst { it.id == s.destinationId },
            showValue = false, enabled = !s.busy, insideMargin = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            startAction = { LocationSymbol(destination?.protocol) }, onSelectedIndexChange = { index ->
                if (index == s.destinations.size) navigate(Route.AddLocation) else vm.selectDestination(s.destinations[index].id)
            })
    }
}

/** App-owned NAS glyph; Miuix's generic backup glyph represents snapshots instead. */
@Composable private fun LocationSymbol(protocol: Protocol?) {
    val modifier = Modifier.padding(end = 14.dp).size(28.dp)
    val color = MiuixTheme.colorScheme.onSurface
    if (protocol == null || protocol == Protocol.LOCAL) Icon(MiuixIcons.Folder, null, modifier, tint = color)
    else Canvas(modifier) {
        val unit = size.width / 24f
        listOf(3.5f, 13.5f).forEach { top ->
            drawRoundRect(color, Offset(3f * unit, top * unit), Size(18f * unit, 7f * unit), CornerRadius(1.8f * unit), style = Stroke(1.7f * unit))
            drawCircle(color, 0.9f * unit, Offset(7f * unit, (top + 3.5f) * unit))
            drawLine(color, Offset(13f * unit, (top + 3.5f) * unit), Offset(17f * unit, (top + 3.5f) * unit), 1.6f * unit)
        }
    }
}
