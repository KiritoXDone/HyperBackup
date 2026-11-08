package io.github.kiritoxdone.hyperbackup.domain

import org.junit.Assert.*
import org.junit.Test

class ScriptScopeTest {
    @Test fun appsStayInOneBatchPerUserWithoutCrossUserSelection() {
        val selected = setOf(AppKey(0, "com.example.one"), AppKey(0, "com.example.two"), AppKey(10, "com.example.one"))
        val draft = ScriptPlanScope.backup(Wire.id(), selected, 0)
        assertEquals(2, draft.items.size)
        val specs = draft.items.map { ScriptPlanScope.forItem(requireNotNull(draft.script), it) }
        assertEquals(listOf("com.example.one", "com.example.two"), specs[0].selectedPackages)
        assertEquals(listOf("com.example.one"), specs[1].selectedPackages)
        assertEquals(10, specs[1].userId)
        assertTrue(specs.all { it.setMode == BackupSetMode.NEW })
    }
    @Test fun anEmptyMappedUserCannotTurnIntoAnUnrestrictedSelection() {
        val spec = ScriptSpec(ScriptCommand.BACKUP_APPS, "", 0, selectedApps = listOf(AppKey(0, "com.example.one")))
        assertThrows(IllegalArgumentException::class.java) {
            ScriptPlanScope.forItem(spec, PlanItem(AppKey(10, "io.github.kiritoxdone.hyperbackup.scope"), parts = setOf(PartKind.CE), group = true))
        }
    }
    @Test fun literalConfigEscapesCommandSubstitutionAndRejectsUnknownKeys() {
        val value = "a\"\\$" + "(touch /data/example)`id`\nnext"
        val encoded = ScriptConfiguration.encode(mapOf("blacklist" to value), setOf("blacklist"))
        assertTrue(encoded.contains("\\$" + "(touch")); assertTrue(encoded.contains("\\`id\\`"))
        assertThrows(IllegalArgumentException::class.java) { ScriptConfiguration.encode(mapOf("evil" to "value"), setOf("blacklist")) }
    }
}
