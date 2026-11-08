package io.github.kiritoxdone.hyperbackup.engine

import android.content.Context
import io.github.kiritoxdone.hyperbackup.domain.*
import kotlinx.serialization.json.*

object ScriptCatalogReader {
    fun fromAssets(context: Context): ScriptCatalog = read { name -> context.assets.open("upstream/$name").bufferedReader().use { it.readText() } }
    fun read(text: (String) -> String): ScriptCatalog {
        val defaults = Wire.json.decodeFromString<Map<String, Map<String, String>>>(text("config-defaults.json"))
        val labels = Json.parseToJsonElement(text("settings-labels.json")).jsonObject.getValue("labels").jsonObject
        val options = Json.parseToJsonElement(text("settings-options.json")).jsonObject
        val keys = text("config-keys.txt").lineSequence().filter(String::isNotBlank).toList()
        return ScriptCatalog(backupDefaults = defaults.getValue("backup"), restoreDefaults = defaults.getValue("restore"),
            fields = keys.map { key -> ScriptField(key, labels[key]?.jsonPrimitive?.content ?: key,
                options[key]?.jsonArray?.map { row -> row.jsonArray.map { it.jsonPrimitive.content } }.orEmpty(), key.endsWith("_pass")) })
    }
}
