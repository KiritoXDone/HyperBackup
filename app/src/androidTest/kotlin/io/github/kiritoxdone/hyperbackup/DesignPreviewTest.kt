package io.github.kiritoxdone.hyperbackup

import androidx.activity.compose.setContent
import androidx.annotation.Keep
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.engine.ScriptCatalogReader
import io.github.kiritoxdone.hyperbackup.feature.*
import io.github.kiritoxdone.hyperbackup.ui.HyperTheme
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Render-only emulator fixtures. They are not persisted and execute no engine commands. */
@Keep
@RunWith(AndroidJUnit4::class)
class DesignPreviewTest {
    @Test(timeout = 240_000) fun populatedPagesKeepTheApprovedHierarchy() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("displayScenarios") == "true")
        withUiTestHost { device ->
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            lateinit var activity: MainActivity
            lateinit var model: AppViewModel
            instrumentation.runOnMainSync {
                activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().first()
                model = ViewModelProvider(activity)[AppViewModel::class.java]
            }
            val local = Destination(name = "本地测试备份", protocol = Protocol.LOCAL, address = "/storage/emulated/0/HyperBackupFixture/ui")
            val remote = Destination(name = "测试服务器", protocol = Protocol.WEBDAV, address = "https://fixture.invalid/backups")
            val apps = (1..6).map { index -> AppInstance(AppKey(0, "io.github.kiritoxdone.hyperbackup.fixture.app$index"),
                listOf("测试笔记", "浏览器测试夹具", "测试日历", "长名称应用布局测试夹具", "测试照片编辑器", "天气测试")[index - 1], "1", "1.0", 10000 + index) }
            val batch = Wire.id()
            val records = apps.map { app -> BackupRecord(batchId = batch, origin = Origin(Wire.id(), "UI fixture", "17", 0), app = app,
                completion = Outcome.SUCCEEDED, parts = listOf(ArchivePart(PartKind.CE, PartStatus.INCLUDED, "data.tar.zst", bytes = 4096))) }
            val draft = ScriptPlanScope.backup(local.id, apps.take(2).map { it.key }.toSet(), 0)
            val historyDraft = ScriptPlanScope.backup(local.id, apps.map { it.key }.toSet() + AppKey(10, "io.github.kiritoxdone.hyperbackup.fixture.work"), 0)
            val plan = Plan(draft = historyDraft, repoId = Wire.id())
            val task = TaskSnapshot(id = Wire.id(), requestId = Wire.id(), plan = plan, digest = plan.digest(), bootId = "fixture", sessionId = "fixture",
                state = TaskState.PARTIAL, startedAt = System.currentTimeMillis() - 252000, finishedAt = System.currentTimeMillis(),
                items = historyDraft.items.mapIndexed { i, item -> ItemResult(item.app, if (i == 1) Outcome.FAILED else Outcome.SUCCEEDED,
                    reason = if (i == 1) "测试夹具：目标空间不足" else "") })
            val state = AppState(capabilities = Capabilities(bootId = "fixture", sessionId = "fixture", androidVersion = "17", abi = "x86_64",
                users = listOf(AndroidUser(0, "主用户", true), AndroidUser(10, "工作用户", true)), limitations = emptyList()),
                apps = apps, selectedApps = apps.take(2).map { it.key }.toSet(), selectedRecords = records.take(2).map { it.recordId }.toSet(),
                destinations = listOf(local, remote), destinationId = local.id, destinationState = DestinationState.READY,
                connectionState = RootConnectionState.READY, connectionTests = mapOf(remote.id to DestinationState.READY),
                repository = ScanResult(RepositoryHeader(), records, emptyList()), tasks = listOf(task), draft = draft,
                storage = DeviceStorage(128L shl 30, 66L shl 30), behavior = AppBehavior(labels = mapOf("测试工具" to setOf(apps.first().key.stableId))),
                scriptCatalog = ScriptCatalogReader.fromAssets(activity))
            instrumentation.runOnMainSync { activity.setContent { HyperTheme("light") { AppNavigation(model, state, null) } } }
            device.waitForIdle(1000)
            device.capturePage("fixture-首页")
            listOf("历史", "云端", "设置").forEach { label ->
                device.findObjects(By.text(label)).last().click(100)
                device.waitForIdle(1000)
                device.capturePage("fixture-$label")
            }
            val cases = listOf(
                Preview("备份", "应用", { AppsPage(state, model, it, true, {}, onFiles = {}) }, bottom = {
                    SelectionActions(state, model, false, "", apps.map { it.key }) {} }),
                Preview("应用详情", "备份内容", { AppDetailPage(state, model, it, apps.first().key, {}) }, showTitle = false),
                Preview("备份设置", "保存位置", { ConfigPage(state, model, it, {}) }, bottom = { BottomAction("检查并继续", true) {} }),
                Preview("恢复", "备份集 · 1", { LibraryPage(state, model, it, {}) }),
                Preview("选择恢复内容", "已选 2 / 6", { BatchPage(state, model, it, batch, {}) }),
                Preview("部分完成", "处理结果", { TaskPage(state, model, it, task.id, {}) }),
                Preview("正在执行", "任务队列", { TaskPage(state.copy(tasks = listOf(task.copy(state = TaskState.RUNNING, finishedAt = null,
                    items = task.items.mapIndexed { i, result -> if (i == 1) result.copy(outcome = Outcome.RUNNING, phase = "BACKUP_CE") else if (i > 1) result.copy(outcome = Outcome.PENDING) else result }))), model, it, task.id, {}) }),
                Preview("标签与黑名单", "我的标签", { LabelsPage(state, model, it, {}) }),
            )
            cases.forEach { case ->
                instrumentation.runOnMainSync { activity.setContent { HyperTheme("light") { PageShell(case.title, back = {}, showTitle = case.showTitle, bottom = case.bottom, content = case.content) } } }
                assertNotNull(device.wait(Until.findObject(By.text(case.marker)), 10000))
                device.waitForIdle(1000)
                device.capturePage("fixture-${case.title}")
            }
        }
    }
    private data class Preview(val title: String, val marker: String, val content: @Composable (PaddingValues) -> Unit,
        val showTitle: Boolean = true, val bottom: @Composable () -> Unit = {})
}
