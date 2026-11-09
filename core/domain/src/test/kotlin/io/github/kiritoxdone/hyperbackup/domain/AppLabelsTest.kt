package io.github.kiritoxdone.hyperbackup.domain

import org.junit.Assert.*
import org.junit.Test

class AppLabelsTest {
    @Test fun membershipKeepsAndroidUsersSeparate() {
        val owner = AppKey(0, "io.github.kiritoxdone.hyperbackup.fixture")
        val work = owner.copy(userId = 10)
        val selected = AppLabels.toggle(mapOf("测试" to setOf(owner.stableId)), "测试", work)
        assertEquals(setOf(owner.stableId, work.stableId), selected["测试"])
        assertEquals(setOf(work.stableId), AppLabels.toggle(selected, "测试", owner)["测试"])
    }
    @Test fun importedMembershipCannotContainInvalidUsersOrPackages() {
        listOf("-1:com.example.app", "0:../escape", "invalid", "0:com.example.app\ncommand").forEach { member ->
            assertThrows(Exception::class.java) { AppLabels.validate(mapOf("测试" to setOf(member))) }
        }
        assertThrows(Exception::class.java) { AppLabels.validate(mapOf(" 测试 " to emptySet())) }
    }
    @Test fun olderPreferencesKeepTheirDefaults() {
        val behavior = Wire.json.decodeFromString<AppBehavior>("{\"showPackageNames\":false}")
        assertTrue(behavior.labels.isEmpty())
        assertFalse(behavior.dynamicColor)
        assertFalse(behavior.showPackageNames)
    }
}
