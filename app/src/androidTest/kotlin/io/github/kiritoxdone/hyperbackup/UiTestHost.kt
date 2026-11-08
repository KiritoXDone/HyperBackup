package io.github.kiritoxdone.hyperbackup

import android.Manifest
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice

internal fun withUiTestHost(test: (UiDevice) -> Unit) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val device = UiDevice.getInstance(instrumentation)
    val config = Configurator.getInstance()
    val oldTimeout = config.waitForIdleTimeout
    config.waitForIdleTimeout = 0
    if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
    device.wakeUp()
    try {
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            test(device)
        }
    } finally {
        config.waitForIdleTimeout = oldTimeout
    }
}
