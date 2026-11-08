package io.github.kiritoxdone.hyperbackup.engine.root

import io.github.kiritoxdone.hyperbackup.domain.EngineFailure
import java.io.File
import java.util.concurrent.TimeUnit

data class ProcessResult(val exitCode: Int, val output: String)
class ProcessRunner(private val work: File) {
    fun run(args: List<String>, timeoutSeconds: Long = 120): ProcessResult {
        val log = File.createTempFile("process-", ".log", work)
        try {
            val p = ProcessBuilder(args).redirectErrorStream(true).redirectOutput(log).start()
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroy(); if (!p.waitFor(3, TimeUnit.SECONDS)) p.destroyForcibly()
                throw EngineFailure("PROCESS_TIMEOUT", fatal = true, uncertain = true)
            }
            val text = log.inputStream().use { input ->
                val bytes = ByteArray(minOf(log.length(), 65536).toInt()); val n = input.read(bytes)
                if (n < 0) "" else bytes.decodeToString(0, n)
            }
            return ProcessResult(p.exitValue(), text)
        } finally { log.delete() }
    }
    fun checked(args: List<String>, timeoutSeconds: Long = 120): String {
        val result = run(args, timeoutSeconds)
        if (result.exitCode != 0) throw EngineFailure("COMMAND_FAILED_${File(args[0]).name}_${result.exitCode}")
        return result.output
    }
}
