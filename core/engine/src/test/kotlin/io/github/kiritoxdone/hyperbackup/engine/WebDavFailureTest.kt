package io.github.kiritoxdone.hyperbackup.engine

import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.engine.storage.WebDavStore
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class WebDavFailureTest {
    private fun store(server: MockWebServer, http: Boolean = true) = WebDavStore(Connection(Destination(name = "test", protocol = Protocol.WEBDAV,
        address = server.url("/").toString(), allowHttp = http), "secret"))
    @Test fun plainHttpRequiresExplicitSelection() {
        MockWebServer().use { server -> server.start(); assertThrows(IllegalArgumentException::class.java) { store(server, false) } }
    }
    @Test fun unauthorizedAndServerErrorsAreNotEmptyRepositories() {
        MockWebServer().use { server -> server.start()
            listOf(401, 403, 500, 507).forEach { code ->
                server.enqueue(MockResponse().setResponseCode(code))
                store(server).use { assertThrows(EngineFailure::class.java) { it.list("") } }
            }
        }
    }
    @Test fun noRedirectOrCredentialForwardingAndConditionalWrite() {
        MockWebServer().use { server -> server.start()
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "http://127.0.0.1:1/"))
            store(server).use { assertThrows(EngineFailure::class.java) { it.list("") } }
            assertEquals(1, server.requestCount)
            server.enqueue(MockResponse().setResponseCode(412))
            store(server).use { assertFalse(it.create("claim", byteArrayOf(1).inputStream(), 1)) }
            server.takeRequest(); val request = server.takeRequest()
            assertEquals("*", request.getHeader("If-None-Match"))
        }
    }
    @Test fun xxEntityAndForeignHrefsRejected() {
        MockWebServer().use { server -> server.start()
            val bodies = listOf("<!DOCTYPE x [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><x>&xxe;</x>",
                "<d:multistatus xmlns:d='DAV:'><d:response><d:href>http://foreign.example/backup</d:href></d:response></d:multistatus>")
            bodies.forEach { body -> server.enqueue(MockResponse().setResponseCode(207).setBody(body)); store(server).use { assertThrows(Exception::class.java) { it.list("") } } }
        }
    }
}
