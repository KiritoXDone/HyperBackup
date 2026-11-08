package io.github.kiritoxdone.hyperbackup.domain

/** Application use cases never depend on Binder, Shell, or transport implementations. */
interface BackupGateway {
    fun isConnected(): Boolean
    suspend fun connect()
    suspend fun capabilities(): Capabilities
    suspend fun inventory(user: Int): List<AppInstance>
    suspend fun configure(connection: Connection)
    suspend fun initialize(id: String): RepositoryHeader
    suspend fun scan(id: String): ScanResult
    suspend fun preflight(draft: Draft): Plan
    suspend fun submit(request: Submit): TaskSnapshot
    suspend fun status(id: String): TaskSnapshot
    suspend fun reconcile(id: String): TaskSnapshot
    suspend fun history(): List<TaskSnapshot>
    suspend fun stop(id: String): TaskSnapshot
    suspend fun heartbeat(id: String)
    suspend fun acknowledge(id: String): TaskSnapshot
    suspend fun scriptCatalog(): ScriptCatalog
    suspend fun answerPrompt(answer: PromptAnswer)
    suspend fun answerEditor(answer: EditorAnswer)
}
