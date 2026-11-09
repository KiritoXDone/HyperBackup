package io.github.kiritoxdone.hyperbackup

import android.content.Intent
import androidx.annotation.Keep
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import io.github.kiritoxdone.hyperbackup.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@Keep
@RunWith(AndroidJUnit4::class)
class SettingsUiTest {
    @Test(timeout = 180_000) fun defaultsPersistAndRowsKeepTheHomepageTextSize() = withUiTestHost { device ->
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = (context.applicationContext as HyperBackupApp).preferences
        val before = runBlocking { preferences.engine.first() }.backup["Backup_Mode"]
        try {
            val home = device.wait(Until.findObject(By.text("备份应用")), 10000)!!
            val homeTextHeight = home.visibleBounds.height()
            device.findObjects(By.text("设置")).last().click()
            device.textOnPage("备份默认值").click()
            val content = device.textOnPage("备份内容")
            assertTrue("Preference text was scaled up on the detail page", content.visibleBounds.height() <= homeTextHeight + 3)
            content.click()
            device.wait(Until.findObject(By.text("仅安装包")), 5000)!!.click()
            val deadline = android.os.SystemClock.uptimeMillis() + 5000
            while (runBlocking { preferences.engine.first() }.backup["Backup_Mode"] != "0" && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(50)
            assertEquals("0", runBlocking { preferences.engine.first() }.backup["Backup_Mode"])
            context.startActivity(Intent(context, MainActivity::class.java).putExtra("testKeepScreenOn", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            device.wait(Until.findObject(By.text("备份应用")), 10000)
            device.findObjects(By.text("设置")).last().click()
            device.textOnPage("备份默认值").click()
            assertNotNull(device.wait(Until.findObject(By.text("仅安装包")), 5000))
        } finally {
            runBlocking {
                val latest = preferences.engine.first()
                preferences.engine(latest.copy(backup = if (before == null) latest.backup - "Backup_Mode" else latest.backup + ("Backup_Mode" to before)))
            }
        }
    }
}
