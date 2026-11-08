package io.github.kiritoxdone.hyperbackup.engine.root

import io.github.kiritoxdone.hyperbackup.domain.*
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.concurrent.TimeUnit

data class ArchiveStats(val bytes: Long, val entries: Long)

object TarValidation {
    /** No links or device nodes. Files are fully staged under a fresh private root before any app write. */
    fun consume(input: InputStream, root: File?, byteLimit: Long, entryLimit: Long, mode: (File, Int) -> Unit = { _, _ -> }): ArchiveStats {
        require(byteLimit >= 0 && entryLimit >= 0)
        var bytes = 0L; var entries = 0L; val seen = HashSet<String>()
        TarArchiveInputStream(input).use { tar ->
            while (true) {
                val entry = tar.nextTarEntry ?: break
                val name = entry.name.removePrefix("./").trimEnd('/')
                if (name.isEmpty() || name == ".") { require(entry.isDirectory); continue }
                RepositoryPaths.validate(name)
                require(entry.linkFlag in setOf(0.toByte(), '0'.code.toByte(), '5'.code.toByte())) { "ARCHIVE_UNSUPPORTED_TYPE" }
                require(entry.isFile || entry.isDirectory) { "ARCHIVE_LINK_OR_SPECIAL_FILE" }
                require(!entry.isSparse && !entry.isSymbolicLink && !entry.isLink) { "ARCHIVE_LINK_OR_SPARSE" }
                require(seen.add(name)) { "DUPLICATE_ARCHIVE_PATH" }
                entries = Math.addExact(entries, 1); bytes = Math.addExact(bytes, entry.size)
                require(entry.size >= 0 && entries <= entryLimit && bytes <= byteLimit) { "EXPANSION_LIMIT" }
                val target = root?.resolve(name)
                if (target != null) {
                    require(target.canonicalFile == target.absoluteFile && target.path.startsWith(root.path + "/"))
                    if (entry.isDirectory) check(target.isDirectory || target.mkdirs()) else {
                        check(target.parentFile.isDirectory || target.parentFile.mkdirs())
                        Files.newOutputStream(target.toPath(), java.nio.file.StandardOpenOption.CREATE_NEW).use { out ->
                            require(tar.copyTo(out, 256 * 1024) == entry.size) { "TRUNCATED_ARCHIVE" }
                        }
                    }
                    mode(target, entry.mode and 511) // Never replay setuid/setgid bits from an archive.
                } else if (entry.isFile) {
                    var count = 0L; val buffer = ByteArray(256 * 1024)
                    while (true) { val n = tar.read(buffer); if (n < 0) break; count += n }
                    require(count == entry.size) { "TRUNCATED_ARCHIVE" }
                }
            }
        }
        return ArchiveStats(bytes, entries)
    }
}
