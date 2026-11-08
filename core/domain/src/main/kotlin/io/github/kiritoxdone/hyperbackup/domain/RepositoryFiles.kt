package io.github.kiritoxdone.hyperbackup.domain

import java.io.File
import java.io.InputStream
import kotlinx.serialization.Serializable

interface ObjectStore : AutoCloseable {
    fun list(path: String): List<String>
    fun read(path: String): InputStream
    /** False only means a proven pre-existing object; exceptions may mean an unknown commit. */
    fun create(path: String, source: InputStream, length: Long): Boolean
    fun delete(path: String)
    fun exists(path: String): Boolean
    override fun close() {}
}

object RepositoryPaths {
    fun validate(path: String): String {
        require(path.length <= 512 && !path.startsWith('/') && '\\' !in path && '\u0000' !in path)
        require(path.isEmpty() || path.split('/').all { it.isNotEmpty() && it != "." && it != ".." }) { "UNSAFE_PATH" }
        return path
    }
    fun record(ref: RecordRef): String { Wire.uuid(ref.batchId); Wire.uuid(ref.recordId); return "batches/${ref.batchId}/apps/${ref.recordId}" }

}

@Serializable data class ScanResult(val repository: RepositoryHeader, val records: List<BackupRecord>, val rejected: List<String>, val scannedAt: Long = System.currentTimeMillis())

object FileDigests {
        fun sha256(file: File): String = file.inputStream().use { input ->
            val digest = java.security.MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(256 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }; digest.digest().joinToString("") { "%02x".format(it) }
        }
}
