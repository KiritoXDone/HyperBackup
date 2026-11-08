package io.github.kiritoxdone.hyperbackup.domain

import org.junit.Assert.*
import org.junit.Test

class PolicyTest {
    @Test fun protectionAndResultPolicies() {
        assertTrue(ProtectionPolicy.protected(AppInstance(AppKey(0, Wire.HOST), "", "1", uid = 10001)))
        assertTrue(ProtectionPolicy.freezeCandidate("com.tencent.mm", false, "", ""))
        assertFalse(ProtectionPolicy.freezeCandidate("com.example.app", false, "", ""))
        assertEquals(Outcome.PARTIAL, Results.combine(listOf(PartResult(PartKind.APK, Outcome.FAILED, "conflict"), PartResult(PartKind.CE, Outcome.SUCCEEDED, "verified"))))
    }
    @Test fun rejectDuplicateTargetsAndUnsafePaths() {
        val key = AppKey(0, "com.example.fixture"); val item = PlanItem(key, parts = setOf(PartKind.CE))
        assertThrows(IllegalArgumentException::class.java) { PlanPolicy.validate(Draft(Operation.BACKUP, Wire.id(), listOf(item, item))) }
        listOf("../x", "/x", "a/../../b", "a\\b", "a//b", "a/./b").forEach {
            assertThrows(IllegalArgumentException::class.java) { RepositoryPaths.validate(it) }
        }
    }
}
