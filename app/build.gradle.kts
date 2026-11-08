plugins { id("com.android.application"); id("org.jetbrains.kotlin.plugin.compose"); id("org.jetbrains.kotlin.plugin.serialization") }
android {
    namespace = "io.github.kiritoxdone.hyperbackup"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.kiritoxdone.hyperbackup"
        minSdk = 28; targetSdk = 37; versionCode = 1; versionName = "0.1.0-preview.1"
        ndk { abiFilters += if (providers.gradleProperty("ciUi").isPresent) listOf("arm64-v8a", "x86_64") else listOf("arm64-v8a") }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    signingConfigs {
        val keyPath = providers.environmentVariable("HYPERBACKUP_KEYSTORE_PATH").orNull
        if (keyPath != null) create("hyperbackup") {
            storeFile = file(keyPath)
            storePassword = providers.environmentVariable("HYPERBACKUP_KEYSTORE_PASSWORD").get()
            keyAlias = "hyperbackup"; keyPassword = storePassword
        }
    }
    buildTypes {
        configureEach {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("debug").proguardFiles("proguard-debug.pro")
        signingConfigs.findByName("hyperbackup")?.let { signing ->
            getByName("release").signingConfig = signing
            getByName("debug").signingConfig = signing
        }
    }
    sourceSets.getByName("main").assets.srcDir(rootProject.file("licenses"))
    compileOptions { sourceCompatibility = JavaVersion.VERSION_21; targetCompatibility = JavaVersion.VERSION_21 }
    packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/INDEX.LIST") }
}
dependencies {
    implementation(project(":core:domain")); implementation(project(":core:data"))
    implementation(project(":core:engine")); implementation(project(":core:ui"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
    androidTestImplementation("com.google.errorprone:error_prone_annotations:2.50.0")
}
