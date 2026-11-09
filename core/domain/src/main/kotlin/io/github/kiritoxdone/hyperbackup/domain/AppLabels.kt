package io.github.kiritoxdone.hyperbackup.domain

import kotlinx.serialization.Serializable

@Serializable data class LabelsArchive(val schema: Int = 1, val labels: Map<String, Set<String>>)

object AppLabels {
    fun validate(labels: Map<String, Set<String>>) {
        require(labels.size <= 256 && labels.values.sumOf { it.size } <= 20_000) { "标签数量过多" }
        labels.forEach { (name, members) ->
            require(name == name.trim() && name.length in 1..40 && name.none { it.isISOControl() }) { "标签名称无效" }
            members.forEach { member ->
                val user = member.substringBefore(':').toIntOrNull() ?: error("标签用户无效")
                AppKey(user, member.substringAfter(':', "")).validate()
            }
        }
    }
    fun toggle(labels: Map<String, Set<String>>, name: String, key: AppKey): Map<String, Set<String>> {
        require(name in labels)
        val old = labels.getValue(name)
        return (labels + (name to if (key.stableId in old) old - key.stableId else old + key.stableId)).also(::validate)
    }
}
