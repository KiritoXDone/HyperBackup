package io.github.kiritoxdone.hyperbackup.domain

object ProtectionPolicy {
    private val hosts = setOf(Wire.HOST, "com.topjohnwu.magisk", "me.weishu.kernelsu", "me.weishu.kernelsu.next", "com.rifsxd.ksunext", "com.sukisu.ultra", "me.bmax.apatch")
    private val freeze = setOf("com.tencent.mm", "com.tencent.mobileqq", "com.tencent.tim", "com.tencent.wework", "com.eg.android.AlipayGphone", "com.taobao.taobao", "com.ss.android.ugc.aweme")
    fun protected(app: AppInstance): Boolean = app.key.packageName in hosts || app.uid % 100000 < 10000 || app.protectedReason != null
    fun freezeCandidate(pkg: String, restore: Boolean, home: String, ime: String): Boolean =
        pkg in freeze || pkg.startsWith("com.baidu.input") || pkg == home || pkg == ime || (restore && pkg == "com.google.android.googlequicksearchbox")
}

object PlanPolicy {
    fun validate(draft: Draft) {
        Wire.uuid(draft.destinationId); Wire.uuid(draft.safetyDestinationId)
        require(draft.items.isNotEmpty() && draft.items.size <= 10000) { "EMPTY_OR_TOO_LARGE" }
        require(draft.items.map { it.app }.distinct().size == draft.items.size) { "DUPLICATE_TARGET" }
        draft.items.forEach {
            it.app.validate(); require(it.parts.isNotEmpty()) { "NO_PARTS" }
            if (draft.operation != Operation.BACKUP && draft.script == null) require(it.record != null) { "MISSING_RECORD" }
            it.record?.let { ref -> Wire.uuid(ref.batchId); Wire.uuid(ref.recordId); require(ref.digest.matches(Regex("[0-9a-f]{64}"))) }
        }
    }
    fun ready(plan: Plan, now: Long) {
        PlanPolicy.validate(plan.draft)
        require(plan.blockers.isEmpty()) { "PREFLIGHT_BLOCKED" }
        require(now - plan.checkedAt in 0..300_000) { "PREFLIGHT_EXPIRED" }
        require(plan.engine == Wire.UPSTREAM) { "ENGINE_CHANGED" }
    }
}

object Results {
    fun combine(parts: List<PartResult>): Outcome {
        if (parts.any { it.outcome == Outcome.UNKNOWN }) return Outcome.UNKNOWN
        if (parts.isEmpty()) return Outcome.SKIPPED
        val failed = parts.count { it.outcome == Outcome.FAILED || it.outcome == Outcome.PARTIAL }
        val good = parts.count { it.outcome == Outcome.SUCCEEDED || it.outcome == Outcome.WARNING || it.outcome == Outcome.SKIPPED }
        return when {
            failed > 0 && good > 0 -> Outcome.PARTIAL
            failed > 0 -> Outcome.FAILED
            parts.any { it.outcome == Outcome.WARNING } -> Outcome.WARNING
            parts.all { it.outcome == Outcome.SKIPPED } -> Outcome.SKIPPED
            else -> Outcome.SUCCEEDED
        }
    }
}
