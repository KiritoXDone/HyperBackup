package io.github.kiritoxdone.hyperbackup.domain

import org.junit.Assert.*
import org.junit.Test

class ScriptSafetyCopyTest {
    @Test fun wifiAndFoldersAreNotSkippedForHavingNoAppPackages() {
        val wifi = ScriptSafetyCopy.specification(ScriptSpec(ScriptCommand.RESTORE_WIFI, "/storage/source", 0), emptyList())!!
        assertEquals(ScriptCommand.BACKUP_WIFI, wifi.command)
        assertThrows(IllegalStateException::class.java) {
            ScriptSafetyCopy.specification(ScriptSpec(ScriptCommand.RESTORE_FOLDERS, "/storage/source", 0), emptyList())
        }
    }
    @Test fun appCopyCapturesOnlyInstalledTargetsIntoANewSet() {
        val source = ScriptSpec(ScriptCommand.RESTORE_APPS, "/storage/source", 10, setMode = BackupSetMode.UPDATE,
            existingSet = "Backup_zstd_10_old", selectedPackages = listOf("com.example.installed", "com.example.missing"))
        val backup = ScriptSafetyCopy.specification(source, listOf("com.example.installed"))!!
        assertEquals(listOf("com.example.installed"), backup.selectedPackages)
        assertEquals(BackupSetMode.NEW, backup.setMode)
        assertEquals("", backup.existingSet)
        assertFalse(backup.associatedWifi)
        assertEquals("0", backup.config["backup_messages"])
        assertEquals("0", backup.config["backup_calllogs"])
        assertNull(ScriptSafetyCopy.specification(source, emptyList()))
    }
}
