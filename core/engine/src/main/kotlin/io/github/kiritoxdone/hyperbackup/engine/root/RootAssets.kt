package io.github.kiritoxdone.hyperbackup.engine.root

import android.content.Context
import android.system.Os
import dalvik.system.DexClassLoader
import io.github.kiritoxdone.hyperbackup.domain.*
import java.io.File

class RootAssets(context: Context, val root: File) {
    val tools = File(root, "engine-${Wire.UPSTREAM}")
    val loader: ClassLoader
    init {
        require(android.os.Process.myUid() == 0) { "ROOT_REQUIRED" }
        check(root.isDirectory || root.mkdirs()); require(root.canonicalFile == root); Os.chmod(root.path, 448)
        check(tools.isDirectory || tools.mkdirs()); Os.chmod(tools.path, 448)
        for (line in context.assets.open("upstream/PAYLOAD-SHA256SUMS").bufferedReader().use { it.readLines() }) {
            val (hash, name) = line.split("  ", limit = 2)
            require(name.matches(Regex("[A-Za-z0-9._-]+")))
            val target = File(tools, name)
            require(target.canonicalFile == target)
            if (!target.isFile || FileDigests.sha256(target) != hash) {
                val temp = File(tools, "$name.new")
                context.assets.open("upstream/$name").use { input -> temp.outputStream().use { input.copyTo(it) } }
                require(FileDigests.sha256(temp) == hash) { "ENGINE_HASH_MISMATCH" }
                Os.chmod(temp.path, if (name.endsWith(".dex")) 256 else 448)
                Os.rename(temp.path, target.path)
            }
        }
        val link = File(tools, "cgfreezer")
        if (!link.exists()) Os.symlink(File(tools, "speednative").path, link.path)
        require(link.canonicalFile == File(tools, "speednative"))
        val run = File(root, "guard").also { it.mkdirs(); Os.chmod(it.path, 448) }
        Os.setenv("SPEEDBACKUP_RUN_TMPDIR", run.path, true)
        loader = DexClassLoader(File(tools, "classes.dex").path, tools.path, null, javaClass.classLoader)
        val capabilities = ProcessBuilder(binary("speednative"), "--capabilities").start()
        val reply = capabilities.inputStream.bufferedReader().use { it.readText() }
        require(capabilities.waitFor() == 0 && "speednative.hyperbackup_host.v1" in reply) { "HOST_ADAPTER_BINARY_REQUIRED" }
    }
    fun binary(name: String): String { require(name in setOf("tar", "zstd", "speednative")); return File(tools, name).path }
}
