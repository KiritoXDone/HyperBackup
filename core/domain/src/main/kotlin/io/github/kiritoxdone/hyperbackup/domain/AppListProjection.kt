package io.github.kiritoxdone.hyperbackup.domain

import java.util.Locale

enum class BackupPresence { PRESENT, ABSENT, UNKNOWN }
object AppListProjection {
    fun presence(app: AppKey, repository: ScanResult?): BackupPresence {
        if (repository == null) return BackupPresence.UNKNOWN
        if (repository.records.any { it.app.key == app && it.parts.any { part -> part.status == PartStatus.INCLUDED } }) return BackupPresence.PRESENT
        return if (repository.rejected.isNotEmpty()) BackupPresence.UNKNOWN else BackupPresence.ABSENT
    }
    fun visible(apps: List<AppInstance>, query: String, behavior: AppBehavior): List<AppInstance> {
        val filtered = apps.filter { !ProtectionPolicy.protected(it) && (behavior.showSystemApps || !it.system) &&
            (it.label.contains(query, ignoreCase = true) || it.key.packageName.contains(query, ignoreCase = true)) }
        val sorted = filtered.sortedWith(compareBy<AppInstance> {
            (if (behavior.appSort == AppSort.PACKAGE) it.key.packageName else it.label).lowercase(Locale.ROOT)
        }.thenBy { it.key.stableId })
        return if (behavior.sortDescending) sorted.reversed() else sorted
    }
}
