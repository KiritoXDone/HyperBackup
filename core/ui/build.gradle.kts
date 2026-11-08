plugins { id("com.android.library"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace = "io.github.kiritoxdone.hyperbackup.ui"
    compileSdk = 37
    defaultConfig { minSdk = 28 }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
}
dependencies {
    api("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
    api("top.yukonga.miuix.kmp:miuix-preference-android:0.9.4")
    api("top.yukonga.miuix.kmp:miuix-nav-android:0.9.4")
    api("androidx.compose.foundation:foundation:1.12.0")
    api("top.yukonga.miuix.kmp:miuix-icons-android:0.9.4")
    api(project(":core:domain"))
}
