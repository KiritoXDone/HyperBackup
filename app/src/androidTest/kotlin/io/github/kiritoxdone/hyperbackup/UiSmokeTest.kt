package io.github.kiritoxdone.hyperbackup

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiSmokeTest {
    @Test fun standaloneLaunchFourTabsAndLargeTypeDarkMode() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<MainActivity>(intent).use {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                device.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
                device.pressBack()
                context.startActivity(intent)
            }
            assertNotNull(device.wait(Until.findObject(By.text("首页")), 15000))
            listOf("应用", "备份库", "设置").forEach { label ->
                val nodes = device.findObjects(By.text(label)); assertTrue(nodes.isNotEmpty()); nodes.last().click()
                device.waitForIdle()
            }
            assertNotNull(device.wait(Until.findObject(By.text("备份位置管理")), 5000))
            device.executeShellCommand("settings put system font_scale 2.0")
            device.executeShellCommand("cmd uimode night yes")
            device.waitForIdle()
            context.startActivity(intent)
            assertNotNull(device.wait(Until.findObject(By.text("设置")), 10000))
            device.executeShellCommand("settings put system font_scale 1.0")
            device.executeShellCommand("cmd uimode night no")
        }
    }
}
