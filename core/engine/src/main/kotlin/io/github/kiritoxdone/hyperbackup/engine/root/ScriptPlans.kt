package io.github.kiritoxdone.hyperbackup.engine.root

import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.engine.ScriptCatalogReader
import io.github.kiritoxdone.hyperbackup.engine.storage.*
import kotlinx.serialization.json.*
import java.io.File

class ScriptPlans(private val assets: RootAssets, private val dex: UpstreamDex, private val connections: Connections) {
    val keys: Set<String> = File(assets.tools, "config-keys.txt").readLines().filter(String::isNotBlank).toSet()
    private val defaults = Wire.json.decodeFromString<Map<String, Map<String, String>>>(File(assets.tools, "config-defaults.json").readText())
    fun catalog(): ScriptCatalog = ScriptCatalogReader.read { name -> File(assets.tools, name).readText() }
    fun check(draft: Draft): Plan {
        PlanPolicy.validate(draft)
        require(draft.operation != Operation.DELETE) { "USE_NATIVE_MAINTENANCE_OPERATION" }
        val spec = requireNotNull(draft.script) { "SCRIPT_PLAN_REQUIRED" }
        Wire.uuid(draft.destinationId)
        require(spec.userId >= 0 && spec.config.keys.none { it.endsWith("_pass") || it.endsWith("_user") }) { "USE_ENCRYPTED_CONNECTION_CREDENTIALS" }
        ScriptConfiguration.encode(spec.config, keys)
        spec.selectedPackages.forEach(Wire::packageName)
        spec.selectedApps.forEach { it.validate() }
        require(spec.selectedApps.distinct().size == spec.selectedApps.size) { "DUPLICATE_TARGET" }
        val blockers = mutableListOf<String>(); val warnings = mutableListOf<String>()
        val destination = connections.destination(draft.destinationId)
        if (spec.command in appRestores && draft.safetyCopy && spec.selectedApps.isEmpty() && spec.selectedPackages.isEmpty()) {
            blockers += "SAFETY_COPY_REQUIRES_APP_SELECTION:请先选择备份版本或填写包名，或明确关闭本次恢复前副本。"
        }
        if (spec.command == ScriptCommand.RESTORE_FOLDERS && draft.safetyCopy) {
            blockers += "文件夹恢复暂不支持恢复前副本。请先备份目标文件夹，再关闭本次恢复前副本。"
        }
        if (spec.setMode == BackupSetMode.UPDATE && !spec.command.restore) {
            val target = Regex("Backup_(zstd|tar)_(\\d+)(.*)").matchEntire(spec.existingSet.substringAfterLast('/'))
            if (target == null || target.groupValues[2].toIntOrNull() != spec.userId) blockers += "SELECT_EXISTING_SET_FOR_THIS_USER"
            warnings += "将更新已有备份集：${spec.existingSet}。fast-skip 只比较此集；本次批次不会保留更新前内容版本。"
        }
        if (spec.workingDirectory.isNotEmpty()) {
            requireStorageDirectory(spec.workingDirectory)
            if (!File(spec.workingDirectory).isDirectory) blockers += "SCRIPT_DIRECTORY_UNAVAILABLE"
        } else if (spec.command.restore && destination.protocol == Protocol.LOCAL) blockers += "CHOOSE_SCRIPT_BACKUP_DIRECTORY"
        if (spec.command == ScriptCommand.BACKUP_UPDATED && spec.setMode != BackupSetMode.UPDATE) blockers += "UPDATED_APPS_REQUIRE_EXISTING_SET"
        val users = draft.items.map { it.app.userId }.distinct()
        require(spec.selectedApps.all { it.userId in users }) { "UNMAPPED_SOURCE_USER" }
        users.forEach { user -> if (dex.users().none { it.id == user && it.unlocked }) blockers += "USER_LOCKED_OR_REMOVED:$user" }
        (spec.selectedApps + draft.items.filter { !it.group && it.app.packageName != SCOPE }.map { it.app }).distinct().forEach { app ->
            if (app.packageName in protectedPackages) blockers += "PROTECTED_EXECUTION_HOST:${app.packageName}"
            if (!spec.command.restore && dex.installed(app) == null) blockers += "APP_NOT_INSTALLED:${app.stableId}"
        }
        if (spec.command in setOf(ScriptCommand.BACKUP_APPS, ScriptCommand.BACKUP_UPDATED)) {
            val effective = defaults.getValue("backup") + spec.config
            val associated = mutableListOf<String>()
            if (spec.associatedWifi) associated += "Wi-Fi 配置"
            if (effective["backup_messages"] in setOf("1", "true")) associated += "SMS / MMS"
            if (effective["backup_calllogs"] in setOf("1", "true")) associated += "通话记录"
            if (effective["backup_media"] in setOf("1", "true")) associated += "自定义目录"
            if (associated.isNotEmpty()) warnings += "按本次配置，还将备份：${associated.joinToString()}。请在配置中核对关联数据。"
        }
        if (spec.command.restore) warnings += "恢复将覆盖所选数据。安装结果不明确时停止；更新失败时可能沿用现有应用恢复数据。"
        if (spec.command == ScriptCommand.REMOTE_RESTORE_STREAM) warnings += "流式恢复直接写入；网络中断可能留下部分数据，不自动回滚。"
        if (spec.command == ScriptCommand.REMOTE_SPEED) warnings += "测速会传输约 5 GiB 测试数据。"
        if (spec.command in setOf(ScriptCommand.EDIT_REMOTE_BACKUPS, ScriptCommand.REMOTE_CLEANUP, ScriptCommand.CONVERT_NAMES, ScriptCommand.REBUILD_METADATA)) warnings += "此维护操作会修改选定备份位置；按脚本的确认选项执行。"
        val repoId = runCatching { connections.open(draft.destinationId).use { NativeRepository(it).identity().repoId } }.getOrElse { NativeRepository.stableId(destination.id) }
        return Plan(draft = draft, repoId = repoId, blockers = blockers, warnings = warnings,
            destinationDigest = connections.digest(draft.destinationId), safetyDestinationDigest = if (draft.safetyCopy && spec.command.restore) connections.digest(draft.safetyDestinationId) else "")
    }
    fun configuration(spec: ScriptSpec, user: Int, destinationId: String, output: File, taskId: String): Map<String, String> {
        val values = defaults.getValue(if (ScriptPreferences.profile(spec.command) == ScriptProfile.RESTORE) "restore" else "backup").toMutableMap()
        values.putAll(spec.config)
        values.putAll(connections.scriptConnection(destinationId))
        values["user"] = user.toString(); values["update"] = "0"; values["background_execution"] = "0"
        values["notification_enable"] = "0"; values.putIfAbsent("pc_auto_shutdown", "0"); values.putIfAbsent("setDisplayPowerMode", "0")
        if (values["Output_path"].isNullOrEmpty()) values["Output_path"] = output.path
        if (spec.setMode == BackupSetMode.NEW) values["Backup_suffix"] = "${values["Backup_suffix"].orEmpty()}_HB_${taskId.replace("-", "")}" else {
            val match = requireNotNull(Regex("Backup_(zstd|tar)_(\\d+)(.*)").matchEntire(spec.existingSet.substringAfterLast('/')))
            values["Compression_method"] = match.groupValues[1]; values["Backup_suffix"] = match.groupValues[3]
            if (spec.existingSet.startsWith('/')) values["Output_path"] = spec.existingSet.substringBeforeLast('/')
        }
        return values
    }
    companion object {
        const val SCOPE = "io.github.kiritoxdone.hyperbackup.scope"
        val hostOwned = ScriptPreferences.runtimeOwned
        val protectedPackages = setOf(Wire.HOST, "com.topjohnwu.magisk", "me.weishu.kernelsu", "com.rifsxd.ksunext", "com.sukisu.ultra", "me.bmax.apatch")
        val appRestores = setOf(ScriptCommand.RESTORE_APPS, ScriptCommand.RESTORE_SSAID_APPS, ScriptCommand.RESTORE_APP_STATE,
            ScriptCommand.REMOTE_RESTORE_STAGED, ScriptCommand.REMOTE_RESTORE_STREAM)
        fun requireStorageDirectory(path: String) {
            require(path.startsWith("/storage/") || path.startsWith("/data/media/") || path.startsWith("/mnt/media_rw/")) { "CHOOSE_STORAGE_DIRECTORY" }
            require(path.split('/').none { it == ".." || it == "." } && '\u0000' !in path)
        }
    }
}
