package io.github.kiritoxdone.hyperbackup.engine.storage

import io.github.kiritoxdone.hyperbackup.domain.*
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMBApiException
import java.io.InputStream
import java.net.URI
import java.util.concurrent.TimeUnit

class SmbStore(connection: Connection) : ObjectStore {
    private val uri = URI(connection.destination.address)
    private val client = SMBClient(SmbConfig.builder().withSigningRequired(true).withTimeout(60, TimeUnit.SECONDS).withSoTimeout(60_000).build())
    private val socket = client.connect(uri.host, if (uri.port == -1) 445 else uri.port)
    private val session = socket.authenticate(AuthenticationContext(connection.destination.username, connection.password.toCharArray(), connection.destination.domain))
    private val pieces = uri.path.trim('/').split('/')
    private val share = session.connectShare(pieces.first()) as DiskShare
    private val root = pieces.drop(1).joinToString("\\")
    init { require(uri.scheme == "smb" && uri.userInfo == null && uri.query == null && uri.fragment == null); RepositoryPaths.validate(pieces.joinToString("/")) }
    private fun path(path: String): String {
        RepositoryPaths.validate(path)
        return listOf(root, path.replace('/', '\\')).filter { it.isNotEmpty() }.joinToString("\\")
    }
    override fun list(path: String): List<String> = share.list(path(path)).map { it.fileName }.filter { it != "." && it != ".." }.sorted()
    override fun read(path: String): InputStream {
        val file = try { share.openFile(path(path), setOf(AccessMask.GENERIC_READ), setOf(FileAttributes.FILE_ATTRIBUTE_NORMAL), SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN, setOf(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)) }
        catch (e: SMBApiException) { if (e.statusCode in setOf(0xC0000034L, 0xC000003AL)) throw java.io.FileNotFoundException("REMOTE_NOT_FOUND") else throw e }
        return object : java.io.FilterInputStream(file.inputStream) { override fun close() { try { super.close() } finally { file.close() } } }
    }
    override fun create(path: String, source: InputStream, length: Long): Boolean {
        val target = path(path); val segments = target.split('\\')
        for (i in 1 until segments.size) {
            val parent = segments.take(i).joinToString("\\")
            if (!share.folderExists(parent)) try { share.mkdir(parent) } catch (e: SMBApiException) { if (e.statusCode != 0xC0000035L) throw e }
        }
        val file = try { share.openFile(target, setOf(AccessMask.GENERIC_WRITE), setOf(FileAttributes.FILE_ATTRIBUTE_NORMAL), SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_CREATE, setOf(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)) }
        catch (e: SMBApiException) { if (e.statusCode == 0xC0000035L) return false else throw e }
        file.use { it.outputStream.use { out -> require(source.copyTo(out, 256 * 1024) == length) }; it.flush() }
        return true
    }
    override fun exists(path: String): Boolean = share.fileExists(path(path)) || share.folderExists(path(path))
    override fun delete(path: String) { if (share.fileExists(path(path))) share.rm(path(path)) }
    override fun close() { share.close(); session.close(); socket.close(); client.close() }
}
