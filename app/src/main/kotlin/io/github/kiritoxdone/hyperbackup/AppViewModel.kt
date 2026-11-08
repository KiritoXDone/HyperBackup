package io.github.kiritoxdone.hyperbackup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.kiritoxdone.hyperbackup.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class AppState(
    val capabilities: Capabilities? = null, val apps: List<AppInstance> = emptyList(), val user: Int = 0,
    val selectedApps: Set<AppKey> = emptySet(), val selectedRecords: Set<String> = emptySet(),
    val destinations: List<Destination> = emptyList(), val destinationId: String? = null,
    val repository: ScanResult? = null, val cached: Boolean = false, val tasks: List<TaskSnapshot> = emptyList(),
    val draft: Draft? = null, val plan: Plan? = null, val busy: Boolean = false, val error: String? = null,
    val theme: String = "system", val pendingSubmission: Boolean = false, val scriptCatalog: ScriptCatalog? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as HyperBackupApp
    private val session = app.session
    private val mutable = MutableStateFlow(AppState())
    val state = mutable.asStateFlow()
    init {
        viewModelScope.launch { app.preferences.theme.collect { value -> mutable.update { it.copy(theme = value) } } }
        action {
            val destinations = session.destinations(); val selected = app.preferences.selectedDestination() ?: destinations.firstOrNull()?.id
            val draft = app.preferences.draft()?.let { runCatching { Wire.json.decodeFromString<Draft>(it) }.getOrNull() }
            mutable.update { it.copy(destinations = destinations, destinationId = selected, tasks = session.cachedHistory(),
                repository = selected?.let { id -> session.cachedRepository(id) }, cached = true, draft = draft) }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(3000)
                if (state.value.capabilities != null && !state.value.busy) {
                    val active = state.value.tasks.firstOrNull { it.state in setOf(TaskState.RUNNING, TaskState.STOP_REQUESTED) }
                    if (active != null) runCatching { session.status(active.id) }.onSuccess { updateTask(it) }.onFailure {
                        mutable.update { s -> s.copy(error = "正在确认任务状态：${it.message}") }
                    }
                }
            }
        }
    }
    fun action(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { block() } catch (e: Exception) { mutable.update { it.copy(error = e.message ?: "操作失败") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun connect() = action {
        val caps = session.connect(); val catalog = session.scriptCatalog(); session.reconcileSubmissions()
        mutable.update { it.copy(capabilities = caps, tasks = session.history(), pendingSubmission = false, scriptCatalog = catalog) }
        val user = caps.users.firstOrNull { it.unlocked }?.id ?: 0
        loadApps(user)
        state.value.tasks.firstOrNull { it.state in setOf(TaskState.RUNNING, TaskState.STOP_REQUESTED) }?.let { TaskForegroundService.observe(app, it.id) }
    }
    private suspend fun loadApps(user: Int) { val apps = session.inventory(user); mutable.update { it.copy(apps = apps, user = user) } }
    fun selectUser(user: Int) = action { loadApps(user) }
    fun toggleApp(key: AppKey) { mutable.update { it.copy(selectedApps = if (key in it.selectedApps) it.selectedApps - key else it.selectedApps + key) } }
    fun toggleAll(keys: List<AppKey>) { mutable.update { it.copy(selectedApps = if (it.selectedApps.containsAll(keys)) it.selectedApps - keys.toSet() else it.selectedApps + keys) } }
    fun toggleRecord(id: String) { mutable.update { it.copy(selectedRecords = if (id in it.selectedRecords) it.selectedRecords - id else it.selectedRecords + id) } }
    fun selectDestination(id: String) = action {
        app.preferences.selectDestination(id)
        mutable.update { it.copy(destinationId = id, repository = session.cachedRepository(id), cached = true, plan = null) }
    }
    fun saveDestination(connection: Connection, create: Boolean, done: () -> Unit) = action {
        val saved = session.saveDestination(connection, create); app.preferences.selectDestination(saved.id)
        mutable.update { it.copy(destinations = session.destinations(), destinationId = saved.id) }; done()
    }
    fun scan() = action {
        val id = state.value.destinationId ?: error("请先添加备份位置")
        val result = session.scan(id); mutable.update { it.copy(repository = result, cached = false) }
    }
    private fun setDraft(draft: Draft) {
        mutable.update { it.copy(draft = draft, plan = null) }
        viewModelScope.launch { app.preferences.draft(Wire.json.encodeToString(Draft.serializer(), draft)) }
    }
    fun beginBackup() {
        val s = state.value; val id = s.destinationId ?: return clearError("请先添加备份位置")
        if (s.selectedApps.isEmpty()) return clearError("请先选择应用")
        setDraft(Draft(Operation.BACKUP, id, s.selectedApps.map { PlanItem(it, parts = setOf(PartKind.APK, PartKind.CE, PartKind.DE)) }, script = ScriptSpec(ScriptCommand.BACKUP_APPS, "", s.user, selectedPackages = s.selectedApps.map { it.packageName })))
    }
    fun beginRecords(records: List<BackupRecord>, operation: Operation) {
        val s = state.value; val id = s.destinationId ?: return
        val destination = s.destinations.first { it.id == id }
        val items = records.map { r -> PlanItem(AppKey(s.user, r.app.key.packageName), parts = setOf(PartKind.APK, PartKind.CE, PartKind.DE),
            scriptRoot = if (destination.protocol == Protocol.LOCAL) destination.address.trimEnd('/') + "/" + r.scriptRoot.orEmpty() else null,
            scriptDirectory = r.scriptDirectory, nativeMetadataHash = r.nativeMetadataHash) }
        val command = if (operation == Operation.VERIFY) ScriptCommand.VERIFY_ARCHIVES else if (destination.protocol == Protocol.LOCAL) ScriptCommand.RESTORE_APPS else ScriptCommand.REMOTE_RESTORE_STAGED
        if (items.isNotEmpty()) setDraft(Draft(operation, id, items, safetyCopy = true,
            script = ScriptSpec(command, items.first().scriptRoot.orEmpty(), s.user, selectedPackages = records.map { it.app.key.packageName })))
    }
    fun beginScript(command: ScriptCommand) {
        val s = state.value; val id = s.destinationId ?: return clearError("请先添加备份位置")
        setDraft(Draft(if (command.restore) Operation.RESTORE else Operation.BACKUP, id,
            listOf(PlanItem(AppKey(s.user, "io.github.kiritoxdone.hyperbackup.scope"), parts = setOf(PartKind.CE))),
            safetyCopy = command.restore, script = ScriptSpec(command, "", s.user, preserveExistingList = false)))
    }
    fun answer(prompt: EnginePrompt, value: String) = action { session.answerPrompt(PromptAnswer(prompt.taskId, prompt.id, value)) }
    fun answerEditor(editor: EditorSnapshot, commandAction: String, body: String = "", done: () -> Unit = {}) = action {
        session.answerEditor(EditorAnswer(editor.taskId, editor.id, commandAction, body)); done()
    }
    fun retry(task: TaskSnapshot) {
        val retryKeys = task.items.filter { it.outcome in setOf(Outcome.FAILED, Outcome.PARTIAL, Outcome.CANCELLED) }.map { it.app }.toSet()
        setDraft(task.plan.draft.copy(items = task.plan.draft.items.filter { it.app in retryKeys }, safetyCopy = true))
    }
    fun changeDraft(change: (Draft) -> Draft) { state.value.draft?.let { setDraft(change(it)) } }
    fun preflight(done: () -> Unit) = action {
        val plan = session.preflight(requireNotNull(state.value.draft))
        mutable.update { it.copy(plan = plan) }; done()
    }
    fun submit(done: (String) -> Unit) = action {
        val plan = requireNotNull(state.value.plan)
        // FGS starts from this visible user action before dispatch; persisted task identity follows below.
        TaskForegroundService.prepare(app)
        mutable.update { it.copy(pendingSubmission = true) }
        val task = session.submit(plan)
        updateTask(task); TaskForegroundService.observe(app, task.id)
        mutable.update { it.copy(pendingSubmission = false, plan = null) }; done(task.id)
    }
    fun refreshTasks() = action { session.reconcileSubmissions(); mutable.update { it.copy(tasks = session.history(), pendingSubmission = false) } }
    fun stop(id: String) = action { updateTask(session.stop(id)) }
    fun reconcile(id: String) = action { updateTask(session.reconcile(id)) }
    fun acknowledge(id: String) = action { updateTask(session.acknowledge(id)) }
    fun theme(value: String) { viewModelScope.launch { app.preferences.theme(value) } }
    fun clearError(message: String? = null) { mutable.update { it.copy(error = message) } }
    private fun updateTask(task: TaskSnapshot) { mutable.update { it.copy(tasks = (listOf(task) + it.tasks.filterNot { old -> old.id == task.id }).sortedByDescending { row -> row.startedAt }) } }
}
