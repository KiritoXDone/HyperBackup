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
        listOf("应用" to "用户应用", "备份库" to "备份集", "设置" to "备份默认值").forEach { (label, content) ->
            val node = device.findObjects(By.text(label)).last()
            println("TAB $label bounds=${node.visibleBounds}")
            node.click()
            assertNotNull(device.wait(Until.findObject(By.textStartsWith(content)), 5000))
            listOf("等待 Root 授权", "正在检查 Root", "Root 已连接", "Root 连接失效").forEach { status ->
                assertFalse("Root card leaked to $label", device.hasObject(By.text(status)))
            }
        }
        device.textOnPage("主题")
        assertFalse(device.hasObject(By.text("浅色")))
        device.textOnPage("主题").click()
        assertNotNull(device.wait(Until.findObject(By.text("浅色")), 5000))
        device.pressBack()
        device.textOnPage("关于").click()
        assertNotNull(device.wait(Until.findObject(By.text("项目主页")), 5000))
        assertNotNull(device.findObject(By.text("问题反馈")))
        device.pressBack()
        device.findObjects(By.text("首页")).last().click()
        assertNotNull(device.wait(Until.findObject(By.text("备份文件夹")), 5000))
        if (device.hasObject(By.text("选择备份位置"))) {
            device.findObject(By.text("备份文件夹")).click()
            assertNotNull(device.wait(Until.findObject(By.text("未添加备份位置")), 5000))
            assertNotNull(device.findObject(By.text("添加位置")))
            assertFalse(device.hasObject(By.text("收起提示")))
            device.findObject(By.text("取消")).click()
        }
    }
}
