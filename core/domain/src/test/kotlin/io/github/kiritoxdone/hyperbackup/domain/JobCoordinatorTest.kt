package io.github.kiritoxdone.hyperbackup.domain

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class JobCoordinatorTest {
    @get:Rule val temp = TemporaryFolder()
    private val key = AppKey(0, "com.example.fixture")
    private fun plan(count: Int = 1) = Plan(draft = Draft(Operation.BACKUP, Wire.id(), (0 until count).map { PlanItem(key.copy(packageName = "com.example.fixture$it"), parts = setOf(PartKind.CE)) }), repoId = Wire.id())
    private fun request(p: Plan) = Submit(Wire.id(), Wire.id(), p.id, p.digest())
    @Test fun duplicateResponseAndPayloadConflict() {
        val pool = Executors.newSingleThreadExecutor(); val gate = CountDownLatch(1); var executions = 0
        val p = plan(); val r = request(p); val journal = FileTaskJournal(temp.newFolder())
        val executor = object : ItemExecutor {
            override fun begin(task: TaskSnapshot) { gate.await(3, TimeUnit.SECONDS) }
            override fun execute(task: TaskSnapshot, index: Int, progress: (String, Boolean) -> Unit): ItemResult { executions++; return ItemResult(key, Outcome.SUCCEEDED) }
            override fun finish(task: TaskSnapshot) {}
        }
        val owner = JobCoordinator(journal, executor, "boot", "session", pool)
        owner.submit(r, p); owner.submit(r, p)
        assertThrows(IllegalArgumentException::class.java) { owner.submit(r.copy(digest = "changed"), p) }
        assertThrows(IllegalStateException::class.java) { owner.submit(request(p), p) }
        gate.countDown(); pool.shutdown(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        assertEquals(1, executions); assertEquals(TaskState.SUCCEEDED, owner.status(r.taskId).state)
    }
    @Test fun unknownCommitStopsBatchAndNeverReplaysOnRecreation() {
        val journal = FileTaskJournal(temp.newFolder()); val pool = Executors.newSingleThreadExecutor(); var count = 0
        val executor = object : ItemExecutor {
            override fun begin(task: TaskSnapshot) {}
            override fun execute(task: TaskSnapshot, index: Int, progress: (String, Boolean) -> Unit): ItemResult {
                count++; progress("INSTALL_COMMIT", true); throw EngineFailure("INSTALL_UNKNOWN", true, true)
            }
            override fun finish(task: TaskSnapshot) {}
        }
        val p = plan(2); val r = request(p); val owner = JobCoordinator(journal, executor, "boot", "session", pool)
        owner.submit(r, p); pool.shutdown(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        assertEquals(1, count); assertEquals(TaskState.NEEDS_RECONCILIATION, owner.status(r.taskId).state)
        assertEquals(Outcome.CANCELLED, owner.status(r.taskId).items[1].outcome)
        val replacement = JobCoordinator(journal, executor, "newboot", "newsession")
        assertEquals(TaskState.NEEDS_RECONCILIATION, replacement.status(r.taskId).state); assertEquals(1, count)
        assertThrows(IllegalStateException::class.java) { replacement.submit(request(p), p) }
    }
    @Test fun deviceRestartTurnsAcceptedWriteIntoUnknown() {
        val journal = FileTaskJournal(temp.newFolder()); val p = plan(); val r = request(p)
        journal.write(TaskSnapshot(id = r.taskId, requestId = r.requestId, plan = p, digest = p.digest(), bootId = "old", sessionId = "old",
            items = listOf(ItemResult(key, Outcome.RUNNING, "RESTORE_DATA", potentiallyWritten = true))))
        val never = object : ItemExecutor {
            override fun begin(task: TaskSnapshot) { fail("must not replay") }
            override fun execute(task: TaskSnapshot, index: Int, progress: (String, Boolean) -> Unit) = error("must not replay")
            override fun finish(task: TaskSnapshot) {}
        }
        val restored = JobCoordinator(journal, never, "new", "new")
        assertEquals(Outcome.UNKNOWN, restored.status(r.taskId).items.single().outcome)
        assertEquals(TaskState.NEEDS_RECONCILIATION, restored.status(r.taskId).state)
    }
    @Test fun stopWaitsForCurrentItemAndKeepsCompletedResults() {
        val pool = Executors.newSingleThreadExecutor(); val entered = CountDownLatch(1); val finish = CountDownLatch(1)
        val journal = FileTaskJournal(temp.newFolder())
        val executor = object : ItemExecutor {
            override fun begin(task: TaskSnapshot) {}
            override fun execute(task: TaskSnapshot, index: Int, progress: (String, Boolean) -> Unit): ItemResult {
                entered.countDown(); finish.await(3, TimeUnit.SECONDS); return ItemResult(key, Outcome.SUCCEEDED)
            }
            override fun finish(task: TaskSnapshot) {}
        }
        val owner = JobCoordinator(journal, executor, "boot", "session", pool); val p = plan(2); val r = request(p)
        owner.submit(r, p); assertTrue(entered.await(3, TimeUnit.SECONDS)); owner.requestStop(r.taskId)
        assertEquals(TaskState.STOP_REQUESTED, owner.status(r.taskId).state)
        finish.countDown(); pool.shutdown(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        val result = owner.status(r.taskId)
        assertEquals(TaskState.CANCELLED, result.state); assertEquals(Outcome.SUCCEEDED, result.items[0].outcome); assertEquals(Outcome.CANCELLED, result.items[1].outcome)
    }
    @Test fun cleanupJournalFailureStillReleasesTheActiveOwner() {
        val disk = FileTaskJournal(temp.newFolder())
        var failWrites = false
        val journal = object : TaskJournal by disk {
            override fun write(task: TaskSnapshot) {
                if (failWrites) throw java.io.IOException("disk unavailable")
                disk.write(task)
            }
        }
        val pool = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable).apply { uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, _ -> } }
        }
        val executor = object : ItemExecutor {
            override fun begin(task: TaskSnapshot) {}
            override fun execute(task: TaskSnapshot, index: Int, progress: (String, Boolean) -> Unit) = ItemResult(key, Outcome.SUCCEEDED)
            override fun finish(task: TaskSnapshot) { failWrites = true; error("cleanup failed") }
        }
        val owner = JobCoordinator(journal, executor, "boot", "session", pool)
        val p = plan(); val r = request(p)
        owner.submit(r, p); pool.shutdown(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        failWrites = false
        disk.write(owner.status(r.taskId).copy(state = TaskState.NEEDS_RECONCILIATION))
        // A stale active owner used to block explicit acknowledgment forever.
        assertEquals(TaskState.FAILED, owner.acknowledge(r.taskId).state)
    }

}
