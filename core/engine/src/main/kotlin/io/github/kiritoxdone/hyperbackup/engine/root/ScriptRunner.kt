package io.github.kiritoxdone.hyperbackup.engine.root

import io.github.kiritoxdone.hyperbackup.domain.*
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

data class ScriptRunResult(val exit: Int, val events: List<List<String>>, val backupPath: String?, val cleanupConfirmed: Boolean)

/** One process runs the original business engine. The host owns gates, prompts and durable task identity. */
class ScriptRunner(private val assets: RootAssets) {
    private val running = ConcurrentHashMap<String, File>()
    private val pending = ConcurrentHashMap<String, Pair<EnginePrompt, File>>()
    var stopDue: (String) -> Boolean = { false }
    var showPrompt: (String, EnginePrompt?) -> Unit = { _, _ -> }
    var showEditor: (String, EditorSnapshot?) -> Unit = { _, _ -> }
    val editorProxy = NativeEditorProxy()

    fun execute(taskId: String, spec: ScriptSpec, workDirectory: File, config: File, job: File,
        progress: (String, Boolean) -> Unit): ScriptRunResult {
        require(!running.containsKey(taskId))
        check(job.mkdirs()); android.system.Os.chmod(job.path, 448)
        listOf("gates", "prompts", "evidence", "editors").forEach { check(File(job, it).mkdir()) }
        val eventsFile = File(job, "events.tsv").also { it.createNewFile() }
        val builder = ProcessBuilder("/system/bin/sh", File(assets.tools, "entry.sh").path, assets.tools.path, job.path,
            workDirectory.path, config.path, spec.command.wireName)
            .redirectErrorStream(true).redirectOutput(File(job, "engine.log"))
        builder.environment()["HYPERBACKUP_OWNER_PID"] = android.os.Process.myPid().toString()
        builder.environment()["SPEEDBACKUP_NATIVE_EXE"] = assets.binary("speednative")
        builder.environment()["HYPERBACKUP_ASSOCIATED_WIFI"] = if (spec.associatedWifi) "1" else "0"
        builder.environment()["PATH"] = "/system/bin:/system/xbin:/vendor/bin"
        File(config.parentFile, "appList.txt").takeIf { it.isFile }?.let { builder.environment()["HYPERBACKUP_APPLIST"] = it.path }
        // Bootstrap can acquire guards or change execution settings before an item gate.
        progress("UPSTREAM_STARTING", true)
        val process = builder.start(); running[taskId] = job
        editorProxy.allowedPackages[taskId] = spec.selectedPackages.toSet()
        val events = mutableListOf<List<String>>(); var offset = 0L; var editorId: String? = null
        fun observe() {
            if (stopDue(taskId)) requestStop(taskId)
            val descriptor = File(job, "editors").listFiles().orEmpty().firstOrNull { File(it, "endpoint.json").isFile }?.name
            if (descriptor != editorId) {
                val editor = editorProxy.discover(taskId, job); editorId = editor?.id
                if (editor != null && !editor.authSetup && spec.selectedPackages.isNotEmpty() &&
                    !(spec.command.restore && "com.google.android.apps.maps" in spec.selectedPackages)) {
                    val allowed = spec.selectedPackages.toSet()
                    val chosen = editor.rows.filter { it.packageName.ifEmpty { it.id } in allowed }
                    require(chosen.map { it.packageName.ifEmpty { it.id } }.toSet().containsAll(allowed)) { "FROZEN_SELECTION_NOT_IN_UPSTREAM_LIST" }
                    val body = editor.rows.joinToString("\n") { "${it.id}\t${if (it in chosen) 1 else 0}\t0" }
                    progress("UPSTREAM_SELECTION_FROM_PLAN", false)
                    editorProxy.answer(EditorAnswer(taskId, editor.id, "save", body))
                } else showEditor(taskId, editor)
            }
            File(job, "gates").listFiles().orEmpty().filter { File(it, "READY").isFile && !File(it, "ALLOW").exists() }.forEach { gate ->
                val phase = File(gate, "phase").readText().trim()
                val pkg = File(gate, "package").readText().trim()
                require(pkg !in ScriptPlans.protectedPackages) { "PROTECTED_EXECUTION_HOST" }
                require(phase in setOf("installapk", "_restore_app_guard_start", "_backup_entry_begin")) { "UNKNOWN_WRITE_GATE" }
                if (!File(job, "stop").exists()) {
                    progress("UPSTREAM_$phase", phase != "_backup_entry_begin")
                    durable(File(gate, "ALLOW"), "allow\n")
                }
            }
            val promptDir = File(job, "prompts").listFiles().orEmpty().firstOrNull { File(it, "READY").isFile && !File(it, "CONSUMED").exists() && !File(it, "answer").exists() }
            if (promptDir != null && pending[taskId]?.second != promptDir) {
                val prompt = EnginePrompt(taskId, promptDir.name, File(promptDir, "title").readText().take(8192),
                    File(promptDir, "options").readText().split('\n').take(1000), File(promptDir, "cancel").readText(), File(promptDir, "mode").readText())
                pending[taskId] = prompt to promptDir; showPrompt(taskId, prompt)
            } else if (promptDir == null && pending.remove(taskId) != null) showPrompt(taskId, null)
            RandomAccessFile(eventsFile, "r").use { input ->
                input.seek(offset)
                while (true) {
                    val start = input.filePointer; val line = input.readLine() ?: break
                    if (input.filePointer == input.length() && eventsFile.length() > 0) {
                        input.seek(input.length() - 1); if (input.read() != 10) { input.seek(start); break }
                    }
                    val fields = String(line.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8).split('\t')
                    require(fields.size == 6 && fields[0] == "HB1") { "BRIDGE_EVENT_INVALID" }
                    events += fields
                    if (fields[1] == "unknown") progress("INSTALL_COMMIT_UNKNOWN", true)
                    if (fields[1] == "progress") progress("${fields[3]} · ${fields[4]}/${fields[5]}", false)
                    offset = input.filePointer
                }
            }
        }
        try {
            while (process.isAlive) { observe(); Thread.sleep(200) }
            observe()
            return ScriptRunResult(process.exitValue(), events,
                File(job, "backup-path").takeIf { it.isFile }?.readText()?.trim()?.takeIf { it.isNotEmpty() },
                File(job, "cleanup").takeIf { it.isFile }?.readText()?.trim() == "1")
        } catch (e: Exception) {
            requestStop(taskId)
            // Do not kill an in-flight write. Keep the task blocked until the owned process exits.
            while (process.isAlive) Thread.sleep(200)
            throw EngineFailure("SCRIPT_OBSERVER_INTERRUPTED", true, true, e)
        } finally { running.remove(taskId); pending.remove(taskId); editorProxy.allowedPackages.remove(taskId); showPrompt(taskId, null); showEditor(taskId, null); config.delete() }
    }
    fun requestStop(id: String) { running[id]?.let { durable(File(it, "stop"), "stop\n") }; editorProxy.cancel(id) }
    fun answer(answer: PromptAnswer) {
        val (prompt, directory) = pending[answer.taskId] ?: error("PROMPT_NOT_PENDING")
        require(prompt.id == answer.id && answer.answer.length <= 8192 && '\n' !in answer.answer && '\r' !in answer.answer && '\u0000' !in answer.answer)
        if (prompt.mode != "input") {
            val choices = if (prompt.mode == "multi") answer.answer.split(' ').filter(String::isNotEmpty) else listOf(answer.answer)
            require(choices.isNotEmpty() && choices.all { it.toIntOrNull() in 0..prompt.options.size }) { "INVALID_PROMPT_ANSWER" }
        }
        require(!File(directory, "answer").exists()) { "PROMPT_ALREADY_ANSWERED" }
        durable(File(directory, "answer"), answer.answer + "\n")
    }
    private fun durable(file: File, text: String) {
        val temp = File(file.parentFile, file.name + ".pending")
        java.io.FileOutputStream(temp).use { it.write(text.toByteArray()); it.fd.sync() }
        android.system.Os.rename(temp.path, file.path)
    }
}
