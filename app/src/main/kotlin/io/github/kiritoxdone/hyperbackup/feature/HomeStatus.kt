package io.github.kiritoxdone.hyperbackup.feature

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kiritoxdone.hyperbackup.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** Palette and card treatment follow KernelSU v3.3.1 HomeMiuix / WarningCard (0c0fdb96). */
@Composable fun HomeStatusCard(state: AppState, onCheck: () -> Unit) {
    val dark = MiuixTheme.colorScheme.surface.luminance() < 0.5f
    val failed = state.connectionState == RootConnectionState.UNAVAILABLE
    val ready = state.connectionState == RootConnectionState.READY && state.capabilities != null
    val checking = state.connectionState == RootConnectionState.CHECKING
    val container = when {
        ready -> if (dark) Color(0xFF1A3825) else Color(0xFFDFFAE4)
        failed -> if (dark) Color(0xFF310808) else Color(0xFFF8E2E2)
        else -> if (dark) Color(0xFF3E2F1B) else Color(0xFFFFF0DB)
    }
    val accent = when { ready -> Color(0xFF36D167); failed -> Color(0xFFF72727); else -> Color(0xFFF5A623) }
    val content = when {
        ready -> if (dark) Color(0xFFB8E8C5) else Color(0xFF164A29)
        failed -> if (dark) Color(0xFFFFBABA) else Color(0xFF842424)
        else -> if (dark) Color(0xFFFFDBA1) else Color(0xFF75501B)
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.defaultColors(container, content),
        onClick = onCheck.takeUnless { state.busy }, showIndication = !state.busy, pressFeedbackType = PressFeedbackType.Sink) {
        Box(Modifier.fillMaxWidth()) {
            Canvas(Modifier.matchParentSize()) {
                val radius = 47.dp.toPx()
                val center = Offset(size.width - 13.dp.toPx(), size.height + 1.dp.toPx())
                val stroke = 6.dp.toPx()
                drawCircle(accent.copy(alpha = if (dark) 0.33f else 0.40f), radius, center, style = Stroke(stroke))
                if (ready) {
                    drawLine(accent.copy(alpha = 0.55f), center + Offset(-24.dp.toPx(), -9.dp.toPx()), center + Offset(-9.dp.toPx(), 6.dp.toPx()), stroke, StrokeCap.Round)
                    drawLine(accent.copy(alpha = 0.55f), center + Offset(-9.dp.toPx(), 6.dp.toPx()), center + Offset(23.dp.toPx(), -27.dp.toPx()), stroke, StrokeCap.Round)
                } else {
                    drawLine(accent.copy(alpha = 0.60f), center + Offset(0f, -28.dp.toPx()), center + Offset(0f, -7.dp.toPx()), stroke, StrokeCap.Round)
                    drawCircle(accent.copy(alpha = 0.60f), stroke / 2, center + Offset(0f, 7.dp.toPx()))
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp)) {
                Text(when { checking -> "正在检查 Root"; ready -> "Root 已连接"; failed -> "Root 连接失效"; else -> "等待 Root 授权" },
                    fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = content)
                if (ready) state.capabilities?.let { capabilities ->
                    Text("Android ${capabilities.androidVersion} · ${capabilities.abi}",
                        Modifier.padding(top = 5.dp, end = 35.dp), fontSize = 15.sp, color = content.copy(alpha = 0.88f))
                }
                Text(when { checking -> "请稍候"; ready -> "点击重新检查  ›"; failed -> "重新检查  ›"; else -> "授权并检查  ›" },
                    Modifier.padding(top = 17.dp), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = content)
            }
        }
    }
}
