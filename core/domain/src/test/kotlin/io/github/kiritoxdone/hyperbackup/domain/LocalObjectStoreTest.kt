package io.github.kiritoxdone.hyperbackup.domain

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalObjectStoreTest {
    @get:Rule val temp = TemporaryFolder()
    @Test fun exclusiveWritesAndLengthFailuresNeverOverwrite() {
        val store = LocalObjectStore(temp.newFolder())
        assertTrue(store.create("a/value", "one".byteInputStream(), 3))
        assertFalse(store.create("a/value", "two".byteInputStream(), 3))
        assertEquals("one", store.read("a/value").bufferedReader().use { it.readText() })
        assertThrows(IllegalArgumentException::class.java) { store.create("a/partial", "bad".byteInputStream(), 10) }
    }
    @Test fun childSymlinkAndTraversalCannotEscape() {
        val root = temp.newFolder(); val outside = temp.newFolder()
        java.nio.file.Files.createSymbolicLink(File(root, "link").toPath(), outside.toPath())
        val store = LocalObjectStore(root)
        assertThrows(IllegalArgumentException::class.java) { store.create("link/x", byteArrayOf(1).inputStream(), 1) }
        assertThrows(IllegalArgumentException::class.java) { store.read("../x") }
        assertFalse(File(outside, "x").exists())
    }
}
