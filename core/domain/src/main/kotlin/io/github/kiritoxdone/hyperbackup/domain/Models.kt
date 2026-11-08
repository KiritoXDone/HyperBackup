package io.github.kiritoxdone.hyperbackup.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.UUID

object Wire {
    const val PROTOCOL = 1
    const val UPSTREAM = "4d85ef6f974dc8b4a9208dd2aa9578a3d69e1ab5"
    const val HOST = "io.github.kiritoxdone.hyperbackup"
    val json = Json { encodeDefaults = true; ignoreUnknownKeys = false }
    fun id() = UUID.randomUUID().toString()
    fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun uuid(value: String) { require(runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)) { "INVALID_ID" } }
    fun packageName(value: String) { require(value.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+"))) { "INVALID_PACKAGE" } }
}

@Serializable enum class PartKind { APK, CE, DE, EXTERNAL_DATA, OBB, MEDIA, APP_STATE }
@Serializable enum class PartStatus { INCLUDED, NOT_SELECTED, EMPTY, UNSUPPORTED, EXCLUDED, FAILED }
@Serializable enum class Outcome { PENDING, RUNNING, SUCCEEDED, WARNING, PARTIAL, FAILED, SKIPPED, CANCELLED, UNKNOWN }
@Serializable enum class TaskState { RUNNING, STOP_REQUESTED, SUCCEEDED, PARTIAL, FAILED, CANCELLED, NEEDS_RECONCILIATION }
@Serializable enum class Operation { BACKUP, RESTORE, DELETE, VERIFY }
@Serializable enum class Protocol { LOCAL, WEBDAV, SMB }
@Serializable data class AndroidUser(val id: Int, val name: String, val unlocked: Boolean)
@Serializable data class AppKey(val userId: Int, val packageName: String) {
    fun validate() { require(userId in 0..21474); Wire.packageName(packageName) }
    val stableId get() = "$userId:$packageName"
}
@Serializable data class AppInstance(
    val key: AppKey, val label: String, val versionCode: String?, val versionName: String = "",
    val uid: Int, val system: Boolean = false, val signatures: List<String> = emptyList(),
    val apkPaths: List<String> = emptyList(), val dataPath: String = "", val dePath: String = "",
    val protectedReason: String? = null,
)
@Serializable data class Destination(
    val id: String = Wire.id(), val name: String, val protocol: Protocol, val address: String,
    val username: String = "", val domain: String = "", val allowHttp: Boolean = false, val repoId: String? = null,
)
@Serializable data class Connection(val destination: Destination, val password: String = "")
@Serializable data class Origin(val deviceId: String, val label: String, val androidVersion: String, val userId: Int)
@Serializable data class ArchivePart(
    val kind: PartKind, val status: PartStatus, val path: String? = null, val bytes: Long = 0,
    val sha256: String? = null, val expandedBytes: Long = 0, val entries: Long = 0, val reason: String = "",
)
@Serializable data class ApkEntry(val name: String, val bytes: Long, val sha256: String, val splitName: String? = null,
    val minSdk: Int = 1, val nativeAbis: Set<String> = emptySet(), val requiredSplits: Set<String> = emptySet())
@Serializable data class BackupRecord(
    val schemaVersion: Int = 1, val recordId: String = Wire.id(), val batchId: String, val origin: Origin,
    val app: AppInstance, val createdAt: Long = System.currentTimeMillis(), val completion: Outcome,
    val parts: List<ArchivePart>, val apks: List<ApkEntry> = emptyList(), val upstreamCommit: String = Wire.UPSTREAM,
    val adapterVersion: Int = 1, val exclusionPolicy: String = "upstream-v1117",
    val purpose: String = "USER", val restoreTaskId: String? = null,
    val scriptRoot: String? = null, val scriptDirectory: String? = null, val nativeMetadataHash: String? = null,
)
@Serializable data class RepositoryHeader(
    val format: String = "hyperbackup", val schemaVersion: Int = 1, val repoId: String = Wire.id(),
    val createdAt: Long = System.currentTimeMillis(), val writerCompatibility: Int = 1,
)
@Serializable data class Commit(val schemaVersion: Int = 1, val recordId: String, val recordHash: String, val payloads: List<ArchivePart>)
@Serializable data class Batch(val batchId: String, val purpose: String, val taskId: String, val startedAt: Long, val recordIds: List<String> = emptyList())
@Serializable data class WriterClaim(val owner: String, val taskId: String, val bootId: String, val sessionId: String, val createdAt: Long = System.currentTimeMillis())
@Serializable data class RecordRef(val batchId: String, val recordId: String, val digest: String)
@Serializable data class PlanItem(val app: AppKey, val record: RecordRef? = null, val parts: Set<PartKind>, val expectedVersion: String? = null,
    val scriptRoot: String? = null, val scriptDirectory: String? = null, val nativeMetadataHash: String? = null)
@Serializable data class Draft(
    val operation: Operation, val destinationId: String, val items: List<PlanItem>, val safetyCopy: Boolean = true,
    val safetyDestinationId: String = destinationId, val restoreSsaid: Boolean = false,
    val restorePermissions: Boolean = false, val restoreAppOps: Boolean = false,
    val ignoreRunning: Boolean = false, val blacklist: Set<String> = emptySet(), val blacklistSkip: Boolean = false,
    val script: ScriptSpec? = null,
)
@Serializable data class Plan(
    val id: String = Wire.id(), val draft: Draft, val repoId: String, val checkedAt: Long = System.currentTimeMillis(),
    val warnings: List<String> = emptyList(), val blockers: List<String> = emptyList(),
    val estimates: List<String> = emptyList(), val engine: String = Wire.UPSTREAM,
    val destinationDigest: String = "", val safetyDestinationDigest: String = "",
) {
    fun digest() = Wire.hash(Wire.json.encodeToString(serializer(), this).toByteArray())
}
@Serializable data class Submit(val taskId: String, val requestId: String, val planId: String, val digest: String)
@Serializable data class PartResult(val kind: PartKind, val outcome: Outcome, val reason: String, val written: Boolean = false)
@Serializable data class ItemResult(
    val app: AppKey, val outcome: Outcome, val phase: String = "PENDING", val reason: String = "",
    val parts: List<PartResult> = emptyList(), val record: RecordRef? = null, val safetyRecord: RecordRef? = null,
    val potentiallyWritten: Boolean = false,
)
@Serializable data class TaskEvent(val sequence: Long, val item: Int, val phase: String, val reason: String, val timestamp: Long = System.currentTimeMillis())
@Serializable data class TaskSnapshot(
    val protocol: Int = 1, val id: String, val requestId: String, val plan: Plan, val digest: String,
    val bootId: String, val sessionId: String, val state: TaskState = TaskState.RUNNING,
    val startedAt: Long = System.currentTimeMillis(), val finishedAt: Long? = null,
    val items: List<ItemResult>, val events: List<TaskEvent> = emptyList(), val error: String = "", val evidence: List<String> = emptyList(),
    val prompt: EnginePrompt? = null, val editor: EditorSnapshot? = null,
)
@Serializable data class Capabilities(val protocol: Int = 1, val engine: String = Wire.UPSTREAM, val bootId: String, val sessionId: String,
    val androidVersion: String, val abi: String, val users: List<AndroidUser>, val limitations: List<String>)
@Serializable data class BridgeReply(val protocol: Int = 1, val status: String = "success", val data: String = "", val error: String? = null)

class EngineFailure(val code: String, val fatal: Boolean = false, val uncertain: Boolean = false, cause: Throwable? = null) : Exception(code, cause)
