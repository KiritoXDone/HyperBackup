package io.github.kiritoxdone.hyperbackup.engine.root

import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.engine.storage.*
import kotlinx.serialization.json.*
import java.io.File

class ScriptPlans(private val assets: RootAssets, private val dex: UpstreamDex, private val connections: Connections) {
    val keys: Set<String> = File(assets.tools, "config-keys.txt").readLines().filter(String::isNotBlank).toSet()
    private val defaults = Wire.json.decodeFromString<Map<String, Map<String, String>>>(File(assets.tools, "config-defaults.json").readText())
    fun catalog(): ScriptCatalog {
        val labels = Json.parseToJsonElement(File(assets.tools, "settings-labels.json").readText()).jsonObject["labels"]!!.jsonObject
        val options = Json.parseToJsonElement(File(assets.tools, "settings-options.json").readText()).jsonObject
        return ScriptCatalog(backupDefaults = defaults.getValue("backup"), restoreDefaults = defaults.getValue("restore"), fields = keys.filterNot { it in hostOwned }.map { key ->
            ScriptField(key, labels[key]?.jsonPrimitive?.content ?: key, options[key]?.jsonArray?.map { row -> row.jsonArray.map { it.jsonPrimitive.content } }.orEmpty(), key.endsWith("_pass"))
        })
    }
    fun check(draft: Draft): Plan {
        require(draft.operation != Operation.DELETE) { "USE_NATIVE_MAINTENANCE_OPERATION" }
        val spec = requireNotNull(draft.script) { "SCRIPT_PLAN_REQUIRED" }
        Wire.uuid(draft.destinationId)
        require(spec.userId >= 0 && spec.config.keys.none { it.endsWith("_pass") || it.endsWith("_user") }) { "USE_ENCRYPTED_CONNECTION_CREDENTIALS" }
        ScriptConfiguration.encode(spec.config, keys)
        spec.selectedPackages.forEach(Wire::packageName)
        val blockers = mutableListOf<String>(); val warnings = mutableListOf<String>()
        val destination = connections.destination(draft.destinationId)
        if (spec.setMode == BackupSetMode.UPDATE && !spec.command.restore) {
            val target = Regex("Backup_(zstd|tar)_(\\d+)(.*)").matchEntire(spec.existingSet.substringAfterLast('/'))
            if (target == null || target.groupValues[2].toIntOrNull() != spec.userId) blockers += "SELECT_EXISTING_SET_FOR_THIS_USER"
            warnings += "将更新已有备份集：${spec.existingSet}。fast-skip 只比较此集；本次批次不会保留更新前内容版本。"
        } else warnings += "新建备份集，使用本次唯一后缀；不会对空集跳过必需载荷。"
        if (spec.workingDirectory.isNotEmpty()) {
            requireStorageDirectory(spec.workingDirectory)
            if (!File(spec.workingDirectory).isDirectory) blockers += "SCRIPT_DIRECTORY_UNAVAILABLE"
        } else if (spec.command.restore && destination.protocol == Protocol.LOCAL) blockers += "CHOOSE_SCRIPT_BACKUP_DIRECTORY"
        if (spec.command == ScriptCommand.BACKUP_UPDATED && spec.setMode != BackupSetMode.UPDATE) blockers += "UPDATED_APPS_REQUIRE_EXISTING_SET"
        val users = draft.items.map { it.app.userId }.distinct()
        users.forEach { user -> if (dex.users().none { it.id == user && it.unlocked }) blockers += "USER_LOCKED_OR_REMOVED:$user" }
        draft.items.filter { it.app.packageName != SCOPE }.forEach { item ->
            if (item.app.packageName in protectedPackages) blockers += "PROTECTED_EXECUTION_HOST:${item.app.packageName}"
            if (!spec.command.restore && dex.installed(item.app) == null) blockers += "APP_NOT_INSTALLED:${item.app.stableId}"
        }
        if (spec.command.restore) warnings += "将按固定版脚本恢复；会覆盖所选数据。安装未知结果停止整批；更新明确失败仍可能沿用现有安装恢复数据。"
        if (spec.command == ScriptCommand.REMOTE_RESTORE_STREAM) warnings += "流式恢复直接写入；网络中断可能留下部分数据，不自动回滚。"
        if (spec.command == ScriptCommand.REMOTE_SPEED) warnings += "测速会传输约 5 GiB 测试数据。"
        if (spec.command in setOf(ScriptCommand.REMOTE_CLEANUP, ScriptCommand.CONVERT_NAMES, ScriptCommand.REBUILD_METADATA)) warnings += "此维护操作会修改选定备份位置；按脚本的确认选项执行。"
        warnings += "备份使用 backup_script 原生布局；App 元数据为附加侧车。脚本自更新锁定，升级随 APK 发布。"
        val repoId = runCatching { connections.open(draft.destinationId).use { NativeRepository(it).identity().repoId } }.getOrElse { NativeRepository.stableId(destination.id) }
        return Plan(draft = draft, repoId = repoId, blockers = blockers, warnings = warnings,
            destinationDigest = connections.digest(draft.destinationId), safetyDestinationDigest = if (draft.safetyCopy && spec.command.restore) connections.digest(draft.safetyDestinationId) else "")
    }
    fun configuration(spec: ScriptSpec, user: Int, destinationId: String, output: File, taskId: String): Map<String, String> {
        val values = defaults.getValue(if (spec.command.restore) "restore" else "backup").toMutableMap()
        values.putAll(spec.config)
        values.putAll(connections.scriptConnection(destinationId))
        values["user"] = user.toString(); values["update"] = "0"; values["background_execution"] = "0"
        values["notification_enable"] = "0"; values.putIfAbsent("pc_auto_shutdown", "0"); values.putIfAbsent("setDisplayPowerMode", "0")
        if (values["Output_path"].isNullOrEmpty()) values["Output_path"] = output.path
        if (spec.setMode == BackupSetMode.NEW) values["Backup_suffix"] = "_HB_${taskId.replace("-", "")}" else {
            val match = requireNotNull(Regex("Backup_(zstd|tar)_(\\d+)(.*)").matchEntire(spec.existingSet.substringAfterLast('/')))
            values["Compression_method"] = match.groupValues[1]; values["Backup_suffix"] = match.groupValues[3]
            if (spec.existingSet.startsWith('/')) values["Output_path"] = spec.existingSet.substringBeforeLast('/')
        }
        return values
    }
    companion object {
        const val SCOPE = "io.github.kiritoxdone.hyperbackup.scope"
        val hostOwned = setOf("update", "background_execution", "notification_enable", "rgb_a", "rgb_b", "rgb_c", "cdn")
        val protectedPackages = setOf(Wire.HOST, "com.topjohnwu.magisk", "me.weishu.kernelsu", "com.rifsxd.ksunext", "com.sukisu.ultra", "me.bmax.apatch")
        fun requireStorageDirectory(path: String) {
            require(path.startsWith("/storage/") || path.startsWith("/data/media/") || path.startsWith("/mnt/media_rw/")) { "CHOOSE_STORAGE_DIRECTORY" }
            require(path.split('/').none { it == ".." || it == "." } && '\u0000' !in path)
        }
    }
}
