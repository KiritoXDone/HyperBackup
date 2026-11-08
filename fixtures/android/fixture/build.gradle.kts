plugins { id("com.android.application") version "9.4.1" }
android {
    namespace = "io.github.kiritoxdone.hyperbackup.fixture"
    compileSdk = 37
    defaultConfig {
        applicationId = namespace
        minSdk = 28; targetSdk = 37
        versionCode = providers.gradleProperty("fixtureVersion").getOrElse("1").toInt()
        versionName = "$versionCode"
    }
}
