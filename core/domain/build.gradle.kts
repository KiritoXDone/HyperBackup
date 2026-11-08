plugins { id("org.jetbrains.kotlin.jvm"); id("org.jetbrains.kotlin.plugin.serialization") }
kotlin { jvmToolchain(21) }
dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("junit:junit:4.13.2")
}
