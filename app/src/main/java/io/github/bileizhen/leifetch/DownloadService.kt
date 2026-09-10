package io.github.bileizhen.leifetch

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import io.github.bileizhen.leifetch.nsfx.NsfxConfig
import io.github.bileizhen.leifetch.nsfx.NsfxKernel
import kotlinx.coroutines.*

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var kernel: NsfxKernel
    private lateinit var wake: PowerManager.WakeLock
    override fun onCreate() {
        super.onCreate()
        val config = app.settings.state.value
        kernel = NsfxKernel(this, NsfxConfig(threads = config.threads, maxConcurrentTasks = config.maxTasks,
            globalMaxConnections = config.connections, globalSpeedLimit = config.speedLimit,
            enableDynamicSegments = config.dynamic)) {
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
            stopSelf()
        }
        wake = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LeiFetch:NSFX").apply { setReferenceCounted(false) }
        scope.launch { while (isActive) { wake.acquire(10 * 60_000L); delay(5 * 60_000L) } }
        scope.launch {
            while (isActive) {
                delay(1000)
                if (kernel.isRunning) Notices.update(this@DownloadService, app.store.tasks.value.firstOrNull { it.state == "下载中" }
                    ?: app.store.tasks.value.firstOrNull { it.state == "保存中" || it.state == "排队中" })
            }
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra("id")
        if (id == null) { stopSelf(startId); return START_NOT_STICKY }
        when (intent.getStringExtra("op") ?: "start") {
            "start" -> {
                if (Build.VERSION.SDK_INT >= 29) startForeground(Notices.ACTIVE_ID, Notices.progress(this, null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                else startForeground(Notices.ACTIVE_ID, Notices.progress(this, null))
                kernel.startDownload(id)
                Notices.refreshCandidate(this, exclude = id)
            }
            "pause" -> kernel.pauseDownload(id)
            "cancel" -> { kernel.cancelDownload(id); Notices.refreshCandidate(this) }
            "ignore" -> {
                // 同步执行：紧随其后的 stopSelf 会取消协程作用域。
                runCatching {
                    if (app.store.get(id)?.state == "待确认") {
                        workDir(this, id).deleteRecursively()
                        app.store.update(id) { it.copy(state = "已取消", headers = emptyMap(), url = "", speed = 0) }
                    }
                }
                Notices.refreshCandidate(this, exclude = id)
            }
        }
        if (!kernel.isRunning) stopSelf(startId)
        return START_NOT_STICKY
    }
    override fun onTimeout(startId: Int, fgsType: Int) {
        kernel.stop()
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else stopForeground(true)
        stopSelf()
    }
    override fun onDestroy() {
        kernel.stop(); scope.cancel()
        if (wake.isHeld) wake.release()
        super.onDestroy()
    }
}
