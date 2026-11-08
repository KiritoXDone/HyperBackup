package io.github.kiritoxdone.hyperbackup.engine

import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.engine.storage.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Exercises real protocols and script-layout indexing, without App-only commit markers. */
class RemoteRepositoryTest {
    @Test fun webdavLifecycle() {
        val url = System.getenv("HYPERBACKUP_TEST_DAV")
        assumeTrue("CI WebDAV fixture not configured", url != null)
        WebDavStore(Connection(Destination(name = "ci", protocol = Protocol.WEBDAV, address = requireNotNull(url), username = "ci", allowHttp = true), "fixture-only")).use { lifecycle(it) }
    }
    @Test fun smbLifecycle() {
        val url = System.getenv("HYPERBACKUP_TEST_SMB")
        assumeTrue("CI Samba fixture not configured", url != null)
        SmbStore(Connection(Destination(name = "ci", protocol = Protocol.SMB, address = requireNotNull(url), username = "ci"), "fixture-only")).use { lifecycle(it) }
    }
    private fun lifecycle(store: ObjectStore) {
        fun put(path: String, content: String) { val bytes = content.toByteArray(); assertTrue(store.create(path, bytes.inputStream(), bytes.size.toLong())) }
        Connections().probe(store)
        for (suffix in listOf("first", "second")) {
            val root = "Backup_zstd_0_$suffix"
            put("$root/appList.txt", "Fixture com.example.fixture\n")
            put("$root/Fixture/app_details.json", """{"Fixture":{"PackageName":"com.example.fixture","apk_version":"1"},"user":{"Size":"12 B"}}""")
            put("$root/Fixture/user.tar.zst", "fixture-only-payload")
        }
        val scan = NativeRepository(store).scan()
        assertEquals(2, scan.records.size)
        assertTrue(scan.records.all { it.nativeMetadataHash != null && it.scriptRoot != null })
        assertFalse(store.exists("repository.json"))
        assertFalse(store.exists("Backup_zstd_0_first/Fixture/COMMITTED.json"))
        store.delete("Backup_zstd_0_first/Fixture/user.tar.zst")
        store.delete("Backup_zstd_0_first/Fixture/app_details.json")
        assertEquals(1, NativeRepository(store).scan().records.size)
        assertTrue(store.exists("Backup_zstd_0_second/Fixture/user.tar.zst"))
    }
}
