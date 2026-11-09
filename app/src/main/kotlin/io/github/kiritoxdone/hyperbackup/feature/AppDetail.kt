package io.github.kiritoxdone.hyperbackup.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.kiritoxdone.hyperbackup.*
import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.ui.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.*
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable fun AppDetailPage(s: AppState, vm: AppViewModel, padding: PaddingValues, key: AppKey, navigate: (Route) -> Unit) {
    val app = s.apps.find { it.key == key }
    val fields = s.scriptCatalog?.fields.orEmpty().filter { it.key in setOf("Backup_Mode", "Backup_user_data", "Backup_obb_data") }
    var changes by remember(key) { mutableStateOf<Map<String, String>>(emptyMap()) }
    val effective = s.scriptCatalog?.let { ScriptPreferences.effective(it, s.enginePreferences, ScriptProfile.BACKUP) }.orEmpty() + changes
    PageList(padding) {
        if (app == null) item { EmptyContent(HyperIcons.Backup, "应用信息不可用") } else {
            item { Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AppIcon(app, Modifier.size(64.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("${key.packageName} · ${app.versionName}", Modifier.padding(top = 4.dp), style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            } }
            item { SectionLabel("备份内容") }
            item { SurfaceCard {
                fields.forEach { field ->
                    OverlaySpinnerPreference(title = settingLabel(field), items = field.options.map { option -> DropdownItem(settingOption(option.getOrElse(1) { option[0] })) },
                        selectedIndex = field.options.indexOfFirst { it[0] == effective[field.key] }.coerceAtLeast(0),
                        onSelectedIndexChange = { changes = changes + (field.key to field.options[it][0]) })
                }
            } }
            item { SectionLabel("标签") }
            item { SurfaceCard {
                s.behavior.labels.forEach { (label, members) ->
                    SwitchPreference(key.stableId in members, { vm.toggleLabel(label, key) }, label)
                }
                ArrowPreference("管理标签", onClick = { navigate(Route.Labels) })
                ArrowPreference("加入黑名单", onClick = {
                    val raw = effective["blacklist"].orEmpty()
                    val next = if (key.packageName in blacklistPackages(raw)) raw else (raw.trimEnd() + "\n" + key.packageName).trim()
                    vm.saveDefault(null, "blacklist", next)
                })
            } }
            item { SectionLabel("应用信息") }
            item { SurfaceCard {
                BasicComponent(title = "用户", summary = key.userId.toString())
                BasicComponent(title = "安装包", summary = "${app.apkPaths.size} 个 APK")
            } }
            val history = s.repository?.records.orEmpty().filter { it.app.key == key }
            if (history.isNotEmpty()) item { SectionLabel("历史备份") }
            items(history, key = { it.recordId }) { record -> SurfaceCard {
                ArrowPreference(date(record.createdAt), summary = record.origin.label, onClick = { navigate(Route.Record(record.recordId)) })
            } }
            item { PrimaryAction("备份此应用", !s.busy) {
                vm.beginAppBackup(key, changes)
                if (vm.state.value.error == null && vm.state.value.draft != null) navigate(Route.Config)
            } }
        }
    }
}
