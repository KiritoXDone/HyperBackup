package io.github.kiritoxdone.hyperbackup.domain

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption

class LocalObjectStore(requestedRoot: File) : ObjectStore {
    private val root = requestedRoot.canonicalFile
    init { require(root.isDirectory) { "INVALID_REPOSITORY_ROOT" } }
    private fun resolve(path: String): File {
        RepositoryPaths.validate(path)
        val file = if (path.isEmpty()) root else File(root, path)
        require(file.canonicalFile == file.absoluteFile && (file == root || file.path.startsWith(root.path + File.separator))) { "SYMLINK_OR_ESCAPE" }
        return file
    }
    override fun list(path: String): List<String> = resolve(path).list()?.sorted() ?: throw java.io.IOException("DIRECTORY_UNREADABLE")
    override fun read(path: String): InputStream = try { Files.newInputStream(resolve(path).toPath(), LinkOption.NOFOLLOW_LINKS) }
    catch (_: java.nio.file.NoSuchFileException) { throw java.io.FileNotFoundException("OBJECT_NOT_FOUND") }
    override fun create(path: String, source: InputStream, length: Long): Boolean {
        val file = resolve(path); check(file.parentFile.isDirectory || file.parentFile.mkdirs()) { "MKDIR_FAILED" }; resolve(path)
        val output = try { Files.newOutputStream(file.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS) }
        catch (_: java.nio.file.FileAlreadyExistsException) { return false }
        output.use { require(source.copyTo(it, 256 * 1024) == length) { "WRITE_LENGTH_MISMATCH" } }
        java.io.RandomAccessFile(file, "rw").use { it.fd.sync() }
        return true
    }
    override fun delete(path: String) { Files.deleteIfExists(resolve(path).toPath()) }
    override fun exists(path: String): Boolean = Files.exists(resolve(path).toPath(), LinkOption.NOFOLLOW_LINKS)
}
