package io.github.kiritoxdone.hyperbackup.engine.root

import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.engine.storage.boundedBytes
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit

/** The original native server retains parsing, selections, account validation and atomic saves. */
class NativeEditorProxy {
    private data class Endpoint(val directory: File, val origin: String, val token: String)
    private val endpoints = java.util.concurrent.ConcurrentHashMap<String, Endpoint>()
    private val snapshots = java.util.concurrent.ConcurrentHashMap<String, EditorSnapshot>()
    private val configurations = java.util.concurrent.ConcurrentHashMap<String, List<ScriptFormValue>>()
    val allowedPackages = java.util.concurrent.ConcurrentHashMap<String, Set<String>>()
    private val client = OkHttpClient.Builder().followRedirects(false).connectTimeout(3, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    fun discover(taskId: String, control: File): EditorSnapshot? {
        val file = File(control, "editors").listFiles().orEmpty().map { File(it, "endpoint.json") }.firstOrNull { it.isFile }
            ?: run { endpoints.remove(taskId); return null }
        val data = Wire.json.parseToJsonElement(file.readText()).jsonObject
        val origin = data.getValue("origin").jsonPrimitive.content; val token = data.getValue("token").jsonPrimitive.content
        val uri = URI(origin)
        require(uri.scheme == "http" && uri.host == "127.0.0.1" && uri.port in 1..65535 && token.matches(Regex("[0-9a-f]{64}")))
        endpoints[taskId] = Endpoint(file.parentFile, origin, token)
        val manager = request(taskId, "GET", "/api/manager").let { Wire.json.parseToJsonElement(it).jsonObject }
        val rows = request(taskId, "GET", "/api/list").let { Wire.json.parseToJsonElement(it).jsonArray }.map { value ->
            val row = value.jsonObject
            EditorRow(row.getValue("pkg").jsonPrimitive.content, row.getValue("label").jsonPrimitive.content,
                row["selected"]?.jsonPrimitive?.booleanOrNull ?: false, row["apk"]?.jsonPrimitive?.booleanOrNull ?: false,
                row["displayPackage"]?.jsonPrimitive?.content.orEmpty())
        }
        val timeline = runCatching { request(taskId, "GET", "/api/timeline") }.getOrNull()
        val auth = manager["authSetup"]?.jsonPrimitive?.booleanOrNull == true
        val rawFields = if (auth) request(taskId, "GET", if (manager["restore"]?.jsonPrimitive?.booleanOrNull == true) "/api/restore" else "/api/backup")
            .let { Wire.json.parseToJsonElement(it).jsonArray }.map { value ->
                val field = value.jsonObject; val key = field.getValue("key").jsonPrimitive.content
                ScriptFormValue(key, field.getValue("value").jsonPrimitive.content)
            } else emptyList()
        configurations[taskId] = rawFields
        val fields = rawFields.filter { it.key.endsWith("_remote_user") || it.key.endsWith("_remote_pass") }.map { if (it.key.endsWith("_pass")) it.copy(value = "") else it }
        return EditorSnapshot(taskId, file.parentFile.name, rows, timeline, manager["mode"]?.jsonPrimitive?.content.orEmpty(),
            auth, fields, manager["restore"]?.jsonPrimitive?.booleanOrNull == true).also { snapshots[taskId] = it }
    }
    fun answer(value: EditorAnswer) {
        val endpoint = endpoints[value.taskId] ?: error("EDITOR_NOT_PENDING")
        require(endpoint.directory.name == value.id && File(endpoint.directory, "endpoint.json").isFile) { "EDITOR_IDENTITY_CHANGED" }
        val path = when (value.action) { "save" -> "/api/save"; "cancel" -> "/api/cancel"; "finish" -> "/api/finish"; "auth-backup" -> "/api/save-backup"; "auth-restore" -> "/api/save-restore"; else -> error("UNKNOWN_EDITOR_ACTION") }
        require(value.body.length <= 1024 * 1024)
        if (value.action == "save") {
            val rows = snapshots.getValue(value.taskId).rows.associateBy { it.id }
            value.body.lineSequence().filter { it.isNotEmpty() && !it.startsWith("@timeline\t") }.forEach { line ->
                val fields = line.split('\t'); require(fields.size == 3)
                val row = rows[fields[0]] ?: error("EDITOR_ROW_CHANGED")
                if (fields[1] == "1") {
                    val pkg = row.packageName.ifEmpty { row.id }
                    require(pkg !in ScriptPlans.protectedPackages) { "PROTECTED_EXECUTION_HOST" }
                    allowedPackages[value.taskId]?.takeIf { it.isNotEmpty() }?.let { allowed -> require(pkg in allowed) { "SELECTION_OUTSIDE_FROZEN_PLAN" } }
                }
            }
        }
        val body = if (value.action.startsWith("auth-")) {
            require(snapshots.getValue(value.taskId).authSetup)
            val changes = Wire.json.decodeFromString<List<ScriptFormValue>>(value.body).associate { it.key to it.value }
            require(changes.keys.all { key -> key.endsWith("_remote_user") || key.endsWith("_remote_pass") })
            configurations.getValue(value.taskId).joinToString("\n") { field ->
                field.key + "\t" + (changes[field.key] ?: field.value).toByteArray().joinToString("") { "%02x".format(it) }
            }
        } else value.body
        request(value.taskId, "POST", path, body)
        if (value.action.startsWith("auth-")) request(value.taskId, "POST", "/api/finish")
    }
    fun cancel(taskId: String) { if (endpoints.containsKey(taskId)) runCatching { request(taskId, "POST", "/api/cancel") } }
    private fun request(taskId: String, method: String, path: String, body: String = ""): String {
        val endpoint = endpoints[taskId] ?: error("EDITOR_NOT_PENDING")
        val request = Request.Builder().url(endpoint.origin + path).header("X-Speedbackup-Token", endpoint.token)
            .method(method, if (method == "GET") null else body.toRequestBody("text/plain;charset=UTF-8".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.byteStream()?.use { boundedBytes(it, 4 * 1024 * 1024).decodeToString() }.orEmpty()
            if (!response.isSuccessful) throw EngineFailure("UPSTREAM_EDITOR_${response.code}:${text.take(160)}")
            return text
        }
    }
}
