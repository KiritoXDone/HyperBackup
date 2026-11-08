package io.github.kiritoxdone.hyperbackup.engine.root

import io.github.kiritoxdone.hyperbackup.domain.*
import java.io.File

class Reconciliation(private val dex: UpstreamDex, private val root: File) {
    fun inspect(task: TaskSnapshot): List<String> {
        val result = mutableListOf("检查于 ${System.currentTimeMillis()}；不根据安装信息推断数据恢复成功。")
        task.items.filter { it.app.packageName != ScriptPlans.SCOPE }.forEach { item ->
            result += runCatching {
                val installed = dex.installed(item.app)
                "${item.app.stableId}: " + if (installed == null) "当前未安装" else "当前 UID=${installed.uid}，versionCode=${installed.versionCode}"
            }.getOrElse { "${item.app.stableId}: 无法核验用户/安装状态" }
        }
        controls(task).forEach { control ->
            result += "${control.parentFile.name}: 原进程${if (alive(control)) "仍在运行" else "已不匹配"}；收尾标记 ${File(control, "cleanup").takeIf { it.isFile }?.readText()?.trim() ?: "缺失"}"
        }
        return result
    }
    fun cleanupAcknowledged(task: TaskSnapshot) {
        require(task.state == TaskState.NEEDS_RECONCILIATION)
        controls(task).forEach { control ->
            if (alive(control)) throw EngineFailure("ORIGINAL_ENGINE_STILL_RUNNING", true, true)
            val cleaned = File(control, "cleanup").takeIf { it.isFile }?.readText()?.trim() == "1"
            if (!cleaned) throw EngineFailure("UPSTREAM_CLEANUP_NOT_CONFIRMED", true, true)
        }
    }
    private fun controls(task: TaskSnapshot): List<File> = File(root, "native-jobs").listFiles().orEmpty()
        .filter { it.name.startsWith(task.id + "-") }.flatMap { it.listFiles().orEmpty().toList() }.map { File(it, "control") }.filter { it.isDirectory }
    private fun alive(control: File): Boolean {
        val pid = File(control, "process-pid").takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull() ?: return false
        val boot = File(control, "process-boot").takeIf { it.isFile }?.readText()?.trim() ?: return true
        val start = File(control, "process-start").takeIf { it.isFile }?.readText()?.trim() ?: return true
        if (boot != File("/proc/sys/kernel/random/boot_id").readText().trim()) return false
        val stat = File("/proc/$pid/stat").takeIf { it.isFile }?.readText() ?: return false
        return stat.substringAfterLast(") ").split(' ').getOrNull(19) == start
    }
}
