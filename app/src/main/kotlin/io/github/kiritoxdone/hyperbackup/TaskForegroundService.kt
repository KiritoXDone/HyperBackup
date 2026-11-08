package io.github.kiritoxdone.hyperbackup

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import io.github.kiritoxdone.hyperbackup.domain.*
import io.github.kiritoxdone.hyperbackup.ui.label
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class TaskForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var taskId: String? = null
    private var observing: Job? = null
    private lateinit var wakeLock: PowerManager.WakeLock
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("tasks", "备份与恢复任务", NotificationManager.IMPORTANCE_LOW))
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HyperBackup:task")
        wakeLock.setReferenceCounted(false)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, notification("正在确认任务状态"))
        intent?.getStringExtra("taskId")?.let { taskId = it }
        if (intent?.action == "stop") scope.launch { taskId?.let { (application as HyperBackupApp).session.stop(it) } }
        if (observing?.isActive != true) observing = scope.launch {
            val session = (application as HyperBackupApp).session; val began = System.currentTimeMillis()
            while (isActive) {
                wakeLock.acquire(90_000)
                try {
                    if (taskId == null) taskId = session.pendingSubmissions().firstOrNull()?.taskId
                    val id = taskId
                    if (id != null) {
                        val task = session.status(id)
                        if (task.state !in setOf(TaskState.RUNNING, TaskState.STOP_REQUESTED)) {
                            val notifyCompletion = (application as HyperBackupApp).preferences.behavior.first().completionNotification
                            if (notifyCompletion) {
                                getSystemService(NotificationManager::class.java).notify(1, notification("${task.plan.draft.script?.command?.label ?: task.plan.draft.operation.label()}${task.state.label()}", running = false))
                                stopForeground(STOP_FOREGROUND_DETACH)
                            } else stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf(); break
                        }
                        session.heartbeat(id)
                        val finished = task.items.count { it.outcome !in setOf(Outcome.PENDING, Outcome.RUNNING) }
                        getSystemService(NotificationManager::class.java).notify(1, notification("${task.state.label()} · 项目 $finished/${task.items.size}"))
                    } else if (System.currentTimeMillis() - began > 120_000) { stopSelf(); break }
                } catch (_: Exception) {
                    getSystemService(NotificationManager::class.java).notify(1, notification("连接中断，任务结果待核对"))
                    if (System.currentTimeMillis() - began > 120_000) { stopSelf(); break }
                }
                delay(15_000)
            }
        }
        return START_NOT_STICKY
    }
    private fun notification(text: String, running: Boolean = true): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).putExtra("taskId", taskId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, TaskForegroundService::class.java).setAction("stop").putExtra("taskId", taskId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, "tasks").setSmallIcon(R.drawable.ic_notification).setContentTitle("HyperBackup").setContentText(text)
            .setContentIntent(open).setOngoing(running).setAutoCancel(!running)
        if (running) builder.addAction(Notification.Action.Builder(null, "停止任务", stop).build())
        return builder.build()
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        scope.launch { runCatching { taskId?.let { (application as HyperBackupApp).session.stop(it) } } }
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    override fun onDestroy() { observing?.cancel(); scope.cancel(); if (wakeLock.isHeld) wakeLock.release(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        fun prepare(context: Context) { context.startForegroundService(Intent(context, TaskForegroundService::class.java)) }
        fun observe(context: Context, id: String) { context.startForegroundService(Intent(context, TaskForegroundService::class.java).putExtra("taskId", id)) }
    }
}
