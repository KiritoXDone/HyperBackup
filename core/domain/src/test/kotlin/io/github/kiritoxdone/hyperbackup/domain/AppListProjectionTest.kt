package io.github.kiritoxdone.hyperbackup.domain

import org.junit.Assert.*
import org.junit.Test

class AppListProjectionTest {
    private val app = AppInstance(AppKey(0, "com.example.fixture"), "Fixture", "1", uid = 10001)
    @Test fun missingOrRejectedScanDoesNotMeanNoBackup() {
        assertEquals(BackupPresence.UNKNOWN, AppListProjection.presence(app.key, null))
        assertEquals(BackupPresence.UNKNOWN, AppListProjection.presence(app.key, ScanResult(RepositoryHeader(), emptyList(), listOf("unreadable"))))
        assertEquals(BackupPresence.ABSENT, AppListProjection.presence(app.key, ScanResult(RepositoryHeader(), emptyList(), emptyList())))
    }
    @Test fun badgeRequiresThisUserAndAnActualIncludedPart() {
        val record = BackupRecord(batchId = Wire.id(), origin = Origin(Wire.id(), "fixture", "16", 0), app = app,
            completion = Outcome.WARNING, parts = listOf(ArchivePart(PartKind.CE, PartStatus.INCLUDED, "user.tar.zst")))
        val scan = ScanResult(RepositoryHeader(), listOf(record), emptyList())
        assertEquals(BackupPresence.PRESENT, AppListProjection.presence(app.key, scan))
        assertEquals(BackupPresence.ABSENT, AppListProjection.presence(app.key.copy(userId = 10), scan))
    }
    @Test fun filterAndSortKeepExecutionHostsExcluded() {
        val other = app.copy(key = AppKey(0, "com.example.another"), label = "Another")
        val host = app.copy(key = AppKey(0, Wire.HOST))
        val rows = AppListProjection.visible(listOf(app, other, host), "", AppBehavior(sortDescending = true))
        assertEquals(listOf(app, other), rows)
        assertEquals(listOf(app), AppListProjection.visible(listOf(app, other, host), "FIXTURE", AppBehavior()))
    }
}
