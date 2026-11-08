package io.github.kiritoxdone.hyperbackup.engine

import io.github.kiritoxdone.hyperbackup.engine.root.TarValidation
import org.apache.commons.compress.archivers.tar.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream

class ArchiveValidationTest {
    @get:Rule val temp = TemporaryFolder()
    private fun tar(entries: List<Pair<TarArchiveEntry, ByteArray>>): ByteArray {
        val output = ByteArrayOutputStream()
        TarArchiveOutputStream(output).use { tar -> entries.forEach { (entry, bytes) -> tar.putArchiveEntry(entry); if (bytes.isNotEmpty()) tar.write(bytes); tar.closeArchiveEntry() } }
        return output.toByteArray()
    }
    private fun file(name: String, data: String) = TarArchiveEntry(name).also { it.size = data.length.toLong() } to data.toByteArray()
    @Test fun stageAndCountActualContents() {
        val bytes = tar(listOf(file("files/data.txt", "abc")))
        val target = temp.newFolder().canonicalFile
        val result = TarValidation.consume(bytes.inputStream(), target, 3, 1)
        assertEquals(3, result.bytes); assertEquals("abc", target.resolve("files/data.txt").readText())
    }
    @Test fun pathTraversalNeverEscapes() {
        val bytes = tar(listOf(file("../outside", "bad")))
        val target = temp.newFolder().canonicalFile
        assertThrows(IllegalArgumentException::class.java) { TarValidation.consume(bytes.inputStream(), target, 3, 1) }
        assertFalse(target.parentFile.resolve("outside").exists())
    }
    @Test fun linksSpecialFilesDuplicatesAndBombsFail() {
        val link = TarArchiveEntry("link", TarConstants.LF_SYMLINK).also { it.linkName = "/data/system" }
        val hard = TarArchiveEntry("hard", TarConstants.LF_LINK).also { it.linkName = "target" }
        listOf(link, hard, TarArchiveEntry("device", TarConstants.LF_CHR)).forEach {
            val bytes = tar(listOf(it to byteArrayOf()))
            assertThrows(IllegalArgumentException::class.java) { TarValidation.consume(bytes.inputStream(), null, 100, 10) }
        }
        val duplicate = tar(listOf(file("a", "a"), file("a", "b")))
        assertThrows(IllegalArgumentException::class.java) { TarValidation.consume(duplicate.inputStream(), null, 2, 2) }
        val oversized = tar(listOf(file("a", "large")))
        assertThrows(IllegalArgumentException::class.java) { TarValidation.consume(oversized.inputStream(), null, 4, 1) }
        assertThrows(IllegalArgumentException::class.java) { TarValidation.consume(oversized.inputStream(), null, 5, 0) }
    }
}
