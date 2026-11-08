package io.github.kiritoxdone.hyperbackup.domain

/** Preserve the upstream per-user batch: associated data is not repeated for every app. */
object ScriptPlanScope {
    private const val GROUP_PACKAGE = "io.github.kiritoxdone.hyperbackup.scope"
    fun backup(destinationId: String, apps: Set<AppKey>, currentUser: Int, config: Map<String, String> = emptyMap()): Draft {
        require(apps.isNotEmpty()); apps.forEach { it.validate() }
        val groups = apps.map { it.userId }.distinct().sorted().map { user ->
            PlanItem(AppKey(user, GROUP_PACKAGE), parts = setOf(PartKind.APK, PartKind.CE, PartKind.DE), group = true)
        }
        return Draft(Operation.BACKUP, destinationId, groups,
            script = ScriptSpec(ScriptCommand.BACKUP_APPS, "", currentUser, config = config, selectedApps = apps.sortedBy { it.stableId }))
    }
    fun forItem(spec: ScriptSpec, item: PlanItem): ScriptSpec {
        val packages = if (spec.selectedApps.isNotEmpty()) spec.selectedApps.filter { it.userId == item.app.userId }.map { it.packageName } else spec.selectedPackages
        require(spec.selectedApps.isEmpty() || packages.isNotEmpty()) { "EMPTY_USER_SCOPE" }
        return spec.copy(userId = item.app.userId, selectedPackages = if (!item.group && item.app.packageName != GROUP_PACKAGE) listOf(item.app.packageName) else packages,
            workingDirectory = item.scriptRoot ?: spec.workingDirectory)
    }
}
