package io.github.kiritoxdone.hyperbackup.engine.root

import io.github.kiritoxdone.hyperbackup.domain.*
import kotlinx.serialization.json.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.UserHandle

data class DexResult(val code: Int, val body: String)

/** Fixed-version adapter. No arbitrary class or command is exposed through IPC. */
class UpstreamDex(private val assets: RootAssets) {
    private val loader get() = assets.loader
    init {
        loader.loadClass("com.xayah.dex.HiddenApiBypassBridge").getDeclaredMethod("installExemptionsOnce").apply { isAccessible = true }.invoke(null)
    }
    val systemContext: Context by lazy {
        loader.loadClass("com.xayah.dex.HiddenApiHelper").getMethod("getContext").invoke(null) as Context
    }
    fun hidden(command: String, vararg args: String): DexResult {
        require(args.none { '\n' in it || '\r' in it })
        val cls = loader.loadClass("com.xayah.dex.HiddenApiUtil")
        val method = cls.getDeclaredMethod("runDaemonCommand", String::class.java, ByteArray::class.java).apply { isAccessible = true }
        val result = method.invoke(null, command, (args.joinToString("\n") + "\n").toByteArray())
        val rc = result.javaClass.getDeclaredField("rc").apply { isAccessible = true }.getInt(result)
        val output = result.javaClass.getDeclaredField("stdout").apply { isAccessible = true }.get(result) as String
        return DexResult(rc, output)
    }
    fun state(command: String, user: Int, body: String): DexResult {
        val cls = loader.loadClass("com.xayah.dex.AppStateEngine")
        val result = cls.getMethod("dispatch", String::class.java, Int::class.javaPrimitiveType, String::class.java, String::class.java)
            .invoke(null, command, user, "", body)
        val code = result.javaClass.getField("resultCode").get(result)
        return DexResult(code.javaClass.getField("code").getInt(code), result.javaClass.getField("body").get(result) as String)
    }
    fun users(): List<AndroidUser> {
        val manager = systemContext.getSystemService(Context.USER_SERVICE)
        val users = manager.javaClass.getMethod("getUsers").invoke(manager) as List<*>
        return users.map { user ->
            val cls = user!!.javaClass; val id = cls.getField("id").getInt(user)
            val handle = UserHandle::class.java.getMethod("of", Int::class.javaPrimitiveType).invoke(null, id)
            val unlocked = manager.javaClass.getMethod("isUserUnlocked", UserHandle::class.java).invoke(manager, handle) as Boolean
            AndroidUser(id, cls.getField("name").get(user)?.toString() ?: "用户 $id", unlocked)
        }
    }
    fun requireUser(user: Int) { if (users().none { it.id == user && it.unlocked }) throw EngineFailure("USER_LOCKED_OR_REMOVED", true) }
    fun inventory(user: Int): List<AppInstance> {
        requireUser(user)
        val result = hidden("appInventorySnapshot", "$user", "jsonl", "all", "refresh")
        if (result.code != 0) throw EngineFailure("INVENTORY_FAILED", true)
        return result.body.lineSequence().filter { it.isNotBlank() }.map { line ->
            val o = Json.parseToJsonElement(line).jsonObject
            require(o["schema"]?.jsonPrimitive?.content == "speedbackup.app_inventory.v1")
            val pkg = o.getValue("packageName").jsonPrimitive.content
            val apk = listOf(o.getValue("sourceDir").jsonPrimitive.content) + o.getValue("splitSourceDirs").jsonArray.map { it.jsonPrimitive.content }
            val app = AppInstance(AppKey(user, pkg), o.getValue("label").jsonPrimitive.content,
                o["versionCode"]?.jsonPrimitive?.content?.takeUnless { it.startsWith('-') }, o["versionName"]?.jsonPrimitive?.content.orEmpty(),
                o.getValue("uid").jsonPrimitive.int, o.getValue("system").jsonPrimitive.boolean,
                apkPaths = apk, dataPath = "/data/user/$user/$pkg", dePath = "/data/user_de/$user/$pkg")
            app.copy(protectedReason = if (ProtectionPolicy.protected(app)) "SYSTEM_OR_EXECUTION_HOST" else null)
        }.toList()
    }
    fun installed(key: AppKey): AppInstance? = inventory(key.userId).firstOrNull { it.key == key }
    fun archiveInfo(file: java.io.File): Pair<String, Pair<String, List<String>>> {
        val info = systemContext.packageManager.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw EngineFailure("INVALID_APK")
        val hashes = info.signingInfo?.apkContentsSigners?.map { Wire.hash(it.toByteArray()) }.orEmpty()
        require(hashes.isNotEmpty()) { "APK_SIGNATURE_UNKNOWN" }
        return info.packageName to (info.longVersionCode.toString() to hashes.sorted())
    }
}
