package io.github.kiritoxdone.hyperbackup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable fun SurfaceCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null, insideMargin: PaddingValues = PaddingValues(0.dp),
    colors: CardColors = CardDefaults.defaultColors(), showIndication: Boolean = onClick != null,
    pressFeedbackType: PressFeedbackType = PressFeedbackType.Sink, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), onClick = onClick, onLongPress = onLongPress, colors = colors,
        cornerRadius = 16.dp, insideMargin = insideMargin, showIndication = showIndication,
        pressFeedbackType = pressFeedbackType, content = content)
}

@Composable fun Segments(items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val textHeight = with(LocalDensity.current) { MiuixTheme.textStyles.button.fontSize.toDp() * 1.6f }
    TabRowWithContour(tabs = items, selectedTabIndex = selected, onTabSelected = onSelect,
        modifier = modifier.fillMaxWidth(), height = textHeight.coerceAtLeast(38.dp), cornerRadius = 11.dp, itemSpacing = 0.dp)
}

@Composable fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, color: Color = MiuixTheme.colorScheme.primary,
    background: Color = MiuixTheme.colorScheme.tertiaryContainer) {
    Box(modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(24.dp), tint = color)
    }
}

@Composable fun Metric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value, style = MiuixTheme.textStyles.title4, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable fun StatusBadge(text: String, error: Boolean = false, success: Boolean = false) {
    val color = if (error) MiuixTheme.colorScheme.error else if (success) if (MiuixTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF72D994) else Color(0xFF17702F) else MiuixTheme.colorScheme.primary
    Text(text, Modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.10f)).padding(horizontal = 9.dp, vertical = 4.dp),
        style = MiuixTheme.textStyles.footnote1, color = color, maxLines = 1)
}

@Composable fun GroupRow(first: Boolean, last: Boolean, onClick: (() -> Unit)?, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(topStart = if (first) 16.dp else 0.dp, topEnd = if (first) 16.dp else 0.dp,
        bottomStart = if (last) 16.dp else 0.dp, bottomEnd = if (last) 16.dp else 0.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(MiuixTheme.colorScheme.surfaceContainer).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 16.dp, vertical = 10.dp).heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), content = content)
}
