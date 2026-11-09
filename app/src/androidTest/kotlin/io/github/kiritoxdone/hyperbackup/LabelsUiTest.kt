package io.github.kiritoxdone.hyperbackup

import androidx.annotation.Keep
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@Keep
@RunWith(AndroidJUnit4::class)
class LabelsUiTest {
    @Test(timeout = 180_000) fun labelsPersistAndCloudFormUsesRealProtocols() = withUiTestHost { device ->
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = (context.applicationContext as HyperBackupApp).preferences
        val before = runBlocking { preferences.behavior.first() }
        val name = "HyperBackup UI fixture"
        try {
            device.findObjects(By.text("设置")).last().click(100)
            device.textOnPage("标签与黑名单").click(100)
            device.wait(Until.findObject(By.desc("添加标签")), 5000)!!.click(100)
            device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)!!.text = name
            device.findObject(By.text("添加")).click(100)
            assertNotNull(device.wait(Until.findObject(By.text(name)), 5000))
            assertTrue(name in runBlocking { preferences.behavior.first() }.labels)
            device.capturePage("标签")
            device.pressBack()
            device.findObjects(By.text("云端")).last().click(100)
            device.wait(Until.findObject(By.desc("添加云端位置")), 5000)!!.click(100)
            assertNotNull(device.wait(Until.findObject(By.text("WEBDAV")), 5000))
            assertNotNull(device.findObject(By.text("SMB")))
            assertFalse(device.hasObject(By.text("SFTP")))
            device.capturePage("添加云端位置")
        } finally {
            runBlocking { preferences.behavior(before) }
        }
    }
}
