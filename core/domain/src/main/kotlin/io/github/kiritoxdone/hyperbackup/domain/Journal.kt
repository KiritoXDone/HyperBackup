package io.github.kiritoxdone.hyperbackup.domain

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

interface TaskJournal {
    fun read(id: String): TaskSnapshot?
    fun write(task: TaskSnapshot)
    fun all(): List<TaskSnapshot>
}

class FileTaskJournal(private val directory: File, private val syncDirectory: (File) -> Unit = { folder ->
    java.nio.channels.FileChannel.open(folder.toPath(), java.nio.file.StandardOpenOption.READ).use { it.force(true) }
}) : TaskJournal {
    init { check(directory.isDirectory || directory.mkdirs()) }
    @Synchronized override fun read(id: String): TaskSnapshot? {
        Wire.uuid(id)
        return File(directory, "$id.json").takeIf { it.isFile }?.readText()?.let { Wire.json.decodeFromString<TaskSnapshot>(it) }
    }
    @Synchronized override fun write(task: TaskSnapshot) {
        Wire.uuid(task.id)
        val target = File(directory, "${task.id}.json")
        val temp = File(directory, "${task.id}.pending")
        FileOutputStream(temp).use { stream ->
            stream.write(Wire.json.encodeToString(TaskSnapshot.serializer(), task).toByteArray()); stream.fd.sync()
        }
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        syncDirectory(directory)
    }
    @Synchronized override fun all(): List<TaskSnapshot> = directory.listFiles().orEmpty()
        .filter { it.extension == "json" }.map { Wire.json.decodeFromString<TaskSnapshot>(it.readText()) }.sortedByDescending { it.startedAt }
}

interface ItemExecutor {
    fun begin(task: TaskSnapshot)
    fun execute(task: TaskSnapshot, index: Int, progress: (String, Boolean) -> Unit): ItemResult
    fun finish(task: TaskSnapshot)
    fun requestStop(taskId: String) {}
}

/** The sole execution owner. UI disconnects never own or cancel its worker. */
class JobCoordinator(
    private val journal: TaskJournal, private val executor: ItemExecutor,
    private val bootId: String, private val sessionId: String,
    private val worker: ExecutorService = Executors.newSingleThreadExecutor(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val leaseMillis: Long = 120_000,
) {
    private var active: String? = null
    private val stop = AtomicBoolean(false)
    @Volatile private var leaseAt = clock()

    init {
        // This constructor runs once per daemon identity. No old plan is ever replayed.
        journal.all().filter { it.state in setOf(TaskState.RUNNING, TaskState.STOP_REQUESTED) }.forEach { old ->
            journal.write(old.copy(state = TaskState.NEEDS_RECONCILIATION, error = "EXECUTOR_IDENTITY_CHANGED",
                items = old.items.map { item -> when (item.outcome) {
                    Outcome.RUNNING -> item.copy(outcome = Outcome.UNKNOWN, reason = "INTERRUPTED_CHECK_REQUIRED")
                    Outcome.PENDING -> item.copy(outcome = Outcome.CANCELLED, reason = "NOT_STARTED")
                    else -> item
                } }))
        }
    }

    @Synchronized fun submit(request: Submit, plan: Plan): TaskSnapshot {
        Wire.uuid(request.taskId); Wire.uuid(request.requestId)
        journal.read(request.taskId)?.let {
            require(it.digest == request.digest && it.requestId == request.requestId && it.plan.id == request.planId) { "IDEMPOTENCY_CONFLICT" }
            return it
        }
        journal.all().firstOrNull { it.requestId == request.requestId }?.let { error("REQUEST_ALREADY_USED:${it.id}") }
        check(active == null) { "TASK_BUSY" }
        check(journal.all().none { it.state in setOf(TaskState.RUNNING, TaskState.STOP_REQUESTED, TaskState.NEEDS_RECONCILIATION) }) { "RECONCILIATION_REQUIRED" }
        PlanPolicy.ready(plan, clock())
        require(request.planId == plan.id && request.digest == plan.digest()) { "PLAN_DIGEST_MISMATCH" }
        val task = TaskSnapshot(id = request.taskId, requestId = request.requestId, plan = plan, digest = request.digest,
            bootId = bootId, sessionId = sessionId, items = plan.draft.items.map { ItemResult(it.app, Outcome.PENDING) })
        journal.write(task) // Durable acceptance precedes execution and the response.
        active = task.id; stop.set(false); leaseAt = clock()
        worker.execute { run(task.id) }
        return task
    }

    @Synchronized fun requestStop(id: String): TaskSnapshot {
        val task = requireNotNull(journal.read(id))
        if (active == id) {
            stop.set(true)
            journal.write(task.copy(state = TaskState.STOP_REQUESTED))
            executor.requestStop(id)
        }
        return requireNotNull(journal.read(id))
    }
    fun heartbeat(id: String) { if (active == id) leaseAt = clock() }
    fun stopDue(id: String): Boolean = active == id && (stop.get() || clock() - leaseAt > leaseMillis)
    @Synchronized fun prompt(id: String, prompt: EnginePrompt?) { journal.write(status(id).copy(prompt = prompt)) }
    @Synchronized fun editor(id: String, editor: EditorSnapshot?) { journal.write(status(id).copy(editor = editor)) }
    fun status(id: String): TaskSnapshot = journal.read(id) ?: throw EngineFailure("TASK_NOT_FOUND")
    fun history(): List<TaskSnapshot> = journal.all()
    @Synchronized fun recordEvidence(id: String, evidence: List<String>): TaskSnapshot {
        val old = status(id)
        val task = if (active != id && old.state in setOf(TaskState.RUNNING, TaskState.STOP_REQUESTED)) {
            old.copy(state = TaskState.NEEDS_RECONCILIATION, evidence = evidence, error = "EXECUTION_RECORD_INCOMPLETE",
                items = old.items.map { if (it.outcome == Outcome.RUNNING) it.copy(outcome = Outcome.UNKNOWN) else it })
        } else old.copy(evidence = evidence)
        journal.write(task); return task
    }

    /** Explicit acknowledgment preserves UNKNOWN items; only clears the global reconciliation gate. */
    @Synchronized fun acknowledge(id: String): TaskSnapshot {
        check(active == null)
        val task = status(id)
        require(task.state == TaskState.NEEDS_RECONCILIATION)
        val updated = task.copy(state = TaskState.FAILED, error = "UNKNOWN_ACKNOWLEDGED_NO_REPLAY", finishedAt = clock())
        journal.write(updated); return updated
    }

    @Synchronized private fun mutate(id: String, block: (TaskSnapshot) -> TaskSnapshot) {
        journal.write(block(status(id)))
    }
    private fun run(id: String) {
        var begun = false
        try {
            executor.begin(status(id)); begun = true
            for (index in status(id).items.indices) {
                if (stop.get() || clock() - leaseAt > leaseMillis) {
                    mutate(id) { it.copy(state = TaskState.CANCELLED, error = if (stop.get()) "STOP_REQUESTED" else "CONTROL_LEASE_EXPIRED") }
                    break
                }
                mutate(id) { it.copy(items = it.items.replace(index, it.items[index].copy(outcome = Outcome.RUNNING))) }
                val result = try {
                    executor.execute(status(id), index) { phase, mayWrite ->
                        mutate(id) { t -> t.copy(items = t.items.replace(index, t.items[index].copy(phase = phase,
                            potentiallyWritten = t.items[index].potentiallyWritten || mayWrite)),
                            events = t.events + TaskEvent((t.events.lastOrNull()?.sequence ?: 0) + 1, index, phase, "")) }
                    }
                } catch (e: EngineFailure) {
                    val old = status(id).items[index]
                    val result = old.copy(outcome = if (e.uncertain || old.potentiallyWritten) Outcome.UNKNOWN else Outcome.FAILED, reason = e.code)
                    mutate(id) { it.copy(items = it.items.replace(index, result)) }
                    if (e.fatal || result.outcome == Outcome.UNKNOWN) throw e
                    result
                }
                mutate(id) { it.copy(items = it.items.replace(index, result)) }
                if (result.outcome == Outcome.UNKNOWN) throw EngineFailure("RESULT_UNKNOWN", fatal = true, uncertain = true)
            }
            mutate(id) { t -> if (t.state == TaskState.CANCELLED) t else t.copy(state = when {
                t.items.any { it.outcome == Outcome.CANCELLED } && t.items.none { it.outcome == Outcome.UNKNOWN } -> TaskState.CANCELLED
                t.items.all { it.outcome in setOf(Outcome.SUCCEEDED, Outcome.WARNING, Outcome.SKIPPED) } -> TaskState.SUCCEEDED
                t.items.any { it.outcome in setOf(Outcome.SUCCEEDED, Outcome.WARNING, Outcome.PARTIAL) } -> TaskState.PARTIAL
                else -> TaskState.FAILED
            }) }
        } catch (e: Exception) {
            mutate(id) { t -> t.copy(state = if (t.items.any { it.outcome == Outcome.UNKNOWN || it.outcome == Outcome.RUNNING && it.potentiallyWritten } || (e is EngineFailure && e.uncertain)) TaskState.NEEDS_RECONCILIATION else TaskState.FAILED,
                error = (e as? EngineFailure)?.code ?: e.message?.take(160).orEmpty(),
                items = t.items.map { if (it.outcome == Outcome.RUNNING) it.copy(outcome = if (it.potentiallyWritten) Outcome.UNKNOWN else Outcome.FAILED) else it }) }
        } finally {
            try {
                try { if (begun) executor.finish(status(id)) } catch (e: Exception) {
                    mutate(id) { it.copy(state = TaskState.NEEDS_RECONCILIATION, error = "CLEANUP_UNCONFIRMED") }
                }
                mutate(id) { it.copy(finishedAt = clock(), items = it.items.map { item ->
                    if (item.outcome == Outcome.PENDING) item.copy(outcome = Outcome.CANCELLED, reason = "NOT_STARTED") else item
                }) }
            } finally { synchronized(this) { active = null } }
        }
    }
}

private fun <T> List<T>.replace(index: Int, item: T) = toMutableList().also { it[index] = item }.toList()
