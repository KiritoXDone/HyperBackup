package io.github.kiritoxdone.hyperbackup

import android.content.Intent
import androidx.annotation.Keep
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@Keep
@RunWith(AndroidJUnit4::class)
class UiSmokeTest {
    @Test fun largeTypeDarkModeOnIsolatedEmulator() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("displayScenarios") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val originalScale = device.executeShellCommand("settings get system font_scale").trim()
        val originalNight = device.executeShellCommand("settings get secure ui_night_mode").trim()
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        try {
            device.executeShellCommand("settings put system font_scale 2.0")
            device.executeShellCommand("cmd uimode night yes")
            withUiTestHost { device ->
                assertNotNull(device.wait(Until.findObject(By.text("首页")), 15000))
                device.findObjects(By.text("设置")).last().click()
                assertNotNull(device.wait(Until.findObject(By.text("主题")), 5000))
                assertFalse(device.hasObject(By.text("浅色")))
                device.findObject(By.text("主题")).click()
                assertNotNull(device.wait(Until.findObject(By.text("深色")), 5000))
                device.pressBack()
            }
        } finally {
            if (originalScale == "null") device.executeShellCommand("settings delete system font_scale")
            else device.executeShellCommand("settings put system font_scale $originalScale")
            val mode = when (originalNight) { "2" -> "yes"; "1" -> "no"; else -> "auto" }
            device.executeShellCommand("cmd uimode night $mode")
            if (originalNight == "null") device.executeShellCommand("settings delete secure ui_night_mode")
        }
    }
}
