package io.github.kiritoxdone.hyperbackup.feature

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.kiritoxdone.hyperbackup.*
import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun AppIcon(app: AppInstance, modifier: Modifier = Modifier.size(44.dp)) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, app.key.packageName) {
        value = withContext(Dispatchers.IO) { runCatching { context.packageManager.getApplicationIcon(app.key.packageName).toBitmap(192, 192) }.getOrNull() }
    }
    bitmap?.let { Image(it.asImageBitmap(), null, modifier) } ?: Box(modifier.clip(RoundedCornerShape(12.dp)).background(MiuixTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) { Text(app.label.take(1).ifEmpty { "A" }, style = MiuixTheme.textStyles.title2) }
}
@Composable fun UserPicker(s: AppState, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        s.capabilities?.users.orEmpty().forEach { user ->
            TextButton("${if (user.id == selected) "✓ " else ""}${user.name} · ${user.id}${if (!user.unlocked) "（锁定）" else ""}", { onSelect(user.id) }, enabled = user.unlocked && !s.busy)
        }
    }
}
