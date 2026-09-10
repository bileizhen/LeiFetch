// Android Kotlin adaptation of nsfx_kernel.dart; CaptureProvider replaces the desktop HTTP bridge.
// SPDX-License-Identifier: GPL-3.0-only
package io.github.bileizhen.leifetch.nsfx

import android.content.Context
import io.github.bileizhen.leifetch.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class DownloadStatistics(val active: Int = 0, val pending: Int = 0, val completed: Int = 0, val speed: Long = 0)

class NsfxKernel(private val context: Context, config: NsfxConfig, private val onIdle: () -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engine = NsfxDownloadEngine(context, config)
    private val publisher = FilePublisher(context)
    private val slots = Semaphore(config.maxConcurrentTasks.coerceIn(1, 8))
    private val jobs = mutableMapOf<String, Job>()
    private val requested = mutableMapOf<String, String>()
    private val finished = MutableSharedFlow<Task>(extraBufferCapacity = 32)
    val onComplete = finished.asSharedFlow()
    val onProgress = context.app.store.tasks
    val statistics = onProgress.map { tasks -> DownloadStatistics(
        tasks.count { it.state == "下载中" || it.state == "保存中" }, tasks.count { it.state == "排队中" },
        tasks.count { it.state == "已完成" }, tasks.sumOf { it.speed })
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), DownloadStatistics())
    val isRunning get() = jobs.isNotEmpty()
    fun startDownload(id: String) {
        if (jobs.containsKey(id)) return
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                context.app.ready.await()
                val initial = context.app.store.get(id) ?: return@launch
                if (initial.state in setOf("已完成", "已取消")) return@launch
                withContext(Dispatchers.IO) { context.app.store.update(id) { it.copy(state = "排队中", error = "") } }
                slots.withPermit { runTask(id) }
            } catch (e: CancellationException) {
                // Keep the entire cleanup non-cancellable: returning from IO to an already
                // cancelled Main context would otherwise skip the durable state update.
                withContext(NonCancellable) {
                    val durable = withContext(Dispatchers.IO) {
                        context.app.store.get(id)?.let { runCatching { publisher.recover(it) }.getOrNull() }
                    }
                    if (durable != null) {
                        markComplete(id, durable.first, durable.second)
                    } else {
                        val state = requested[id] ?: "已暂停"
                        withContext(Dispatchers.IO) {
                            context.app.store.update(id) {
                                if (it.state == "已完成") it else it.copy(state = state, speed = 0,
                                    headers = if (state == "已取消") emptyMap() else it.headers,
                                    url = if (state == "已取消") "" else it.url)
                            }
                            if (state == "已取消") { workDir(context, id).deleteRecursively(); context.app.telemetry.clear(id) }
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.IO) { context.app.store.update(id) { it.copy(state = "失败", speed = 0,
                    error = "${e.javaClass.simpleName}：${e.message?.take(140).orEmpty()}") } }
            } finally {
                jobs.remove(id); requested.remove(id)
                if (jobs.isEmpty()) onIdle()
            }
        }
        jobs[id] = job; job.start()
    }
    fun pauseDownload(id: String) { requested[id] = "已暂停"; jobs[id]?.cancel() }
    fun cancelDownload(id: String) { requested[id] = "已取消"; jobs[id]?.cancel() }
    fun resumeDownload(id: String) = startDownload(id)
    fun retryFailedSegments(id: String) = startDownload(id)
    fun stop() { for (job in jobs.values.toList()) job.cancel(); scope.cancel() }
    private suspend fun runTask(id: String) {
        val task = requireNotNull(context.app.store.get(id))
        val recovered = withContext(Dispatchers.IO) { publisher.recover(task) }
        if (recovered != null) { markComplete(id, recovered.first, recovered.second); return }
        withContext(Dispatchers.IO) { context.app.store.update(id) { it.copy(state = "下载中", speed = 0) } }
        // GitHub 直链经镜像站加速：只改本次下载地址，存储的任务保持原始链接。
        val settings = context.app.settings.state.value
        val effective = GithubMirrors.resolve(task.url, settings.githubMirror, settings.githubMirrorPick, settings.githubMirrors)
        val active = if (effective == task.url) task else task.copy(url = effective)
        val file = engine.download(active,
            { done, total, speed -> context.app.store.update(id) { it.copy(done = done, total = total, speed = speed) } },
            { t -> context.app.telemetry.record(id, t.connections, t.pieceSize, t.fills, t.speed) })
        val size = file.length()
        withContext(Dispatchers.IO) { context.app.store.update(id) { it.copy(state = "保存中", speed = 0) } }
        val uri = publisher.publish(task, file)
        markComplete(id, uri, size)
    }
    private suspend fun markComplete(id: String, uri: String, size: Long) {
        withContext(NonCancellable + Dispatchers.IO) {
            context.app.store.update(id) { it.copy(state = "已完成", uri = uri, done = size, total = size,
                finished = System.currentTimeMillis(), headers = emptyMap(), url = "", speed = 0, error = "") }
            workDir(context, id).deleteRecursively()
        }
        val task = requireNotNull(context.app.store.get(id))
        finished.tryEmit(task)
        Notices.complete(context, task)
    }
}
