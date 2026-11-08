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

internal fun withUiTestHost(test: (UiDevice) -> Unit) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val device = UiDevice.getInstance(instrumentation)
    val config = Configurator.getInstance()
    val oldTimeout = config.waitForIdleTimeout
    config.waitForIdleTimeout = 0
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
        instrumentation.runOnMainSync {
            activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
            activity?.window?.let { window ->
                wasKeepingScreenOn = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        test(device)
    } finally {
        instrumentation.runOnMainSync {
            if (!wasKeepingScreenOn) activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        config.waitForIdleTimeout = oldTimeout
    }
}

internal fun UiDevice.textOnPage(label: String): androidx.test.uiautomator.UiObject2 {
    wait(Until.findObject(By.text(label)), 1200)?.let { return it }
    repeat(8) {
        val list = findObject(By.scrollable(true)) ?: error("SCROLL_CONTAINER_NOT_FOUND:$label")
        if (!list.scroll(androidx.test.uiautomator.Direction.DOWN, 0.65f)) {
            findObject(By.text(label))?.let { return it }
            error("TEXT_NOT_VISIBLE:$label")
        }
        findObject(By.text(label))?.let { return it }
    }
    error("TEXT_NOT_VISIBLE:$label")
}
