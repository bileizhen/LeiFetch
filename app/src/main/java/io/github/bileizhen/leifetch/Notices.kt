package io.github.bileizhen.leifetch

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Locale

object Notices {
    const val ACTIVE_ID = 42
    private const val LIVE = "downloads_live"
    private const val EVENTS = "downloads_events"
    // 单一下载强调色；轨道、圆角、字体和按钮间距由系统模板管理。
    private val PROGRESS_BLUE = 0xFF5C9DF6.toInt()
    fun channels(c: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = c.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(LIVE, "下载进度与流体云", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setSound(null, null); enableVibration(false); setShowBadge(false)
            })
            manager.createNotificationChannel(NotificationChannel(EVENTS, "待确认与下载完成", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
    fun activity(c: Context, id: String = "", action: String = "view"): PendingIntent = PendingIntent.getActivity(
        c, 0, Intent(c, MainActivity::class.java).setData(Uri.parse("leifetch://task/$id/$action"))
            .putExtra("id", id).putExtra("op", action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun command(c: Context, id: String, action: String, foreground: Boolean = false): PendingIntent {
        val intent = Intent(c, DownloadService::class.java).setData(Uri.parse("leifetch://control/$id/$action"))
            .putExtra("id", id).putExtra("op", action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (foreground && Build.VERSION.SDK_INT >= 26) PendingIntent.getForegroundService(c, 0, intent, flags)
        else PendingIntent.getService(c, 0, intent, flags)
    }

    fun progress(c: Context, task: Task?): Notification {
        val t = task
        val known = t != null && t.total > 0
        val fraction = if (t != null && known) (t.done.toDouble() / t.total).coerceIn(0.0, 1.0) else 0.0
        val percent = (fraction * 100).toInt()
        val transferring = t?.state == "下载中"
        val title = when {
            t == null -> "准备下载"
            t.state == "保存中" -> "正在保存文件"
            t.state == "排队中" -> "等待下载"
            transferring && known -> "正在下载 · $percent%"
            transferring -> "正在下载"
            else -> t.state
        }
        val detail = when {
            t == null -> "正在连接"
            t.state == "保存中" -> "正在写入保存位置"
            t.state == "排队中" -> "有空闲通道后自动开始"
            transferring -> transferDetail(t, known)
            else -> "已下载 ${bytes(t.done)}"
        }
        val showProgress = transferring || t == null
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, LIVE) else Notification.Builder(c)
        // 参照官方组合模板的信息层级：核心状态 / 文件名 / 最多两项辅助信息。
        // 本项目使用 Android 原生通知，subText 并非 OML 的 C2/C3 槽位。
        b.setSmallIcon(R.drawable.ic_download).setContentTitle(title)
            .setContentText(t?.name ?: "正在准备下载任务").setSubText(detail)
            .setContentIntent(activity(c, t?.id.orEmpty()))
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_PROGRESS)
            .setShowWhen(false).setVisibility(Notification.VISIBILITY_PRIVATE).setColor(PROGRESS_BLUE)
        if (showProgress) b.setProgress(10000, (fraction * 10000).toInt(), !known)
        if (Build.VERSION.SDK_INT >= 31) b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        if (t != null) {
            b.addAction(Notification.Action.Builder(null, "暂停", command(c, t.id, "pause")).build())
            b.addAction(Notification.Action.Builder(null, "取消", command(c, t.id, "cancel")).build())
        }
        if (Build.VERSION.SDK_INT >= 36 && c.app.settings.state.value.fluid) {
            if (showProgress) fluidProgress(b, fraction, !known)
            else fluidText(b, t?.name.orEmpty())
            b.setShortCriticalText(when {
                t == null -> "准备中"
                transferring && known -> "$percent%"
                else -> t.state.take(5)
            })
        }
        return b.build()
    }
    /** 实际传输才展示进度；纯色连续条，不添加里程碑或装饰性 tracker。 */
    @RequiresApi(36)
    private fun fluidProgress(b: Notification.Builder, fraction: Double, indeterminate: Boolean) {
        requestPromotion(b)
        val style = Notification.ProgressStyle()
            .setProgressSegments(listOf(Notification.ProgressStyle.Segment(10000).setColor(PROGRESS_BLUE)))
            .setProgress((fraction * 10000).toInt().coerceIn(0, 10000)).setProgressIndeterminate(indeterminate)
            .setStyledByProgress(true)
        // 清除进度条两端与行尾的默认图标，保持纯色条样式。
        style.setProgressStartIcon(null).setProgressEndIcon(null).setProgressTrackerIcon(null)
        b.setStyle(style)
    }

    private fun fluidText(b: Notification.Builder, text: String) {
        requestPromotion(b)
        b.setStyle(Notification.BigTextStyle().bigText(text))
    }

    private fun transferDetail(t: Task, known: Boolean): String {
        val size = if (known) "${bytes(t.done)} / ${bytes(t.total)}" else "已下载 ${bytes(t.done)}"
        if (t.speed <= 0) return size
        return "${bytes(t.speed)}/s · ${eta(t) ?: size}"
    }
    /** 剩余时间估计（按当前速度）；不可用或已完成时返回 null。 */
    private fun eta(t: Task): String? {
        if (t.total <= 0 || t.speed <= 0 || t.done >= t.total) return null
        val s = kotlin.math.ceil((t.total - t.done).toDouble() / t.speed).toLong()
        return when {
            s < 60 -> "约剩 $s 秒"
            s < 3600 -> "约剩 ${(s + 59) / 60} 分钟"
            else -> "约剩 ${String.format(Locale.ROOT, "%.1f", s / 3600.0)} 小时"
        }
    }
    fun update(c: Context, task: Task?) {
        runCatching { c.getSystemService(NotificationManager::class.java).notify(ACTIVE_ID, progress(c, task)) }
    }
    /** 待确认任务的流体云卡片：可用时作为实时通知呈现（含确认下载 / 忽略动作），否则退回普通通知。 */
    fun refreshCandidate(c: Context, exclude: String? = null) {
        val manager = c.getSystemService(NotificationManager::class.java)
        val config = c.app.settings.state.value
        val pending = runCatching {
            c.app.store.tasks.value.filter { it.state == "待确认" && it.id != exclude }
        }.getOrDefault(emptyList())
        if (pending.isEmpty() || !config.notices || !canPost(c)) { clearCandidate(c); return }
        val newest = pending.first()
        val fluid = Build.VERSION.SDK_INT >= 36 && config.fluid &&
            runCatching { manager.canPostPromotedNotifications() }.getOrDefault(false)
        try { manager.notify(43, candidate(c, newest, pending.size, fluid)) } catch (_: SecurityException) { }
    }

    internal fun candidate(c: Context, newest: Task, count: Int, fluid: Boolean): Notification {
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, if (fluid) LIVE else EVENTS) else Notification.Builder(c)
        val detail = listOfNotNull(
            if (newest.total > 0) bytes(newest.total) else "大小待获取",
            if (count > 1) "另有 ${count - 1} 项待确认" else null
        ).joinToString(" · ")
        b.setSmallIcon(R.drawable.ic_download).setContentTitle("等待确认下载")
            .setContentText(newest.name).setSubText(detail)
            .setStyle(Notification.BigTextStyle().bigText(newest.name))
            .setContentIntent(activity(c, newest.id)).setOnlyAlertOnce(true).setShowWhen(false)
            .setColor(PROGRESS_BLUE).setVisibility(Notification.VISIBILITY_PRIVATE)
        if (Build.VERSION.SDK_INT >= 36 && fluid) {
            fluidText(b, newest.name)
            b.setOngoing(true).setShortCriticalText("待确认")
                .addAction(Notification.Action.Builder(null, "确认下载", command(c, newest.id, "start", foreground = true)).build())
                .addAction(Notification.Action.Builder(null, "忽略", command(c, newest.id, "ignore")).build())
        } else b.setAutoCancel(true)
        return b.build()
    }
    fun clearCandidate(c: Context) {
        runCatching { c.getSystemService(NotificationManager::class.java).cancel(43) }
    }
    fun complete(c: Context, task: Task) {
        val config = c.app.settings.state.value
        if ((!config.notices && !config.fluid) || !canPost(c)) return
        try { c.getSystemService(NotificationManager::class.java).notify(task.id, 1, completed(c, task, config.fluid)) }
        catch (_: SecurityException) { }
    }

    internal fun completed(c: Context, task: Task, fluid: Boolean): Notification {
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, LIVE) else Notification.Builder(c)
        b.setSmallIcon(R.drawable.ic_download_done).setContentTitle("下载完成")
            .setContentText(task.name).setSubText(bytes(task.done)).setShowWhen(false)
            .setStyle(Notification.BigTextStyle().bigText(task.name))
            .setColor(PROGRESS_BLUE).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(activity(c, task.id)).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .addAction(Notification.Action.Builder(null, "打开", activity(c, task.id, "open")).build())
            .addAction(Notification.Action.Builder(null, "分享", activity(c, task.id, "share")).build())
        if (Build.VERSION.SDK_INT >= 36 && fluid) {
            fluidText(b, task.name)
            b.setOngoing(true).setShortCriticalText("已完成")
        } else b.setAutoCancel(true)
        return b.build()
    }
    private fun requestPromotion(builder: Notification.Builder) {
        builder.addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
        runCatching { promotedOngoing?.invoke(builder, true) }
    }
    private fun canPost(c: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    // setRequestPromotedOngoing 正式标注为 API 36.1；部分 36.0 OEM 固件已带该方法，
    // 反射探测一次：有则请求提升为实时通知，无则跳过（避免 NoSuchMethodError）。
    private val promotedOngoing by lazy {
        runCatching { Notification.Builder::class.java.getMethod("setRequestPromotedOngoing", java.lang.Boolean.TYPE) }.getOrNull()
    }
    fun diagnostic(c: Context): String {
        val m = c.getSystemService(NotificationManager::class.java)
        val enabled = NotificationManagerCompat.from(c).areNotificationsEnabled()
        val promoted = if (Build.VERSION.SDK_INT >= 36) m.canPostPromotedNotifications().toString() else "系统低于 Android 16"
        val channel = if (Build.VERSION.SDK_INT >= 26) m.getNotificationChannel(LIVE)?.importance.toString() else "无通知渠道"
        return "${Build.MANUFACTURER} ${Build.MODEL} · API ${Build.VERSION.SDK_INT}\n通知：$enabled · 实时通知：$promoted · 渠道重要性：$channel"
    }
    fun openSettings(c: Context) {
        val intent = if (Build.VERSION.SDK_INT >= 36) Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)
        else if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName)
        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}"))
        runCatching { c.startActivity(intent) }.onFailure {
            c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}")))
        }
    }
}

fun bytes(value: Long): String = when {
    value >= 1024L * 1024 * 1024 -> String.format(Locale.ROOT, "%.1f GB", value / (1024.0 * 1024 * 1024))
    value >= 1024L * 1024 -> String.format(Locale.ROOT, "%.1f MB", value / (1024.0 * 1024))
    value >= 1024 -> String.format(Locale.ROOT, "%.1f KB", value / 1024.0)
    else -> "${value.coerceAtLeast(0)} B"
}

/** 限速文案：0 为不限速；整数倍不拖小数尾巴，例如 5 MB/s 而非 5.0 MB/s。 */
fun speedText(bytesPerSecond: Long): String = when {
    bytesPerSecond <= 0L -> "不限速"
    bytesPerSecond >= 1024L * 1024 -> compact(bytesPerSecond / (1024.0 * 1024)) + " MB/s"
    else -> compact(bytesPerSecond / 1024.0) + " KB/s"
}

private fun compact(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')
