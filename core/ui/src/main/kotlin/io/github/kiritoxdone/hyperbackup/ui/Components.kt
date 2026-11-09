package io.github.kiritoxdone.hyperbackup.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.*
import io.github.kiritoxdone.hyperbackup.domain.*
import java.text.DateFormat
import java.util.Date

@Composable fun HyperTheme(mode: String, dynamic: Boolean = false, content: @Composable () -> Unit) {
    val controller = remember(mode, dynamic) { ThemeController(
        when { dynamic && mode == "light" -> ColorSchemeMode.MonetLight; dynamic && mode == "dark" -> ColorSchemeMode.MonetDark
            dynamic -> ColorSchemeMode.MonetSystem; mode == "light" -> ColorSchemeMode.Light; mode == "dark" -> ColorSchemeMode.Dark; else -> ColorSchemeMode.System },
        lightColors = lightColorScheme(primary = Color(0xFF2F6FEB), primaryVariant = Color(0xFF2F6FEB),
            primaryContainer = Color(0xFF2F6FEB), onPrimaryContainer = Color.White,
            tertiaryContainer = Color(0xFFE8F0FE), onTertiaryContainer = Color(0xFF1F56C4),
            secondaryVariant = Color(0xFFEEF0F3), secondaryContainer = Color(0xFFE9EBEE),
            onSurfaceVariantSummary = Color(0xFF666B72), onSurfaceVariantActions = Color(0xFF9AA0A6),
            dividerLine = Color(0xFFECEDEF)),
        darkColors = darkColorScheme(primary = Color(0xFF85AEFF), onPrimary = Color(0xFF10264B), primaryVariant = Color(0xFF85AEFF))) }
    MiuixTheme(controller = controller, content = content)
}
@Composable fun PageList(padding: PaddingValues, spacing: androidx.compose.ui.unit.Dp = 12.dp, content: LazyListScope.() -> Unit) {
    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val extra = ((maxWidth - 720.dp) / 2).coerceAtLeast(0.dp)
        LazyColumn(Modifier.fillMaxSize().consumeWindowInsets(padding),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(direction) + extra + 12.dp,
                end = padding.calculateEndPadding(direction) + extra + 12.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(spacing), content = content)
    }
}
@Composable fun SectionLabel(text: String) {
    Text(text, Modifier.padding(start = 16.dp, top = 10.dp, bottom = 0.dp),
        style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}
@Composable fun InfoCard(title: String, description: String, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), cornerRadius = 16.dp, insideMargin = PaddingValues(16.dp)) {
        Text(title, style = MiuixTheme.textStyles.headline1)
        if (description.isNotEmpty()) Text(description, Modifier.padding(top = 4.dp), style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}
@Composable fun Search(value: String, onChange: (String) -> Unit, placeholder: String) {
    TextField(value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
        label = placeholder, singleLine = true, useLabelAsPlaceholder = true, cornerRadius = 16.dp,
        insideMargin = androidx.compose.ui.unit.DpSize(14.dp, 12.dp),
        colors = TextFieldDefaults.textFieldColors(backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
            labelColor = MiuixTheme.colorScheme.onSurfaceVariantSummary),
        leadingIcon = { Icon(HyperIcons.Search, null, Modifier.size(22.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantActions) })
}
@Composable fun InlineAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(text, onClick, enabled = enabled, minHeight = 48.dp,
        colors = ButtonDefaults.textButtonColors(color = Color.Transparent, disabledColor = Color.Transparent,
            textColor = MiuixTheme.colorScheme.primary, disabledTextColor = MiuixTheme.colorScheme.disabledOnSecondaryVariant))
}
@Composable fun EmptyContent(icon: ImageVector, title: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(top = 68.dp, bottom = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(48.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.45f))
        Text(title, Modifier.padding(top = 16.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        if (action != null) InlineAction(action, onClick = onAction)
    }
}
@Composable fun LeadingIcon(icon: ImageVector) {
    Icon(icon, null, Modifier.padding(end = 12.dp).size(22.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantActions)
}
@Composable fun ActionIcon(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(icon, label, Modifier.size(22.dp))
    }
}
@Composable fun PrimaryAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(text, onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled,
        colors = ButtonDefaults.textButtonColorsPrimary())
}
fun date(timestamp: Long): String = if (timestamp <= 0) "时间未知" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
fun size(bytes: Long): String = when { bytes < 1024 -> "$bytes B"; bytes < 1024 * 1024 -> "%.1f KiB".format(bytes / 1024.0); bytes < 1024L * 1024 * 1024 -> "%.1f MiB".format(bytes / (1024.0 * 1024)); else -> "%.2f GiB".format(bytes / (1024.0 * 1024 * 1024)) }
fun PartKind.label(): String = when (this) { PartKind.APK -> "安装包"; PartKind.CE -> "私有数据（CE）"; PartKind.DE -> "设备加密数据（DE）"; PartKind.EXTERNAL_DATA -> "外部应用数据"; PartKind.OBB -> "OBB"; PartKind.MEDIA -> "应用媒体"; PartKind.APP_STATE -> "应用状态" }
fun Outcome.label(): String = when (this) { Outcome.PENDING -> "待处理"; Outcome.RUNNING -> "处理中"; Outcome.SUCCEEDED -> "已完成"; Outcome.WARNING -> "有警告完成"; Outcome.PARTIAL -> "部分完成"; Outcome.FAILED -> "失败"; Outcome.SKIPPED -> "跳过"; Outcome.CANCELLED -> "已停止 / 未处理"; Outcome.UNKNOWN -> "结果待确认" }
fun TaskState.label(): String = when (this) { TaskState.RUNNING -> "正在执行"; TaskState.STOP_REQUESTED -> "正在停止，等待当前步骤收尾"; TaskState.SUCCEEDED -> "已完成"; TaskState.PARTIAL -> "部分完成"; TaskState.FAILED -> "未完成"; TaskState.CANCELLED -> "已停止"; TaskState.NEEDS_RECONCILIATION -> "结果待核对" }
fun Operation.label(): String = when (this) { Operation.BACKUP -> "备份"; Operation.RESTORE -> "恢复"; Operation.DELETE -> "删除"; Operation.VERIFY -> "校验" }
