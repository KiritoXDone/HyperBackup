import java.security.MessageDigest
plugins { id("com.android.library") }
android {
    namespace = "io.github.kiritoxdone.hyperbackup.engine"
    compileSdk = 37
    defaultConfig { minSdk = 28 }
    buildFeatures { aidl = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
val generateBridge = tasks.register<Exec>("generateBridge") {
    commandLine("python3", rootProject.file("engine/patches/generate.py"), layout.buildDirectory.dir("bridge").get().asFile)
    inputs.files(rootProject.file("engine/patches/generate.py"), rootProject.file("engine/upstream/backup_script/tools/tools.sh"), rootProject.file("engine/generated/speednative"))
    outputs.dir(layout.buildDirectory.dir("bridge"))
}
val bundleEngine = tasks.register<Copy>("bundleEngine") {
    dependsOn(generateBridge)
    val source = rootProject.file("engine/upstream/backup_script/tools")
    from(source) { include("classes.dex", "tar", "zstd", "busybox", "find", "cmd", "dex_check.sh", "keycheck", "smbclient", "soc.json") }
    into(file("src/main/assets/upstream"))
    from(rootProject.file("engine/upstream/SHA256SUMS"))
    from(rootProject.file("engine/generated")) { include("speednative", "provenance.json") }
    from(layout.buildDirectory.dir("bridge"))
    from(rootProject.file("engine/patches")) { include("*.sh") }
    from(rootProject.file("engine/upstream/backup_script/webview/app/src/main/assets")) { include("settings-labels.json", "settings-options.json") }
    doLast {
        val directory = file("src/main/assets/upstream")
        val hashes = directory.listFiles()!!.filter { it.isFile && it.name != "PAYLOAD-SHA256SUMS" }.sortedBy { it.name }.joinToString("\n", postfix = "\n") {
            MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString("") { byte -> "%02x".format(byte) } + "  " + it.name
        }
        directory.resolve("PAYLOAD-SHA256SUMS").writeText(hashes)
    }
    doFirst {
        check(rootProject.file("engine/generated/speednative").isFile) { "Build the native host adapter: python3 engine/patches/build_native.py" }
        val expected = rootProject.file("engine/upstream/SHA256SUMS").readLines().filter { it.isNotBlank() }
        expected.forEach { line ->
            val (hash, name) = line.split("  ", limit = 2)
            val actual = MessageDigest.getInstance("SHA-256").digest(source.resolve(name).readBytes()).joinToString("") { "%02x".format(it) }
            check(actual == hash) { "Upstream payload differs: $name" }
        }
    }
}
tasks.named("preBuild") { dependsOn(bundleEngine) }
dependencies {
    api(project(":core:domain"))
    api("com.github.topjohnwu.libsu:core:6.0.0")
    api("com.github.topjohnwu.libsu:service:6.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.hierynomus:smbj:0.14.0")
    implementation("org.apache.commons:commons-compress:1.27.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
