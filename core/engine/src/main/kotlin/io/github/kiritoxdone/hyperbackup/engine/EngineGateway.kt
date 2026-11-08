package io.github.kiritoxdone.hyperbackup.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.topjohnwu.superuser.ipc.RootService
import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.engine.root.HyperBackupRootService
import io.github.kiritoxdone.hyperbackup.engine.storage.boundedBytes
import kotlinx.coroutines.*
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class EngineGateway(private val context: Context) : BackupGateway {
    @Volatile private var remote: IRootBridge? = null
    private var pending: CompletableDeferred<IRootBridge>? = null
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val service = IRootBridge.Stub.asInterface(binder); remote = service; pending?.complete(service)
            binder.linkToDeath({ remote = null }, 0)
        }
        override fun onServiceDisconnected(name: ComponentName) { remote = null }
        override fun onNullBinding(name: ComponentName) { pending?.completeExceptionally(EngineFailure("ROOT_DENIED")) }
    }
    override suspend fun connect() = withContext(Dispatchers.Main) {
        remote?.let { return@withContext }
        val awaiting = pending?.takeIf { it.isActive } ?: CompletableDeferred<IRootBridge>().also {
            pending = it
            RootService.bind(Intent(context, HyperBackupRootService::class.java).addCategory(RootService.CATEGORY_DAEMON_MODE), connection)
        }
        try { withTimeout(45_000) { awaiting.await() }; Unit } catch (e: Exception) { pending = null; throw EngineFailure("ROOT_CONNECTION_UNAVAILABLE", cause = e) }
    }
    suspend fun call(command: String, body: String = ""): String = withContext(Dispatchers.IO) {
        require(command in HyperBackupRootService.commands)
        val bridge = remote ?: throw EngineFailure("ROOT_NOT_CONNECTED")
        val input = File.createTempFile("bridge-", ".json", context.cacheDir)
        try {
            input.writeText(body)
            val fd = ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY)
            val response = fd.use { bridge.call(command, it) }
            val text = ParcelFileDescriptor.AutoCloseInputStream(response).use { boundedBytes(it, 32 * 1024 * 1024).decodeToString() }
            val reply = Wire.json.decodeFromString<BridgeReply>(text)
            require(reply.protocol == Wire.PROTOCOL) { "PROTOCOL_MISMATCH" }
            if (reply.status != "success") throw EngineFailure(reply.error ?: "ENGINE_ERROR")
            reply.data
        } catch (e: android.os.RemoteException) { remote = null; throw EngineFailure("BINDER_DISCONNECTED_QUERY_EXISTING_TASK", true, true, e) }
        finally { input.delete() }
    }
    override suspend fun capabilities() = Wire.json.decodeFromString<Capabilities>(call("capabilities"))
    override suspend fun inventory(user: Int) = Wire.json.decodeFromString<List<AppInstance>>(call("inventory", "$user"))
    override suspend fun configure(connection: Connection) { call("configure", Wire.json.encodeToString(Connection.serializer(), connection)) }
    override suspend fun initialize(id: String) = Wire.json.decodeFromString<RepositoryHeader>(call("initializeRepository", id))
    override suspend fun scan(id: String) = Wire.json.decodeFromString<ScanResult>(call("inspectRepository", id))
    override suspend fun preflight(draft: Draft) = Wire.json.decodeFromString<Plan>(call("preflight", Wire.json.encodeToString(Draft.serializer(), draft)))
    override suspend fun submit(request: Submit) = Wire.json.decodeFromString<TaskSnapshot>(call("submit", Wire.json.encodeToString(Submit.serializer(), request)))
    override suspend fun status(id: String) = Wire.json.decodeFromString<TaskSnapshot>(call("status", id))
    override suspend fun reconcile(id: String) = Wire.json.decodeFromString<TaskSnapshot>(call("reconcile", id))
    override suspend fun history() = Wire.json.decodeFromString<List<TaskSnapshot>>(call("history"))
    override suspend fun stop(id: String) = Wire.json.decodeFromString<TaskSnapshot>(call("requestStop", id))
    override suspend fun heartbeat(id: String) { call("heartbeat", id) }
    override suspend fun acknowledge(id: String) = Wire.json.decodeFromString<TaskSnapshot>(call("acknowledge", id))
    override suspend fun scriptCatalog() = Wire.json.decodeFromString<ScriptCatalog>(call("scriptCatalog"))
    override suspend fun answerPrompt(answer: PromptAnswer) { call("answerPrompt", Wire.json.encodeToString(PromptAnswer.serializer(), answer)) }
    override suspend fun answerEditor(answer: EditorAnswer) { call("answerEditor", Wire.json.encodeToString(EditorAnswer.serializer(), answer)) }

}
