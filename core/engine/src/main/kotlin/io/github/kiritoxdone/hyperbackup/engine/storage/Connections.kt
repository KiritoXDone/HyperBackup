package io.github.kiritoxdone.hyperbackup.engine.storage

import io.github.kiritoxdone.hyperbackup.domain.*
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class Connections {
    private val values = ConcurrentHashMap<String, Connection>()
    fun configure(connection: Connection) {
        val d = connection.destination
        Wire.uuid(d.id); d.repoId?.let(Wire::uuid)
        require(d.name.length in 1..100 && d.address.length in 1..1024)
        if (d.protocol == Protocol.LOCAL) {
            require(d.address.startsWith("/storage/") || d.address.startsWith("/mnt/media_rw/") || d.address.startsWith("/data/media/")) { "LOCAL_PATH_OUTSIDE_STORAGE" }
            require(!d.address.contains("/../") && d.address.trimEnd('/').split('/').size >= 5) { "CHOOSE_REPOSITORY_SUBDIRECTORY" }
        } else {
            val uri = URI(d.address)
            require(uri.host != null && uri.userInfo == null && uri.query == null && uri.fragment == null) { "INVALID_SERVER_ADDRESS" }
            require(if (d.protocol == Protocol.SMB) uri.scheme == "smb" else uri.scheme == "https" || uri.scheme == "http" && d.allowHttp)
            require(uri.path.split('/').none { it == ".." || it == "." })
        }
        values[d.id] = connection
    }
    fun destination(id: String) = values[id]?.destination ?: throw EngineFailure("DESTINATION_NOT_CONFIGURED", true)
    fun digest(id: String) = Wire.hash(Wire.json.encodeToString(Destination.serializer(), destination(id)).toByteArray())
    fun scriptConnection(id: String): Map<String, String> {
        val c = values[id] ?: throw EngineFailure("DESTINATION_NOT_CONFIGURED")
        return when (c.destination.protocol) {
            Protocol.LOCAL -> mapOf("remote_type" to "", "Output_path" to c.destination.address)
            Protocol.SMB -> mapOf("remote_type" to "smb", "smb_url" to c.destination.address,
                "smb_remote_user" to if (c.destination.domain.isEmpty()) c.destination.username else "${c.destination.domain}\\${c.destination.username}", "smb_remote_pass" to c.password)
            Protocol.WEBDAV -> mapOf("remote_type" to "webdav", "webdav_url" to c.destination.address,
                "webdav_remote_user" to c.destination.username, "webdav_remote_pass" to c.password)
        }
    }
    fun open(id: String, createRoot: Boolean = false): ObjectStore {
        val c = values[id] ?: throw EngineFailure("DESTINATION_NOT_CONFIGURED", true)
        return when (c.destination.protocol) {
            Protocol.LOCAL -> {
                val root = File(c.destination.address).canonicalFile
                if (createRoot) require(root.isDirectory || root.mkdirs()) { "LOCAL_DIRECTORY_CREATE_FAILED" }
                LocalObjectStore(root)
            }
            Protocol.WEBDAV -> WebDavStore(c)
            Protocol.SMB -> SmbStore(c)
        }
    }
    fun probe(store: ObjectStore) {
        val path = ".capability-${Wire.id()}"
        val a = byteArrayOf(1); val b = byteArrayOf(2)
        require(store.create(path, a.inputStream(), 1))
        try {
            require(!store.create(path, b.inputStream(), 1)) { "NO_EXCLUSIVE_CREATE" }
            require(store.read(path).use { it.read() } == 1) { "CONDITIONAL_CREATE_IGNORED" }
        } finally { store.delete(path) }
    }
}
