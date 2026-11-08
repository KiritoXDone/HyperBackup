package io.github.kiritoxdone.hyperbackup.engine.root

import io.github.kiritoxdone.hyperbackup.domain.*
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/** The upstream converter translates a run-private copy; the pinned payload stays immutable. */
internal object ScriptLanguage {
    fun prepare(assets: RootAssets, spec: ScriptSpec, job: File): File {
        val original = File(assets.tools, "tools.sh")
        val locale = Locale.getDefault()
        val simplified = when (spec.config["Shell_LANG"]) {
            "1" -> true
            "0" -> false
            else -> locale.language == "zh" && (locale.script == "Hans" || locale.country in setOf("CN", "SG"))
        }
        if (!simplified) return original
        val translated = File(job, "runtime-tools.sh")
        val errors = File(job, "language.log")
        val converter = ProcessBuilder("/system/bin/app_process", "-Djava.class.path=${File(assets.tools, "classes.dex").path}",
            "/system/bin", "com.xayah.dex.CCUtil", "t2s")
            .redirectInput(original).redirectOutput(translated).redirectError(errors).start()
        if (!converter.waitFor(60, TimeUnit.SECONDS)) {
            converter.destroy(); if (!converter.waitFor(2, TimeUnit.SECONDS)) converter.destroyForcibly()
            throw EngineFailure("LANGUAGE_CONVERSION_TIMEOUT")
        }
        require(converter.exitValue() == 0 && translated.length() > 0) { "LANGUAGE_CONVERSION_FAILED" }
        translated.writeText(translated.readText().replace("shell_language=\"zh-TW\"", "shell_language=\"zh-CN\""))
        ProcessRunner(job).checked(listOf("/system/bin/sh", "-n", translated.path))
        android.system.Os.chmod(translated.path, 256)
        return translated
    }
}
