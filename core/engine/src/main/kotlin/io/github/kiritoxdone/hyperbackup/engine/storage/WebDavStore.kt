package io.github.kiritoxdone.hyperbackup.engine.storage

import io.github.kiritoxdone.hyperbackup.domain.*
import okhttp3.*
import io.github.kiritoxdone.hyperbackup.domain.Connection
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

class WebDavStore(connection: Connection) : ObjectStore {
    private val base = (connection.destination.address.trimEnd('/') + "/").toHttpUrl()
    private val auth = Credentials.basic(connection.destination.username, connection.password)
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()
    init {
        require(base.username.isEmpty() && base.password.isEmpty() && base.query == null && base.fragment == null)
        require(base.isHttps || connection.destination.allowHttp) { "HTTP_NOT_EXPLICITLY_ALLOWED" }
    }
    private fun url(path: String): HttpUrl {
        RepositoryPaths.validate(path)
        return base.newBuilder().apply { if (path.isNotEmpty()) path.split('/').forEach { addPathSegment(it) } }.build()
    }
    private fun request(path: String, method: String, body: RequestBody? = null, headers: Map<String, String> = emptyMap()): Response {
        val req = Request.Builder().url(url(path)).header("Authorization", auth).method(method, body)
        headers.forEach { (k, v) -> req.header(k, v) }
        try { return client.newCall(req.build()).execute() }
        catch (e: java.io.IOException) { throw EngineFailure("WEBDAV_TRANSPORT", true, cause = e) }
    }
    private fun check(response: Response) {
        if (!response.isSuccessful) throw when (response.code) {
            404 -> FileNotFoundException("REMOTE_NOT_FOUND")
            401, 403 -> EngineFailure("REMOTE_AUTH_OR_PERMISSION", true)
            507 -> EngineFailure("REMOTE_FULL", true)
            else -> EngineFailure("WEBDAV_HTTP_${response.code}", true)
        }
    }
    override fun list(path: String): List<String> {
        val body = "<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:resourcetype/></d:prop></d:propfind>".toRequestBody("application/xml".toMediaType())
        request(path, "PROPFIND", body, mapOf("Depth" to "1")).use { response ->
            check(response)
            val bytes = response.body!!.byteStream().use { boundedBytes(it, 8 * 1024 * 1024) }
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true; setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                isExpandEntityReferences = false
            }
            val nodes = factory.newDocumentBuilder().parse(bytes.inputStream()).getElementsByTagNameNS("DAV:", "href")
            val parent = url(path).encodedPath.trimEnd('/') + "/"
            return (0 until nodes.length).mapNotNull { index ->
                val href = URI(nodes.item(index).textContent)
                require(!href.isAbsolute || href.host == base.host) { "DAV_FOREIGN_HREF" }
                val raw = href.rawPath.trimEnd('/')
                if (!raw.startsWith(parent)) null else raw.removePrefix(parent).takeIf { it.isNotEmpty() && '/' !in it }?.let {
                    val decoded = URI("/$it").path.removePrefix("/"); RepositoryPaths.validate(decoded); require('/' !in decoded); decoded
                }
            }.distinct().sorted()
        }
    }
    override fun read(path: String): InputStream {
        val response = request(path, "GET")
        try { check(response) } catch (e: Exception) { response.close(); throw e }
        return object : java.io.FilterInputStream(response.body!!.byteStream()) { override fun close() { super.close(); response.close() } }
    }
    override fun create(path: String, source: InputStream, length: Long): Boolean {
        RepositoryPaths.validate(path)
        val pieces = path.split('/')
        for (i in 1 until pieces.size) {
            request(pieces.take(i).joinToString("/"), "MKCOL", ByteArray(0).toRequestBody()).use {
                if (it.code != 405) check(it)
            }
        }
        val body = object : RequestBody() {
            override fun contentType() = "application/octet-stream".toMediaType()
            override fun contentLength() = length
            override fun isOneShot() = true
            override fun writeTo(sink: BufferedSink) {
                val buffer = ByteArray(256 * 1024); var total = 0L
                while (true) { val n = source.read(buffer); if (n < 0) break; total += n; require(total <= length); sink.write(buffer, 0, n) }
                require(total == length) { "SOURCE_LENGTH" }
            }
        }
        request(path, "PUT", body, mapOf("If-None-Match" to "*")).use {
            if (it.code == 412) return false
            check(it); return true
        }
    }
    override fun exists(path: String): Boolean = request(path, "PROPFIND", ByteArray(0).toRequestBody(), mapOf("Depth" to "0")).use {
        if (it.code == 404) false else { check(it); true }
    }
    override fun delete(path: String) { request(path, "DELETE").use { if (it.code != 404) check(it) } }
    override fun close() { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
}

internal fun boundedBytes(input: InputStream, limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n <= limit) { "PAYLOAD_LIMIT" }; out.write(buffer, 0, n) }
    return out.toByteArray()
}
