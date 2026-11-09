package io.github.kiritoxdone.hyperbackup

import android.Manifest
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.uiautomator.waitForStable

internal fun withUiTestHost(test: (UiDevice) -> Unit) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val device = UiDevice.getInstance(instrumentation)
    val config = Configurator.getInstance()
    val oldTimeout = config.waitForIdleTimeout
    config.waitForIdleTimeout = 1000
    if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }
    device.wakeUp()
    var activity: MainActivity? = null
    var wasKeepingScreenOn = false
    try {
        // OEM animations can keep Instrumentation.waitForIdleSync busy indefinitely.
        context.startActivity(Intent(context, MainActivity::class.java).putExtra("testKeepScreenOn", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        check(device.wait(Until.hasObject(By.pkg(context.packageName)), 15000)) { "APP_WINDOW_NOT_VISIBLE" }
        check(device.wait(Until.hasObject(By.pkg(context.packageName).text("HyperBackup")), 15000)) { "HOME_WINDOW_NOT_READY" }
        instrumentation.runOnMainSync {
            activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
            activity?.window?.let { window ->
                wasKeepingScreenOn = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        test(device)
    } catch (failure: Throwable) {
        if (InstrumentationRegistry.getArguments().getString("displayScenarios") == "true") {
            runCatching { device.saveUiEvidence("failure-${System.currentTimeMillis()}.xml") { device.dumpWindowHierarchy(it) } }
            runCatching { device.capturePage("failure-${System.currentTimeMillis()}") }
        }
        throw failure
    } finally {
        instrumentation.runOnMainSync {
            if (!wasKeepingScreenOn) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().forEach { it.finish() }
        }
        config.waitForIdleTimeout = oldTimeout
    }
}

internal fun UiDevice.capturePage(name: String) {
    if (InstrumentationRegistry.getArguments().getString("displayScenarios") != "true") return
    saveUiEvidence("$name.png") { check(takeScreenshot(it)) }
}

private fun UiDevice.saveUiEvidence(name: String, write: (java.io.File) -> Unit) {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val output = java.io.File(context.getExternalFilesDir(null), "ui-evidence").apply { mkdirs() }
    val file = java.io.File(output, name)
    write(file)
    // UTP uninstalls the target APK before the host can collect its external files.
    executeShellCommand("mkdir -p /sdcard/Download/HyperBackupUi")
    // UiAutomation executes argv directly; shell quotes would become part of this path.
    require(name.none { it.isWhitespace() || it == '\'' })
    executeShellCommand("cp ${file.absolutePath} /sdcard/Download/HyperBackupUi/")
}

internal fun UiDevice.textOnPage(label: String): androidx.test.uiautomator.UiObject2 {
    fun settledText(): androidx.test.uiautomator.UiObject2? {
        val node = findObject(By.text(label)) ?: return null
        val result = runCatching { node.waitForStable(stableTimeoutMs = 2000, stableIntervalMs = 300,
            requireStableScreenshot = false) }.getOrNull() ?: return null
        if (result.isTimeout) return null
        val current = findObject(By.text(label)) ?: return null
        val bounds = current.visibleBounds
        return current.takeIf { bounds.centerY() in (displayHeight * 0.10f).toInt()..(displayHeight * 0.85f).toInt() }
    }
    wait(Until.hasObject(By.scrollable(true)), 3000)
    settledText()?.let { return it }
    // The lazy list fills the scaffold behind its bars. Keep gestures in the
    // content area, and wait for semantics after scrolling instead of trusting
    // a missing TYPE_VIEW_SCROLLED event to mean the end of the list.
    for (down in listOf(true, false)) {
        repeat(10) {
            val list = findObject(By.scrollable(true)) ?: error("SCROLL_CONTAINER_NOT_FOUND:$label")
            val bounds = list.visibleBounds
            val top = maxOf(bounds.top, (displayHeight * 0.28f).toInt())
            val bottom = minOf(bounds.bottom, (displayHeight * 0.76f).toInt())
            check(bottom > top) { "SCROLL_CONTENT_OCCLUDED:$label" }
            swipe(bounds.centerX(), if (down) bottom else top, bounds.centerX(), if (down) top else bottom, 30)
            Thread.sleep(350)
            settledText()?.let { return it }
        }
    }
    error("TEXT_NOT_VISIBLE:$label")
}
