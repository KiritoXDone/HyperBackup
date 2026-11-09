package io.github.kiritoxdone.hyperbackup

import android.content.Intent
import androidx.annotation.Keep
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@Keep
@RunWith(AndroidJUnit4::class)
class LayoutProbeTest {
    @Test(timeout = 180_000) fun mainTabsAndLocationDialog() = withUiTestHost { device ->
        assertNotNull(device.wait(Until.findObject(By.text("首页")), 10000))
        assertNotNull(device.wait(Until.findObject(By.text("HyperBackup")), 10000))
        device.capturePage("首页")
        listOf("历史" to By.text("全部"), "云端" to By.text("还没有云端位置"), "设置" to By.text("备份默认值")).forEach { (label, content) ->
            device.findObjects(By.text(label)).last().click(100)
            assertNotNull(device.wait(Until.findObject(content), 5000))
            device.capturePage(label)
            listOf("等待 Root 授权", "正在检查 Root", "Root 已连接", "Root 连接失效").forEach { status ->
                assertFalse("Root card leaked to $label", device.hasObject(By.text(status)))
            }
        }
        device.textOnPage("主题")
        assertFalse(device.hasObject(By.text("浅色")))
        device.textOnPage("主题").click(100)
        assertNotNull(device.wait(Until.findObject(By.text("浅色")), 5000))
        device.pressBack()
        device.textOnPage("关于 HyperBackup").click(100)
        assertNotNull(device.wait(Until.findObject(By.text("项目主页")), 5000))
        assertNotNull(device.findObject(By.text("问题反馈")))
        device.pressBack()
        device.findObjects(By.text("首页")).last().click()
        assertNotNull(device.wait(Until.findObject(By.text("HyperBackup")), 5000))
        val missingLocation = device.hasObject(By.text("选择备份位置"))
        device.findObject(By.text("备份")).click(100)
        assertNotNull(device.wait(Until.findObject(By.desc("应用排序")), 5000))
        device.capturePage("备份应用")
        device.findObject(By.text("文件")).click(100)
        assertNotNull(device.wait(Until.findObject(By.text("备份文件夹")), 5000))
        device.capturePage("备份文件")
        if (missingLocation) {
            device.findObject(By.clazz("android.widget.EditText")).text = "/storage/emulated/0/HyperBackupFixture/ui-layout"
            device.findObject(By.text("下一步")).click(100)
            assertNotNull(device.wait(Until.findObject(By.text("未添加备份位置")), 5000))
            assertNotNull(device.findObject(By.text("添加位置")))
            device.findObject(By.text("取消")).click(100)
        }
        device.pressBack()
        device.findObject(By.text("恢复")).click(100)
        assertNotNull(device.wait(Until.findObject(By.text("备份集")), 5000))
        device.capturePage("恢复")
    }
}
