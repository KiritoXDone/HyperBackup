# The opt-in fixture instrumentation calls this public API from a separate test APK.
# UI and dependency code still run through the same optimizing R8 pipeline.
-keep class io.github.kiritoxdone.hyperbackup.engine.EngineGateway { public *; }
-keep class io.github.kiritoxdone.hyperbackup.domain.** { public *; }
# The test runner also links Kotlin facade classes through the target APK.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
