package io.github.bileizhen.leifetch

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.github.bileizhen.leifetch.ui.theme.LocalDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 行内时间戳。只在组合（主线程）里使用，故共享一个 SimpleDateFormat。 */
private val logTimeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)

/**
 * 日志页：实时跟随应用进程内的日志缓冲，可按级别 / 来源 / 关键字筛选，
 * 并可把当前视图导出成诊断包保存或分享（形态沿用 XBlocker 的日志上报）。
 */
internal fun LazyListScope.logItems(vm: MainViewModel, ui: LogUiState, listState: LazyListState) {
    // 结构固定为「标题 → 控制区 → 列表」，末尾下标据此算出，供实时跟随贴底使用。
    val lastIndex = if (ui.entries.isEmpty()) 2 else ui.entries.size + 1
    item { LogHeader(vm, ui, listState, lastIndex) }
    item { LogControls(vm, ui) }
    if (ui.entries.isEmpty()) item { LogEmpty(ui.total) }
    else items(ui.entries) { entry -> LogRow(entry) }
}

@Composable
private fun LogHeader(vm: MainViewModel, ui: LogUiState, listState: LazyListState, lastIndex: Int) {
    // 实时跟随：新日志贴底。用户往回翻时自动停下，不去抢滚动位置。
    LaunchedEffect(ui.entries.size, vm.logLive) {
        if (vm.logLive && ui.entries.isNotEmpty()) listState.scrollToItem(lastIndex)
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling || !vm.logLive) return@collect
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@collect
            if (last.index < info.totalItemsCount - 1) vm.setLogLive(false)
        }
    }
    Column(Modifier.padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("日志", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Text("实时记录下载、内核、镜像、代理与捕获的日志；插件进程（Firefox、系统下载器）的日志会回传到这里。" +
            "只保留最近 ${Logs.CAPACITY} 条，仅存于内存，进程结束后清空。",
            fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun LogControls(vm: MainViewModel, ui: LogUiState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    // 导出/分享回调晚于本次组合触发，用最新快照而不是回调注册时的快照。
    val current by rememberUpdatedState(ui.entries)
    fun message(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            try {
                val report = LogExport.create(context, current)
                try {
                    withContext(Dispatchers.IO) {
                        val output = context.contentResolver.openOutputStream(uri, "wt")
                            ?: throw IllegalStateException("无法写入所选位置")
                        output.use { out -> report.inputStream().use { it.copyTo(out) } }
                    }
                    message("日志已保存")
                } finally { report.delete() }
            } catch (e: Exception) {
                message("保存失败：${e.message ?: e.javaClass.simpleName}")
            } finally { busy = false }
        }
    }
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 级别与来源都用 chip：Miuix 的 TabRow 在四个等宽页签下会把末位文字裁掉。
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val levels = listOf(null, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR)
                levels.forEach { level ->
                    LogChip(level?.label ?: "全部", ui.level == level) { vm.setLogLevel(level) }
                }
            }
            if (ui.sources.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LogChip("全部来源", ui.source == null) { vm.setLogSource(null) }
                    ui.sources.forEach { source -> LogChip(source, ui.source == source) { vm.setLogSource(source) } }
                }
            }
            TextField(value = query, onValueChange = { query = it; vm.setLogQuery(it) },
                label = "搜索日志", singleLine = true, modifier = Modifier.fillMaxWidth())
            Text(
                buildString {
                    append("显示 ${ui.entries.size} 条")
                    if (ui.total != ui.entries.size) append(" / 共 ${ui.total} 条")
                    if (!vm.logLive) append(" · 已暂停跟随")
                },
                fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(if (vm.logLive) "暂停跟随" else "回到最新", enabled = !busy,
                    onClick = { vm.setLogLive(!vm.logLive) }, modifier = Modifier.weight(1f))
                TextButton("清空", enabled = !busy && ui.total > 0, onClick = {
                    vm.clearLogs()
                    message("已清空日志")
                }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton("保存日志", enabled = !busy && ui.entries.isNotEmpty(), onClick = {
                    runCatching {
                        val stamp = SimpleDateFormat("yyyy-MM-dd_HH_mm_ss", Locale.ROOT).format(Date())
                        save.launch("LeiFetch_logs_$stamp.zip")
                    }.onFailure { message("无法打开文件选择器：${it.message.orEmpty()}") }
                }, modifier = Modifier.weight(1f))
                TextButton("分享日志", enabled = !busy && ui.entries.isNotEmpty(), onClick = {
                    if (busy) return@TextButton
                    scope.launch {
                        busy = true
                        try {
                            val report = LogExport.create(context, current)
                            val uri = FileProvider.getUriForFile(context, "$PKG.files", report)
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/zip"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                clipData = android.content.ClipData.newRawUri("LeiFetch 日志", uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "分享日志"))
                        } catch (e: Exception) {
                            message("分享失败：${e.message ?: e.javaClass.simpleName}")
                        } finally { busy = false }
                    }
                }, modifier = Modifier.weight(1f))
            }
            Text("诊断包含 logs.txt（本页日志）、summary.json（版本与设置摘要）与 logcat.txt（本进程系统日志片段）；" +
                "日志里的下载地址只保留主机名，不含任务链接与文件内容。",
                fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun LogChip(text: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(text = text, onClick = onClick,
        colors = if (selected) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors())
}

@Composable
private fun LogRow(entry: LogEntry) {
    val tint = when (entry.level) {
        LogLevel.ERROR -> MiuixTheme.colorScheme.errorContainer.copy(alpha = .35f)
        LogLevel.WARN -> MiuixTheme.colorScheme.errorContainer.copy(alpha = .15f)
        else -> Color.Transparent
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(tint)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(logTimeFormat.format(Date(entry.time)), fontSize = 11.sp,
                fontFamily = FontFamily.Monospace, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Text(entry.level.label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = logLevelColor(entry.level))
            Text(entry.source, fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        Text(entry.message, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurface)
    }
}

/** 级别配色：信息随主题弱化，警告用琥珀、错误用主题错误色，明暗主题各取一档。 */
@Composable
private fun logLevelColor(level: LogLevel): Color = when (level) {
    LogLevel.INFO -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    LogLevel.WARN -> if (LocalDarkTheme.current) Color(0xFFFFB74D) else Color(0xFFB26A00)
    LogLevel.ERROR -> MiuixTheme.colorScheme.error
}

@Composable
private fun LogEmpty(total: Int) {
    Text(
        if (total == 0) "暂无日志。发起一次下载，或开启插件后重启目标应用，这里会实时出现记录。"
        else "当前筛选条件下没有日志。",
        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
        fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}
