package io.github.kiritoxdone.hyperbackup.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.*
import io.github.kiritoxdone.hyperbackup.domain.*
import java.text.DateFormat
import java.util.Date

@Composable fun HyperTheme(mode: String, content: @Composable () -> Unit) {
    val controller = remember(mode) { ThemeController(when (mode) { "light" -> ColorSchemeMode.Light; "dark" -> ColorSchemeMode.Dark; else -> ColorSchemeMode.System }) }
    MiuixTheme(controller = controller, content = content)
}
@Composable fun PageList(padding: PaddingValues, content: LazyListScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp,
        top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
@Composable fun InfoCard(title: String, description: String, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
        Text(title, style = MiuixTheme.textStyles.title3)
        if (description.isNotEmpty()) Text(description, Modifier.padding(top = 8.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}
@Composable fun StatusCard(title: String, description: String, warning: Boolean = false, success: Boolean = false) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val color = when { warning -> if (dark) Color(0xFF493823) else Color(0xFFFFEED5); success -> if (dark) Color(0xFF203F2C) else Color(0xFFDFF9E6); else -> MiuixTheme.colorScheme.secondaryContainer }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.defaultColors(color = color), insideMargin = PaddingValues(22.dp)) {
        Text(title, style = MiuixTheme.textStyles.title2)
        Text(description, Modifier.padding(top = 8.dp), color = MiuixTheme.colorScheme.onSurface)
    }
}
@Composable fun Search(value: String, onChange: (String) -> Unit, placeholder: String) {
    SearchBar(inputField = { TextField(value, onChange, Modifier.fillMaxWidth(), label = placeholder, useLabelAsPlaceholder = true, singleLine = true) },
        onExpandedChange = {}, insideMargin = androidx.compose.ui.unit.DpSize(0.dp, 0.dp)) {}
}
@Composable fun PrimaryAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(text, onClick, Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = enabled,
        colors = ButtonDefaults.textButtonColorsPrimary())
}
fun date(timestamp: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))
fun size(bytes: Long): String = when { bytes < 1024 -> "$bytes B"; bytes < 1024 * 1024 -> "%.1f KiB".format(bytes / 1024.0); bytes < 1024L * 1024 * 1024 -> "%.1f MiB".format(bytes / (1024.0 * 1024)); else -> "%.2f GiB".format(bytes / (1024.0 * 1024 * 1024)) }
fun PartKind.label(): String = when (this) { PartKind.APK -> "安装包"; PartKind.CE -> "私有数据（CE）"; PartKind.DE -> "设备加密数据（DE）"; PartKind.EXTERNAL_DATA -> "外部应用数据"; PartKind.OBB -> "OBB"; PartKind.MEDIA -> "应用媒体"; PartKind.APP_STATE -> "应用状态" }
fun Outcome.label(): String = when (this) { Outcome.PENDING -> "待处理"; Outcome.RUNNING -> "处理中"; Outcome.SUCCEEDED -> "已完成"; Outcome.WARNING -> "有警告完成"; Outcome.PARTIAL -> "部分完成"; Outcome.FAILED -> "失败"; Outcome.SKIPPED -> "跳过"; Outcome.CANCELLED -> "已停止 / 未处理"; Outcome.UNKNOWN -> "结果待确认" }
fun TaskState.label(): String = when (this) { TaskState.RUNNING -> "正在执行"; TaskState.STOP_REQUESTED -> "正在停止，等待当前步骤收尾"; TaskState.SUCCEEDED -> "已完成"; TaskState.PARTIAL -> "部分完成"; TaskState.FAILED -> "未完成"; TaskState.CANCELLED -> "已停止"; TaskState.NEEDS_RECONCILIATION -> "结果待核对" }
fun Operation.label(): String = when (this) { Operation.BACKUP -> "备份"; Operation.RESTORE -> "恢复"; Operation.DELETE -> "删除"; Operation.VERIFY -> "校验" }
