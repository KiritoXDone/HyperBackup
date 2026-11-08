package io.github.kiritoxdone.hyperbackup

import androidx.annotation.Keep
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.engine.EngineGateway
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in physical-device test; the only mutable app is the disposable fixture package. */
@Keep
@RunWith(AndroidJUnit4::class)
class RootFixtureTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    @Test fun nativeBackupRestoreWithSafetyCopy() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("rootFixture") == "true")
        val context = instrumentation.targetContext
        assertNotNull(context.packageManager.getPackageInfo(FIXTURE, 0))
        val gateway = EngineGateway(context)
        gateway.connect()
        val caps = gateway.capabilities()
        assertEquals(Wire.UPSTREAM, caps.engine)
        assertTrue(caps.users.any { it.id == 0 && it.unlocked })
        val id = Wire.id()
        val destination = Destination(name = "Disposable fixture", protocol = Protocol.LOCAL,
            address = "/storage/emulated/0/HyperBackupFixture/$id")
        gateway.configure(Connection(destination)); gateway.initialize(destination.id)
        suspend fun seed(value: String) {
            require(value.matches(Regex("[a-z0-9]+")))
            shell("am force-stop $FIXTURE")
            shell("am start -W -n $FIXTURE/.FixtureActivity --es seed $value")
            repeat(20) { if (shell("run-as $FIXTURE cat files/value.txt").trim() == value) return; delay(250) }
            fail("Fixture seed was not written")
        }
        suspend fun execute(draft: Draft): TaskSnapshot {
            val plan = gateway.preflight(draft)
            assertTrue(plan.blockers.joinToString(), plan.blockers.isEmpty())
            val request = Submit(Wire.id(), Wire.id(), plan.id, plan.digest())
            gateway.submit(request)
            repeat(300) {
                gateway.heartbeat(request.taskId)
                val state = gateway.status(request.taskId)
                if (state.prompt != null || state.editor != null) {
                    gateway.stop(request.taskId)
                    fail("Unexpected prompt; no unplanned choice was approved: ${state.prompt?.title ?: state.editor?.mode}")
                }
                if (state.state !in setOf(TaskState.RUNNING, TaskState.STOP_REQUESTED)) return state
                delay(2000)
            }
            gateway.stop(request.taskId); error("Fixture task timed out; stop requested")
        }
        seed("alpha")
        val draft = ScriptPlanScope.backup(destination.id, setOf(AppKey(0, FIXTURE)), 0, mapOf(
            "backup_messages" to "0", "backup_calllogs" to "0", "backup_media" to "0", "Custom_path" to "",
            "low_battery_mode" to "2", "Backup_Mode" to "1", "Backup_user_data" to "1", "Backup_obb_data" to "1",
            "setDisplayPowerMode" to "0", "pc_auto_shutdown" to "0", "Background_apps_ignore" to "0"))
        val backup = execute(draft.copy(script = requireNotNull(draft.script).copy(associatedWifi = false)))
        assertTrue(Wire.json.encodeToString(TaskSnapshot.serializer(), backup), backup.state == TaskState.SUCCEEDED)
        val records = gateway.scan(destination.id).records.filter { it.app.key.packageName == FIXTURE }
        assertEquals(1, records.size)
        val record = records.single()
        seed("beta")
        val source = destination.address + "/" + record.scriptRoot.orEmpty()
        val restore = Draft(Operation.RESTORE, destination.id,
            listOf(PlanItem(AppKey(0, FIXTURE), parts = setOf(PartKind.APK, PartKind.CE, PartKind.DE, PartKind.EXTERNAL_DATA),
                scriptRoot = source, scriptDirectory = record.scriptDirectory, nativeMetadataHash = record.nativeMetadataHash)),
            safetyCopy = true, script = ScriptSpec(ScriptCommand.RESTORE_APPS, source, 0,
                config = mapOf("restore_messages" to "0", "restore_calllogs" to "0", "media_recovery" to "0", "low_battery_mode" to "2", "setDisplayPowerMode" to "0", "pc_auto_shutdown" to "0"),
                selectedPackages = listOf(FIXTURE), associatedWifi = false))
        val restored = execute(restore)
        assertTrue(Wire.json.encodeToString(TaskSnapshot.serializer(), restored), restored.state == TaskState.SUCCEEDED)
        assertNotNull(restored.items.single().safetyBackupPath)
        assertEquals("alpha", shell("run-as $FIXTURE cat files/value.txt").trim())
        shell("am start -W -n $FIXTURE/.FixtureActivity")
        assertTrue(shell("run-as $FIXTURE cat shared_prefs/fixture.xml").contains("alpha"))
        assertEquals(2, gateway.scan(destination.id).records.count { it.app.key.packageName == FIXTURE })
        context.getExternalFilesDir("test-evidence")!!.resolve("root-fixture-result.json")
            .writeText(Wire.json.encodeToString(TaskSnapshot.serializer(), restored))
    }
    companion object { const val FIXTURE = "io.github.kiritoxdone.hyperbackup.fixture" }
}
