# The opt-in fixture instrumentation calls this public API from a separate test APK.
# UI and dependency code still run through the same optimizing R8 pipeline.
-keep class io.github.kiritoxdone.hyperbackup.engine.EngineGateway { public *; }
-keep class io.github.kiritoxdone.hyperbackup.domain.** { public *; }
# The test runner also links Kotlin facade classes through the target APK.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
# Settings instrumentation reads/restores the same DataStore after UI edits.
-keep class io.github.kiritoxdone.hyperbackup.data.Preferences { public *; }
-keepclassmembers class io.github.kiritoxdone.hyperbackup.HyperBackupApp {
    public io.github.kiritoxdone.hyperbackup.data.Preferences getPreferences();
}
# The render-only instrumentation obtains the same Activity-owned ViewModel.
-keepclassmembers class androidx.lifecycle.ViewModelProvider {
    public <init>(androidx.lifecycle.ViewModelStoreOwner);
    public androidx.lifecycle.ViewModel get(java.lang.Class);
}
