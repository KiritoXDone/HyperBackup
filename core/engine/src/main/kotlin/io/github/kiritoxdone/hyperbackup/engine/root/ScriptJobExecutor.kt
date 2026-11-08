package io.github.kiritoxdone.hyperbackup.engine.root

import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.engine.storage.*
import java.io.File

class ScriptJobExecutor(
    private val assets: RootAssets, private val dex: UpstreamDex, private val connections: Connections,
    private val plans: ScriptPlans, val runner: ScriptRunner,
) : ItemExecutor {
    private val work = File(assets.root, "native-jobs").also { it.mkdirs() }
    override fun begin(task: TaskSnapshot) {
        val spec = requireNotNull(task.plan.draft.script) { "SCRIPT_PLAN_REQUIRED" }
        require(connections.digest(task.plan.draft.destinationId) == task.plan.destinationDigest) { "DESTINATION_CHANGED" }
        // The upstream global execution lock remains authoritative for its own processes.
        // App-private claims cannot prove exclusion against an unmodified remote script.

    }
    override fun execute(task: TaskSnapshot, index: Int, progress: (String, Boolean) -> Unit): ItemResult {
        val draft = task.plan.draft; val baseSpec = requireNotNull(draft.script); val item = draft.items[index]
        val spec = ScriptPlanScope.forItem(baseSpec, item)
        dex.requireUser(spec.userId)
        val staging = File(work, "${task.id}-$index").also { require(!it.exists()); check(it.mkdir()) }
        val currentPackages = spec.selectedPackages.filter { dex.installed(AppKey(spec.userId, it)) != null }
        var safety: ScriptRunResult? = null
        val safeSpec = if (spec.command.restore && draft.safetyCopy) ScriptSafetyCopy.specification(spec, currentPackages) else null
        if (safeSpec != null) {
            progress("SAFETY_BACKUP", false)
            safety = run(task, safeSpec, draft.safetyDestinationId, File(staging, "safety"), item, progress)
            if (!safe(safety)) throw EngineFailure("SAFETY_COPY_NOT_CONFIRMED", true, safety.exit == 75)
        }
        progress("UPSTREAM_${spec.command.wireName}", false)
        val result = run(task, spec, draft.destinationId, File(staging, "operation"), item, progress)
        val detail = result.events.filter { it[1] == "result" }.map { event ->
            val kind = when (event[3]) { "apk" -> PartKind.APK; "user_de" -> PartKind.DE; "data" -> PartKind.EXTERNAL_DATA; "obb" -> PartKind.OBB; "media" -> PartKind.MEDIA; else -> PartKind.CE }
            PartResult(kind, if (event[4] == "0") Outcome.SUCCEEDED else Outcome.FAILED, "${event[2]} · ${event[3]} · ${event[5]}", spec.command.restore)
        }
        val unknown = result.exit == 75 || result.events.any { it[1] == "unknown" } || !result.cleanupConfirmed
        val outcome = when { unknown -> Outcome.UNKNOWN; result.exit == 130 -> Outcome.CANCELLED; result.exit != 0 -> if (detail.any { it.outcome == Outcome.SUCCEEDED }) Outcome.PARTIAL else Outcome.FAILED
            detail.any { it.outcome == Outcome.FAILED } -> Outcome.PARTIAL; detail.isEmpty() -> Outcome.WARNING; else -> Outcome.SUCCEEDED }
        return ItemResult(item.app, outcome, "UPSTREAM_FINISHED", "引擎退出码 ${result.exit} · 收尾确认 ${result.cleanupConfirmed}" +
            (safety?.backupPath?.let { "\n恢复前副本：$it" } ?: "") + (result.backupPath?.let { "\n原生备份：$it" } ?: ""), detail,
            potentiallyWritten = spec.command.restore, nativeBackupPath = result.backupPath, safetyBackupPath = safety?.backupPath)
    }
    private fun safe(result: ScriptRunResult) = result.exit == 0 && result.cleanupConfirmed && result.events.none { it[1] == "result" && it[4] != "0" || it[1] == "unknown" || it[1] == "archive" && it[5] == "failed" }
    private fun run(task: TaskSnapshot, spec: ScriptSpec, destinationId: String, directory: File, item: PlanItem,
        progress: (String, Boolean) -> Unit): ScriptRunResult {
        check(directory.mkdir())
        val workspace = if (spec.workingDirectory.isEmpty()) File(directory, "workspace").also { it.mkdir() } else File(spec.workingDirectory).canonicalFile
        require(workspace.isDirectory)
        val config = File(if (spec.workingDirectory.isEmpty()) workspace else directory, if (spec.command.restore && !spec.command.wireName.startsWith("remote_")) "restore_settings.conf" else "backup_settings.conf")
        val output = File(assets.root, "local-backups").also { it.mkdirs() }
        val values = plans.configuration(spec, spec.userId, destinationId, output, task.id).toMutableMap()
        if (spec.selectedPackages.isNotEmpty()) {
            if (!spec.command.restore) {
                val selectedSystem = spec.selectedPackages.filter { dex.installed(AppKey(spec.userId, it))?.system == true }
                if (selectedSystem.isNotEmpty()) {
                    values["system"] = (values["system"].orEmpty().lines() + selectedSystem).distinct().joinToString("\n")
                }
            }
            val lines = spec.selectedPackages.map { pkg ->
                require(pkg !in ScriptPlans.protectedPackages)
                val label = if (spec.command.restore) item.scriptDirectory?.substringAfterLast('/') ?: findLabel(workspace, pkg) else pkg
                require(label.isNotEmpty() && label.none { it == '\n' || it == '\r' || it == '\t' || it == '/' })
                "$label $pkg"
            }
            val list = File(config.parentFile, "appList.txt").also { it.writeText(lines.joinToString("\n", postfix = "\n")) }
            values["list_location"] = list.path
        } // An unbound scope uses the upstream explicit selection flow before execution.
        config.writeText(ScriptConfiguration.encode(values, plans.keys))
        android.system.Os.chmod(config.path, 384)
        return runner.execute(task.id, spec, workspace, config, File(directory, "control"), progress)
    }
    private fun findLabel(directory: File, pkg: String): String = File(directory, "appList.txt").readLines().map { it.trim().removePrefix("#").removePrefix("!").trim() }
        .firstOrNull { it.split(Regex("\\s+")).getOrNull(1) == pkg }?.substringBefore(' ') ?: error("PACKAGE_NOT_IN_SOURCE_LIST")
    override fun requestStop(taskId: String) = runner.requestStop(taskId)
    override fun finish(task: TaskSnapshot) {}
}
