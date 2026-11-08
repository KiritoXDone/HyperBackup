package io.github.kiritoxdone.hyperbackup.engine.storage

import io.github.kiritoxdone.hyperbackup.domain.*
import kotlinx.serialization.json.*
import java.util.UUID

/** Reads the script's existing layout. HyperBackup metadata is a separate, optional sidecar. */
class NativeRepository(private val store: ObjectStore) {
    fun identity(): RepositoryHeader = RepositoryHeader(repoId = stableId("unregistered-script-repository"), createdAt = 0)

    fun initialize(): RepositoryHeader {
        require(store.list("").isEmpty()) { "DIRECTORY_NOT_EMPTY" }
        return identity()
    }
    fun scan(expected: String? = null): ScanResult {
        val header = identity()
        require(expected == null || expected == header.repoId) { "REPOSITORY_CHANGED" }
        val roots = mutableListOf<String>()
        fun discover(path: String, depth: Int) {
            val names = store.list(path)
            if ("appList.txt" in names || "restore_settings.conf" in names) roots += path
            if (depth > 0) names.filter { it.startsWith("Backup_") || it.startsWith("batch-") }.forEach { child ->
                discover(listOf(path, child).filter(String::isNotEmpty).joinToString("/"), depth - 1)
            }
        }
        discover("", 2)
        val records = mutableListOf<BackupRecord>(); val rejected = mutableListOf<String>()
        roots.distinct().forEach { root ->
            store.list(root).filter { !it.endsWith(".txt") && !it.endsWith(".sh") && !it.endsWith(".conf") && !it.endsWith(".json") && !it.endsWith(".zst") && it !in setOf("tools", "log", "Media", "wifi", "communications", ".hyperbackup") }.forEach { name ->
                val directory = join(root, name); val path = "$directory/app_details.json"
                if (store.exists(path)) {
                    try { records += record(root, directory, name, path) }
                    catch (e: IllegalArgumentException) { rejected += "$directory:${e.message}" }
                }
            }
        }
        return ScanResult(header, records, rejected)
    }
    private fun record(root: String, directory: String, label: String, path: String): BackupRecord {
        val body = store.read(path).use { boundedBytes(it, 4 * 1024 * 1024) }
        val metadata = Json.parseToJsonElement(body.decodeToString()).jsonObject
        val identity = metadata.values.filterIsInstance<JsonObject>().firstOrNull { it["PackageName"] is JsonPrimitive } ?: error("SCRIPT_METADATA_IDENTITY_MISSING")
        val pkg = identity.getValue("PackageName").jsonPrimitive.content; Wire.packageName(pkg)
        val user = Regex("Backup_(?:zstd|tar)_(\\d+)").find(root)?.groupValues?.get(1)?.toIntOrNull() ?: identity["app_state"]?.jsonObject?.get("userId")?.jsonPrimitive?.intOrNull ?: -1
        val names = store.list(directory)
        val kinds = mapOf(PartKind.APK to "apk", PartKind.CE to "user", PartKind.DE to "user_de", PartKind.EXTERNAL_DATA to "data", PartKind.OBB to "obb", PartKind.MEDIA to "media")
        val parts = kinds.map { (kind, stem) ->
            val archive = listOf("$stem.tar.zst", "$stem.tar").firstOrNull { it in names }
            if (archive == null) ArchivePart(kind, PartStatus.NOT_SELECTED) else ArchivePart(kind, PartStatus.INCLUDED, archive, reason = "SCRIPT_ARCHIVE_NOT_HASH_VERIFIED")
        }.toMutableList()
        if (identity["app_state"] is JsonObject) parts += ArchivePart(PartKind.APP_STATE, PartStatus.INCLUDED, "app_details.json", reason = "UPSTREAM_CANONICAL_STATE")
        val record = BackupRecord(recordId = stableId(directory), batchId = stableId(root), origin = Origin(stableId(root), "来源设备未记录", "未知", user),
            app = AppInstance(AppKey(user, pkg), label, identity["apk_version"]?.jsonPrimitive?.contentOrNull, uid = -1),
            completion = Outcome.WARNING, createdAt = 0, parts = parts, scriptRoot = root, scriptDirectory = directory, nativeMetadataHash = Wire.hash(body))
        return record
    }
    fun validate(record: BackupRecord) {
        val path = "${record.scriptDirectory}/app_details.json".removePrefix("/")
        val bytes = store.read(path).use { boundedBytes(it, 4 * 1024 * 1024) }
        require(Wire.hash(bytes) == record.nativeMetadataHash) { "SOURCE_METADATA_CHANGED" }
    }
    companion object {
        fun stableId(value: String) = UUID.nameUUIDFromBytes(value.toByteArray()).toString()
        private fun join(a: String, b: String) = if (a.isEmpty()) b else "$a/$b"
    }
}

class PrefixStore(private val store: ObjectStore, private val prefix: String) : ObjectStore {
    private fun path(value: String) = if (value.isEmpty()) prefix else "$prefix/$value"
    override fun list(path: String) = store.list(path(path))
    override fun read(path: String) = store.read(path(path))
    override fun create(path: String, source: java.io.InputStream, length: Long) = store.create(path(path), source, length)
    override fun delete(path: String) = store.delete(path(path))
    override fun exists(path: String) = store.exists(path(path))
}
