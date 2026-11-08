package io.github.kiritoxdone.hyperbackup.engine.root

import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.topjohnwu.superuser.ipc.RootService
import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.engine.IRootBridge
import io.github.kiritoxdone.hyperbackup.engine.storage.Connections
import io.github.kiritoxdone.hyperbackup.engine.storage.boundedBytes
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.util.concurrent.Executors

class HyperBackupRootService : RootService() {
    private val rpc = Executors.newFixedThreadPool(3)
    private val plans = java.util.concurrent.ConcurrentHashMap<String, Plan>()
    private val connections = Connections()
    private lateinit var dex: UpstreamDex
    private lateinit var jobs: JobCoordinator
    private lateinit var preflight: ScriptPlans
    private lateinit var scriptRunner: ScriptRunner
    private lateinit var bootId: String
    private val sessionId = Wire.id()
    private var ownerUid = -1
    private var initializationError: String? = null

    override fun onCreate() {
        super.onCreate()
        ownerUid = applicationInfo.uid
        try {
            require(android.os.Build.VERSION.SDK_INT >= 28 && android.os.Build.SUPPORTED_ABIS.contains("arm64-v8a")) { "PLATFORM_UNSUPPORTED" }
            bootId = File("/proc/sys/kernel/random/boot_id").readText().trim()
            val root = File("/data/adb/hyperbackup/$ownerUid")
            val assets = RootAssets(this, root)
            dex = UpstreamDex(assets)
            dex.systemContext // Bootstrap the upstream system Context on the daemon main thread.
            val work = File(root, "work").also { it.mkdirs(); android.system.Os.chmod(it.path, 448) }
            val origin = File(root, "origin-id").let { if (!it.exists()) it.writeText(Wire.id()); it.readText() }
            preflight = ScriptPlans(assets, dex, connections)
            scriptRunner = ScriptRunner(assets)
            val journal = FileTaskJournal(File(root, "journal")) { directory ->
                val descriptor = android.system.Os.open(directory.path, android.system.OsConstants.O_RDONLY, 0)
                try { require(android.system.OsConstants.S_ISDIR(android.system.Os.fstat(descriptor).st_mode)); android.system.Os.fsync(descriptor) } finally { android.system.Os.close(descriptor) }
            }
            jobs = JobCoordinator(journal, ScriptJobExecutor(assets, dex, connections, preflight, scriptRunner), bootId, sessionId)
            scriptRunner.stopDue = jobs::stopDue
            scriptRunner.showPrompt = jobs::prompt
            scriptRunner.showEditor = jobs::editor
        } catch (e: Throwable) { initializationError = "ENGINE_INIT:${e.cause?.message ?: e.message ?: e.javaClass.simpleName}" }
    }
    override fun onBind(intent: Intent): IBinder = object : IRootBridge.Stub() {
        override fun call(command: String, payload: ParcelFileDescriptor): ParcelFileDescriptor {
            if (Binder.getCallingUid() != ownerUid) throw SecurityException("CLIENT_UID_MISMATCH")
            require(command in commands) { "UNKNOWN_COMMAND" }
            val pipe = ParcelFileDescriptor.createPipe()
            rpc.execute {
                val reply = try {
                    val input = ParcelFileDescriptor.AutoCloseInputStream(payload).use { boundedBytes(it, 8 * 1024 * 1024).decodeToString() }
                    initializationError?.let { error(it) }
                    BridgeReply(data = dispatch(command, input))
                } catch (e: Throwable) {
                    BridgeReply(status = "error", error = (e as? EngineFailure)?.code ?: e.cause?.message?.take(200) ?: e.message?.take(200) ?: e.javaClass.simpleName)
                }
                runCatching { ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { it.write(Wire.json.encodeToString(BridgeReply.serializer(), reply).toByteArray()) } }
            }
            return pipe[0]
        }
    }
    private fun dispatch(command: String, body: String): String = when (command) {
        "capabilities" -> Wire.json.encodeToString(Capabilities.serializer(), Capabilities(bootId = bootId, sessionId = sessionId,
            androidVersion = android.os.Build.VERSION.RELEASE, abi = android.os.Build.SUPPORTED_ABIS.first(), users = dex.users(),
            limitations = listOf("归档未加密", "归档链接/稀疏文件拒绝恢复", "硬件密钥与登录状态不保证迁移", "SMB2/3 签名，不承诺传输加密")))
        "inventory" -> Wire.json.encodeToString(ListSerializer(AppInstance.serializer()), dex.inventory(body.toInt()))
        "configure" -> { connections.configure(Wire.json.decodeFromString(body)); "ok" }
        "initializeRepository" -> connections.open(body, true).use { Wire.json.encodeToString(RepositoryHeader.serializer(), io.github.kiritoxdone.hyperbackup.engine.storage.NativeRepository(it).initialize()) }
        "inspectRepository" -> connections.open(body).use { Wire.json.encodeToString(ScanResult.serializer(), io.github.kiritoxdone.hyperbackup.engine.storage.NativeRepository(it).scan(connections.destination(body).repoId)) }
        "scriptCatalog" -> Wire.json.encodeToString(ScriptCatalog.serializer(), preflight.catalog())
        "answerPrompt" -> { scriptRunner.answer(Wire.json.decodeFromString(body)); "ok" }
        "answerEditor" -> { scriptRunner.editorProxy.answer(Wire.json.decodeFromString(body)); "ok" }
        "preflight" -> {
            val plan = preflight.check(Wire.json.decodeFromString(body))
            if (plans.size >= 32) plans.remove(plans.keys.first())
            plans[plan.id] = plan
            Wire.json.encodeToString(Plan.serializer(), plan)
        }
        "submit" -> {
            val request = Wire.json.decodeFromString<Submit>(body)
            val plan = jobs.history().find { it.id == request.taskId }?.plan ?: plans[request.planId] ?: error("PLAN_NOT_FOUND_RECHECK")
            Wire.json.encodeToString(TaskSnapshot.serializer(), jobs.submit(request, plan))
        }
        "status", "result" -> Wire.json.encodeToString(TaskSnapshot.serializer(), jobs.status(body))
        "reconcile" -> Wire.json.encodeToString(TaskSnapshot.serializer(), jobs.recordEvidence(body, Reconciliation(dex, File("/data/adb/hyperbackup/$ownerUid")).inspect(jobs.status(body))))
        "history" -> Wire.json.encodeToString(ListSerializer(TaskSnapshot.serializer()), jobs.history())
        "requestStop" -> Wire.json.encodeToString(TaskSnapshot.serializer(), jobs.requestStop(body))
        "heartbeat" -> { jobs.heartbeat(body); "ok" }
        "acknowledge" -> {
            Reconciliation(dex, File("/data/adb/hyperbackup/$ownerUid")).cleanupAcknowledged(jobs.status(body))
            Wire.json.encodeToString(TaskSnapshot.serializer(), jobs.acknowledge(body))
        }
        else -> error("UNKNOWN_COMMAND")
    }
    override fun onUnbind(intent: Intent): Boolean = true
    companion object {
        val commands = setOf("capabilities", "inventory", "configure", "initializeRepository", "inspectRepository", "preflight", "submit", "status", "reconcile", "result", "history", "requestStop", "heartbeat", "acknowledge", "scriptCatalog", "answerPrompt", "answerEditor")
    }
}
