package io.github.kiritoxdone.hyperbackup.data

import io.github.kiritoxdone.hyperbackup.domain.*

/** Coordinates typed engine requests and disposable Room projections; no Root writes live here. */
class BackupSession(private val gateway: BackupGateway, private val dao: BackupDao, private val vault: CredentialVault) {
    companion object {
        fun create(context: android.content.Context, gateway: BackupGateway) = BackupSession(gateway, BackupDatabase.open(context).dao(), CredentialVault())
    }
    val connected: Boolean get() = gateway.isConnected()
    suspend fun destinations() = dao.destinations().map { Wire.json.decodeFromString<Destination>(it.json) }
    suspend fun connect(): Capabilities {
        gateway.connect()
        dao.destinations().forEach { row ->
            val destination = Wire.json.decodeFromString<Destination>(row.json)
            gateway.configure(Connection(destination, vault.decrypt(row.id, row.encryptedSecret)))
        }
        return gateway.capabilities()
    }
    suspend fun saveDestination(connection: Connection, create: Boolean): Destination {
        gateway.configure(connection)
        val repo = if (create) gateway.initialize(connection.destination.id) else gateway.scan(connection.destination.id).repository
        val destination = connection.destination.copy(repoId = repo.repoId.takeIf { repo.createdAt != 0L })
        gateway.configure(connection.copy(destination = destination))
        dao.saveDestination(DestinationRow(destination.id, Wire.json.encodeToString(Destination.serializer(), destination), vault.encrypt(destination.id, connection.password)))
        return destination
    }
    suspend fun inventory(user: Int) = gateway.inventory(user)
    suspend fun scan(id: String): ScanResult {
        val result = gateway.scan(id)
        dao.saveRepository(RepositoryRow(id, Wire.json.encodeToString(ScanResult.serializer(), result), result.scannedAt)); return result
    }
    suspend fun cachedRepository(id: String) = dao.repository(id)?.let { Wire.json.decodeFromString<ScanResult>(it.json) }
    suspend fun preflight(draft: Draft) = gateway.preflight(draft)
    suspend fun submit(plan: Plan): TaskSnapshot {
        check(dao.submissions().isEmpty()) { "SUBMISSION_AWAITING_RECONCILIATION" }
        val request = Submit(Wire.id(), Wire.id(), plan.id, plan.digest())
        dao.saveSubmission(SubmissionRow(request.taskId, Wire.json.encodeToString(Submit.serializer(), request), Wire.json.encodeToString(Plan.serializer(), plan)))
        val task = gateway.submit(request)
        save(task); dao.accepted(task.id); return task
    }
    suspend fun pendingSubmissions() = dao.submissions()
    suspend fun reconcileSubmissions() {
        dao.submissions().forEach { row ->
            try { val task = gateway.status(row.taskId); save(task); dao.accepted(task.id) }
            catch (e: EngineFailure) { if (e.code == "TASK_NOT_FOUND") dao.accepted(row.taskId) else throw e }
        }
    }
    suspend fun status(id: String): TaskSnapshot = gateway.status(id).also { save(it) }
    suspend fun reconcile(id: String): TaskSnapshot = gateway.reconcile(id).also { save(it) }
    suspend fun history(): List<TaskSnapshot> = gateway.history().also { list -> list.forEach { save(it) } }
    suspend fun cachedHistory() = dao.tasks().map { Wire.json.decodeFromString<TaskSnapshot>(it.json) }
    suspend fun stop(id: String) = gateway.stop(id).also { save(it) }
    suspend fun heartbeat(id: String) = gateway.heartbeat(id)
    suspend fun acknowledge(id: String) = gateway.acknowledge(id).also { save(it) }
    suspend fun scriptCatalog() = gateway.scriptCatalog()
    suspend fun answerPrompt(answer: PromptAnswer) = gateway.answerPrompt(answer)
    suspend fun answerEditor(answer: EditorAnswer) = gateway.answerEditor(answer)
    private suspend fun save(task: TaskSnapshot) { dao.saveTask(TaskRow(task.id, Wire.json.encodeToString(TaskSnapshot.serializer(), task), task.startedAt)) }
}
