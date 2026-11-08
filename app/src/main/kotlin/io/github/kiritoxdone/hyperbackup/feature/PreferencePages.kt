package io.github.kiritoxdone.hyperbackup.feature

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kiritoxdone.hyperbackup.*
import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.ui.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.preference.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.*
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable fun EngineSettingsPage(s: AppState, vm: AppViewModel, padding: PaddingValues, group: SettingsGroup, navigate: (Route) -> Unit) {
    val catalog = s.scriptCatalog ?: return
    val profile = group.profile ?: ScriptProfile.BACKUP
    val values = ScriptPreferences.effective(catalog, s.enginePreferences, profile)
    val fields = catalog.fields.filter { ScriptPreferences.editable(it.key) && settingsGroup(it.key) == group }
    PageList(padding) {
        if (group == SettingsGroup.BACKUP) item { Card(Modifier.fillMaxWidth()) {
            SwitchPreference(s.enginePreferences.associatedWifi, { value -> vm.changeEnginePreferences { it.copy(associatedWifi = value) } }, "关联 Wi-Fi")
        } }
        if (group == SettingsGroup.RESTORE) item { Card(Modifier.fillMaxWidth()) {
            SwitchPreference(s.enginePreferences.safetyCopy, { value -> vm.changeEnginePreferences { it.copy(safetyCopy = value) } }, "恢复前副本")
        } }
        item { Card(Modifier.fillMaxWidth()) {
            fields.forEach { field ->
                val value = values[field.key].orEmpty()
                val enabled = !s.busy && !(values["Backup_Mode"] == "0" && field.key in setOf("Backup_user_data", "Backup_obb_data")) &&
                    !(values["remote_stream"] == "1" && field.key in setOf("remote_keep_local", "remote_upload_per_app"))
                if (field.options.isNotEmpty()) OverlaySpinnerPreference(title = settingLabel(field),
                    items = field.options.map { option -> DropdownItem(settingOption(option.getOrElse(1) { option[0] })) },
                    selectedIndex = field.options.indexOfFirst { it.firstOrNull() == value }.coerceAtLeast(0), enabled = enabled,
                    onSelectedIndexChange = { vm.saveDefault(group.profile, field.key, field.options[it][0]) })
                else ArrowPreference(settingLabel(field), summary = fieldSummary(field, value),
                    onClick = { navigate(Route.DefaultField(field.key, group.profile)) })
            }
        } }
    }
}

@Composable fun DefaultFieldPage(s: AppState, vm: AppViewModel, padding: PaddingValues, key: String, profile: ScriptProfile?, back: () -> Unit) {
    val catalog = s.scriptCatalog ?: return
    val field = catalog.fields.find { it.key == key } ?: return
    val original = ScriptPreferences.effective(catalog, s.enginePreferences, profile ?: ScriptProfile.BACKUP)[key].orEmpty()
    var value by rememberSaveable(key, profile) { mutableStateOf(original) }
    PageList(padding) {
        item { TextField(value, { value = it }, Modifier.fillMaxWidth(), label = settingLabel(field),
            minLines = if (key in setOf("Custom_path", "blacklist", "whitelist", "system")) 4 else 1,
            singleLine = key !in setOf("Custom_path", "blacklist", "whitelist", "system")) }
        item { PrimaryAction("保存", !s.busy) { vm.saveDefault(profile, key, value, back) } }
    }
}

@Composable fun AppBehaviorPage(s: AppState, vm: AppViewModel, padding: PaddingValues) {
    PageList(padding) {
        item { SectionLabel("应用列表") }
        item { Card(Modifier.fillMaxWidth()) {
            SwitchPreference(s.behavior.showPackageNames, { value -> vm.changeBehavior { it.copy(showPackageNames = value) } }, "显示包名")
            SwitchPreference(s.behavior.showSystemApps, { value -> vm.changeBehavior { it.copy(showSystemApps = value) } }, "显示系统应用")
            OverlaySpinnerPreference(title = "默认排序", items = listOf(DropdownItem("名称"), DropdownItem("包名")),
                selectedIndex = s.behavior.appSort.ordinal, onSelectedIndexChange = { index -> vm.changeBehavior { it.copy(appSort = AppSort.entries[index]) } })
        } }
        item { SectionLabel("任务") }
        item { Card(Modifier.fillMaxWidth()) {
            SwitchPreference(s.behavior.connectOnLaunch, { value -> vm.changeBehavior { it.copy(connectOnLaunch = value) } }, "启动时检查 Root")
            SwitchPreference(s.behavior.keepScreenOnDuringTask, { value -> vm.changeBehavior { it.copy(keepScreenOnDuringTask = value) } }, "任务期间保持亮屏")
            SwitchPreference(s.behavior.completionNotification, { value -> vm.changeBehavior { it.copy(completionNotification = value) } }, "完成通知")
        } }
    }
}

@Composable fun SettingsTransferPage(s: AppState, vm: AppViewModel, padding: PaddingValues) {
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportSettings) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::importSettings) }
    var reset by rememberSaveable { mutableStateOf(false) }
    PageList(padding) {
        item { Card(Modifier.fillMaxWidth()) {
            ArrowPreference("导出配置", startAction = { LeadingIcon(MiuixIcons.Send) }, onClick = { export.launch("HyperBackup-settings.json") }, enabled = !s.busy)
            ArrowPreference("导入配置", startAction = { LeadingIcon(MiuixIcons.Import) }, onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }, enabled = !s.busy)
            ArrowPreference("恢复默认设置", startAction = { LeadingIcon(MiuixIcons.Reset) }, onClick = { reset = true }, enabled = !s.busy)
        } }
    }
    WindowDialog(show = reset, title = "恢复备份与恢复的默认设置？", onDismissRequest = { reset = false }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("取消", { reset = false }, Modifier.weight(1f))
            TextButton("恢复默认", { vm.resetEnginePreferences(); reset = false }, Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
        }
    }
}
