package io.github.bileizhen.leifetch

import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bileizhen.leifetch.ui.component.miuix.animation.DampedDragAnimation
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/** 用四段三次曲线画一个圆：PathBuilder 没有 arc 指令。 */
private fun PathBuilder.circle(cx: Float, cy: Float, radius: Float) {
    val handle = radius * 0.5523f
    moveTo(cx, cy - radius)
    curveTo(cx + handle, cy - radius, cx + radius, cy - handle, cx + radius, cy)
    curveTo(cx + radius, cy + handle, cx + handle, cy + radius, cx, cy + radius)
    curveTo(cx - handle, cy + radius, cx - radius, cy + handle, cx - radius, cy)
    curveTo(cx - radius, cy - handle, cx - handle, cy - radius, cx, cy - radius)
    close()
}

/** A small, consistent outline set for the transfer workspace. */
internal object TransferIcons {
    private fun icon(name: String, draw: PathBuilder.() -> Unit) = ImageVector.Builder(
        name, 24.dp, 24.dp, 24f, 24f,
    ).apply {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = draw)
    }.build()
    val Dashboard = icon("Overview") {
        moveTo(4f, 4f); lineTo(10f, 4f); lineTo(10f, 10f); lineTo(4f, 10f); close()
        moveTo(14f, 4f); lineTo(20f, 4f); lineTo(20f, 10f); lineTo(14f, 10f); close()
        moveTo(4f, 14f); lineTo(10f, 14f); lineTo(10f, 20f); lineTo(4f, 20f); close()
        moveTo(14f, 14f); lineTo(20f, 14f); lineTo(20f, 20f); lineTo(14f, 20f); close()
    }
    val Download = icon("Download") {
        moveTo(12f, 3f); lineTo(12f, 15f); moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
        moveTo(4f, 15f); lineTo(4f, 20f); lineTo(20f, 20f); lineTo(20f, 15f)
    }
    val Plugins = icon("Plugins") {
        moveTo(8f, 3f); lineTo(8f, 7f); moveTo(16f, 3f); lineTo(16f, 7f)
        moveTo(5f, 7f); lineTo(19f, 7f); lineTo(19f, 12f)
        curveTo(19f, 16f, 16f, 18f, 12f, 18f); curveTo(8f, 18f, 5f, 16f, 5f, 12f); close()
        moveTo(12f, 18f); lineTo(12f, 22f)
    }
    val Settings = icon("Settings") {
        // 齿轮：外圈 + 中心孔 + 8 颗齿。齿起于外圈半径，圆头描边负责收尾。
        circle(12f, 12f, 6.2f)
        circle(12f, 12f, 2.1f)
        repeat(8) { index ->
            val angle = (index * 45.0 + 22.5) * PI / 180.0
            val cosA = cos(angle).toFloat()
            val sinA = sin(angle).toFloat()
            moveTo(12f + 6.2f * cosA, 12f + 6.2f * sinA)
            lineTo(12f + 9.4f * cosA, 12f + 9.4f * sinA)
        }
    }
    val File = icon("File") {
        moveTo(14f, 3f); lineTo(5f, 3f); lineTo(5f, 21f); lineTo(19f, 21f); lineTo(19f, 8f); close()
        moveTo(14f, 3f); lineTo(14f, 8f); lineTo(19f, 8f)
        moveTo(9f, 13f); lineTo(15f, 13f); moveTo(9f, 17f); lineTo(13f, 17f)
    }
    val Pause = icon("Pause") {
        moveTo(8f, 5f); lineTo(8f, 19f); moveTo(16f, 5f); lineTo(16f, 19f)
    }
    val Trash = icon("Trash") {
        moveTo(4f, 7f); lineTo(20f, 7f)
        moveTo(9.5f, 7f); lineTo(9.5f, 4f); lineTo(14.5f, 4f); lineTo(14.5f, 7f)
        moveTo(6.5f, 7f); lineTo(7.4f, 20f); lineTo(16.6f, 20f); lineTo(17.5f, 7f)
        moveTo(10.5f, 10.5f); lineTo(10.5f, 16.5f); moveTo(13.5f, 10.5f); lineTo(13.5f, 16.5f)
    }
    val SelectAll = icon("SelectAll") {
        moveTo(12f, 3f); curveTo(17f, 3f, 21f, 7f, 21f, 12f); curveTo(21f, 17f, 17f, 21f, 12f, 21f)
        curveTo(7f, 21f, 3f, 17f, 3f, 12f); curveTo(3f, 7f, 7f, 3f, 12f, 3f); close()
        moveTo(8f, 12.4f); lineTo(11f, 15.4f); lineTo(16.4f, 9f)
    }
    val Deselect = icon("Deselect") {
        moveTo(12f, 3f); curveTo(17f, 3f, 21f, 7f, 21f, 12f); curveTo(21f, 17f, 17f, 21f, 12f, 21f)
        curveTo(7f, 21f, 3f, 17f, 3f, 12f); curveTo(3f, 7f, 7f, 3f, 12f, 3f); close()
        moveTo(8.6f, 8.6f); lineTo(15.4f, 15.4f); moveTo(15.4f, 8.6f); lineTo(8.6f, 15.4f)
    }
}

internal fun Task.isTransferring() = state in setOf("下载中", "排队中", "保存中")
internal fun Task.canStart() = state in setOf("待确认", "已暂停", "失败")
internal fun Task.matchesFilter(filter: Int) = when (filter) {
    0 -> state == "待确认" || isTransferring()
    1 -> state in setOf("已暂停", "失败", "已取消")
    2 -> state == "已完成"
    else -> true
}

/** 当前可见任务：分类与搜索同一口径，列表与「全选」共用，避免两处过滤条件走偏。 */
internal fun visibleTasks(tasks: List<Task>, filter: Int, query: String) =
    tasks.filter { it.matchesFilter(filter) && (query.isEmpty() || it.name.contains(query, true) || it.source.contains(query, true)) }

/** 下载页的多选状态与回调；状态由页面层持有，行只负责呈现与触发。 */
internal class TransferActions(
    val selecting: Boolean,
    val selected: Set<String>,
    /** 删除待确认的任务 id：这些行先滑出屏幕，确认后真正删除，取消则弹回。 */
    val pending: Set<String>,
    val revealedRow: String,
    val onRevealedRow: (String) -> Unit,
    val onToggleSelect: (String) -> Unit,
    /** 右滑提交：未选中则选中，已选中则取消。 */
    val onSelect: (String) -> Unit,
    val onDelete: (List<String>) -> Unit,
)

@Composable
internal fun TransferSidebar(selected: Int, labels: List<String>, icons: List<ImageVector>,
    pending: Int, onSelect: (Int) -> Unit, onNew: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Column(Modifier.width(200.dp).fillMaxHeight().statusBarsPadding().navigationBarsPadding()
        .padding(horizontal = 16.dp, vertical = 24.dp).selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(start = 12.dp, bottom = 28.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(TransferIcons.Download, null, tint = colors.primary)
            Text("LeiFetch", fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
        }
        labels.forEachIndexed { index, label ->
            if (index == 3) Spacer(Modifier.weight(1f))
            val tint = if (selected == index) colors.primary else colors.onSurfaceVariantSummary
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(if (selected == index) colors.primary.copy(alpha = .12f) else Color.Transparent)
                .selectable(selected == index, role = Role.Tab, onClick = { onSelect(index) })
                .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icons[index], null, tint = tint)
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = tint)
                if (index == 1 && pending > 0) Text("$pending", fontSize = 12.sp, color = colors.primary)
            }
        }
        TextButton("新建下载", onClick = onNew, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
    }
}

internal fun LazyListScope.overviewItems(tasks: List<Task>, config: Config, vm: MainViewModel,
    onDownloads: () -> Unit, onCompleted: () -> Unit, onAllDownloads: () -> Unit, onNew: () -> Unit,
    onPlugins: () -> Unit, onTask: (String) -> Unit) {
    item {
        Column(Modifier.padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("传输概览", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            MutedText(if (tasks.any { it.isTransferring() }) "NSFX 正在处理你的下载。" else "由 NSFX 驱动，随时准备传输。")
        }
    }
    item {
        val history by vm.speedHistory.collectAsStateWithLifecycle()
        BoxWithConstraints {
            if (maxWidth >= 620.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SpeedPanel(tasks, history, config, Modifier.weight(1.5f))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        TaskSummaryPanel(tasks, onDownloads, onCompleted)
                        PluginEntry(config, onPlugins)
                    }
                }
            } else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SpeedPanel(tasks, history, config)
                TaskSummaryPanel(tasks, onDownloads, onCompleted)
                PluginEntry(config, onPlugins)
            }
        }
    }
    item { SectionHeading("最近任务", "查看全部", onAllDownloads) }
    val recent = tasks.filter { it.state != "已取消" }.take(3)
    if (recent.isEmpty()) item {
        EmptyTransfers("还没有下载任务", "添加一个链接，或从已配置的应用接管下载。", onNew = onNew)
    } else items(recent, key = { "recent-${it.id}" }) { task ->
        TransferRow(task, false, onClick = { onTask(task.id) },
            onPrimary = { if (task.isTransferring()) vm.control(task, false) else if (task.canStart()) vm.start(task.id) else onTask(task.id) },
            onCancel = {}, onDelete = {})
    }
}

@Composable
private fun PluginEntry(config: Config, onPlugins: () -> Unit) {
        Card(Modifier.fillMaxWidth(), cornerRadius = 22.dp, insideMargin = PaddingValues(18.dp),
            onClick = onPlugins, showIndication = true) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(TransferIcons.Plugins)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (config.enabled) "应用下载接管已开启" else "连接你的应用", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    MutedText(if (config.enabled) "${HookPlugins.enabled(config.plugins).size} 个插件已启用 · 查看配置" else "配置插件，将应用下载交给 LeiFetch", 12)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
}

@Composable
private fun SpeedPanel(tasks: List<Task>, history: List<Long>, config: Config, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val transferring = tasks.any { it.isTransferring() }
    Card(modifier.fillMaxWidth(), cornerRadius = 24.dp, insideMargin = PaddingValues(22.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                GradientText("NSFX", listOf(Color(0xFF5227FF), Color(0xFFFF9FFC), Color(0xFFB497CF)),
                    animationSpeedSec = if (transferring) 1.5f else 8f,
                    style = MiuixTheme.textStyles.main.copy(fontSize = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp))
                Text("多线程下载内核", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.onSurfaceVariantSummary)
                StateLabel(if (transferring) "传输中" else "待机就绪")
            }
            NsfxEngineMotion(active = transferring, lanes = config.threads,
                modifier = Modifier.widthIn(max = 160.dp).weight(1f).height(100.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EngineTag("${config.threads} 线程上限")
            EngineTag("${config.maxTasks} 任务并行")
            EngineTag(if (config.dynamic) "动态拆分" else "固定分段")
        }
        Hairline(Modifier.padding(top = 18.dp, bottom = 16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            MutedText("下载速度")
            MutedText("实时", 11)
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Bottom) {
            Text(bytes(tasks.sumOf { it.speed }), fontSize = 32.sp, fontWeight = FontWeight.SemiBold,
                style = MiuixTheme.textStyles.main.copy(fontFeatureSettings = "tnum"))
            Text("/s", fontSize = 16.sp, modifier = Modifier.padding(start = 5.dp, bottom = 7.dp), color = colors.onSurfaceVariantSummary)
        }
        Canvas(Modifier.fillMaxWidth().height(58.dp).padding(top = 12.dp, bottom = 8.dp)
            .semantics { contentDescription = "最近 40 秒下载速度趋势" }) {
            val baseline = size.height - 2.dp.toPx()
            repeat(3) { i ->
                val y = baseline * i / 2f
                drawLine(colors.onSurface.copy(alpha = .06f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            val maximum = (history.maxOrNull() ?: 0L).coerceAtLeast(1024)
            val points = history.mapIndexed { i, speed -> Offset(size.width * i / (history.size - 1).coerceAtLeast(1),
                baseline - (speed.toFloat() / maximum).coerceIn(0f, 1f) * (baseline - 2.dp.toPx())) }
            if (points.isNotEmpty()) {
                val path = Path().apply { moveTo(points.first().x, points.first().y); points.drop(1).forEach { lineTo(it.x, it.y) } }
                val fill = Path().apply { addPath(path); lineTo(size.width, baseline); lineTo(0f, baseline); close() }
                drawPath(fill, colors.primary.copy(alpha = .10f))
                drawPath(path, colors.primary, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawCircle(colors.primary, 3.dp.toPx(), points.last())
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MutedText("40 秒前", 11)
            MutedText(if (config.speedLimit == 0L) "不限速 · 实时" else "上限 ${bytes(config.speedLimit)}/s", 11)
        }
    }
}

/** Kotlin port of ReactBits Gradient Text (horizontal, yoyo = false): the gradient band is
 *  300% of the text width, tiled with the first color repeated at both ends for a seamless
 *  one-way scroll. BasicText keeps the brush; Miuix Text 0.9.3 resolves a solid content color. */
@Composable
private fun GradientText(text: String, colors: List<Color>, animationSpeedSec: Float = 1.5f,
    style: TextStyle = MiuixTheme.textStyles.main) {
    // Frame-time accumulator like ReactBits' unbounded progress, so a speed change never snaps
    // the band; % 1f wraps invisibly on the identical tile seam.
    var progress by remember { mutableStateOf(0f) }
    LaunchedEffect(animationSpeedSec) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            progress = (progress + (now - last) / animationSpeedSec / 1_000_000_000f) % 1f
            last = now
        }
    }
    var textWidth by remember { mutableStateOf(0f) }
    val span = (textWidth * 3f).coerceAtLeast(1f)
    val origin = -progress * span
    val band = colors + colors.first()
    val stops = Array(band.size) { i -> i / (band.size - 1).toFloat() to band[i] }
    BasicText(text,
        style = style.copy(brush = Brush.linearGradient(colorStops = stops,
            start = Offset(origin, 0f), end = Offset(origin + span, 0f), tileMode = TileMode.Repeated)),
        onTextLayout = { textWidth = it.size.width.toFloat() })
}

@Composable
private fun EngineTag(label: String) {
    Text(label, fontSize = 11.sp, color = MiuixTheme.colorScheme.primary,
        modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(MiuixTheme.colorScheme.primary.copy(alpha = .09f))
            .padding(horizontal = 9.dp, vertical = 6.dp))
}

@Composable
private fun TaskSummaryPanel(tasks: List<Task>, onDownloads: () -> Unit, onCompleted: () -> Unit, modifier: Modifier = Modifier) {
    val completed = tasks.filter { it.state == "已完成" }
    Card(modifier.fillMaxWidth(), cornerRadius = 24.dp, insideMargin = PaddingValues(20.dp)) {
        Row(Modifier.fillMaxWidth()) {
            SummaryNumber("进行中", tasks.count { it.isTransferring() }, Modifier.weight(1f), onDownloads)
            SummaryNumber("待确认", tasks.count { it.state == "待确认" }, Modifier.weight(1f), onDownloads)
            SummaryNumber("已完成", completed.size, Modifier.weight(1f), onCompleted)
        }
        Hairline(Modifier.padding(vertical = 18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            MutedText("已完成文件", 12)
            Text(bytes(completed.sumOf { it.done }), fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SummaryNumber(label: String, count: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MutedText(label, 12)
        Text("$count", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
    }
}

internal fun LazyListScope.transferItems(tasks: List<Task>, vm: MainViewModel, filter: Int,
    onFilter: (Int) -> Unit, search: TextFieldState, expanded: String,
    onExpand: (String) -> Unit, onNew: () -> Unit, actions: TransferActions) {
    val query = search.text.toString().trim()
    val visible = visibleTasks(tasks, filter, query)
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            MutedText("${tasks.size} 个任务 · ${tasks.count { it.isTransferring() }} 个进行中")
            Text("↓ ${bytes(tasks.sumOf { it.speed })}/s", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.primary)
        }
    }
    item {
        FilterBar(filter, tasks, onFilter)
    }
    if (visible.any { it.canStart() || it.isTransferring() }) item {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TextButton("开始本组", enabled = visible.any { it.canStart() },
                onClick = { visible.filter { it.canStart() }.forEach { vm.start(it.id) } })
            TextButton("暂停本组", enabled = visible.any { it.isTransferring() },
                onClick = { visible.filter { it.isTransferring() }.forEach { vm.control(it, false) } })
            MutedText("${visible.size} 个任务", 12)
        }
    }
    // 多选时给出本组的批量入口，长列表里也能一次选完当前筛选结果。
    if (actions.selecting) item {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val all = visible.isNotEmpty() && visible.all { it.id in actions.selected }
            TextButton(if (all) "取消本组全选" else "选中本组",
                enabled = visible.isNotEmpty(),
                onClick = { visible.forEach { if (all == (it.id in actions.selected)) actions.onToggleSelect(it.id) } })
            MutedText("${actions.selected.size} 个已选中", 12)
        }
    }
    if (visible.isEmpty()) item {
        // 每个分类给各自的空态说法；"进行中"以前借用的是"全部"的文案。
        val title = when {
            query.isNotEmpty() -> "没有找到相关下载"
            filter == 1 -> "没有已停止的任务"
            filter == 2 -> "还没有已完成的文件"
            filter == 0 -> "没有正在进行的任务"
            else -> "下载列表很清爽"
        }
        EmptyTransfers(title, when {
            query.isNotEmpty() -> "试试其他文件名，或切换任务分类。"
            filter == 1 -> "暂停、失败或取消的下载会显示在这里。"
            filter == 2 -> "下载完成后，可以在这里打开和分享文件。"
            filter == 0 -> "待确认与传输中的任务会显示在这里。"
            // 新建下载的入口是「下拉顶部标题」弹出的顶弹层，右上角早已不是加号。
            else -> "下拉顶部标题，或点下面的按钮添加下载。"
        }, onNew = if (query.isEmpty() && filter != 1) onNew else null)
    }
    itemsIndexed(visible, key = { _, task -> "transfer-${task.id}" }) { index, task ->
        val context = LocalContext.current
        // 分类切换时列表项淡入淡出并滑动到新位置，而不是瞬间替换。
        Box(Modifier.animateItem()) {
            StaggeredEntrance(index) {
                val selected = task.id in actions.selected
                SwipeActionRow(
                    // 右滑露出多选：已选中的行给出取消，未选中的行给出多选。
                    start = SwipeAction(
                        label = if (selected) "取消" else "多选",
                        icon = if (selected) TransferIcons.Deselect else TransferIcons.SelectAll,
                        colors = listOf(MiuixTheme.colorScheme.primary, MiuixTheme.colorScheme.primary.copy(alpha = .72f)),
                    ),
                    end = SwipeAction("删除", TransferIcons.Trash, SwipeDeleteColors),
                    revealed = actions.revealedRow == task.id,
                    onRevealChange = { open ->
                        if (open) actions.onRevealedRow(task.id)
                        else if (actions.revealedRow == task.id) actions.onRevealedRow("")
                    },
                    removing = task.id in actions.pending,
                    onStart = { actions.onSelect(task.id) },
                    onEnd = { actions.onDelete(listOf(task.id)) },
                ) {
                    TransferRow(task, expanded == task.id && !actions.selecting,
                        selecting = actions.selecting, selected = selected,
                        // 露出的行动作优先：先收回面板，再处理多选或展开。
                        onClick = {
                            when {
                                actions.revealedRow == task.id -> actions.onRevealedRow("")
                                actions.selecting -> actions.onToggleSelect(task.id)
                                else -> onExpand(task.id)
                            }
                        },
                        onPrimary = {
                            when {
                                task.state == "已完成" -> fileAction(context, task, false)
                                task.isTransferring() -> vm.control(task, false)
                                task.canStart() -> vm.start(task.id)
                                else -> onExpand(task.id)
                            }
                        }, onCancel = { vm.control(task, true) },
                        onDelete = { actions.onDelete(listOf(task.id)) })
                }
            }
        }
    }
}

/** 顶栏里的搜索行：从大标题下方展开、钉在顶栏中，展开即聚焦。
 *  右侧那个放大镜就是顶栏图标落下来的落点，顶栏原位则由渐显的 ✕ 接手关闭。 */
@Composable
internal fun TaskSearchBar(search: TextFieldState, open: Boolean) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    // 顶栏图标到本行的垂直距离（实测 54.9dp）：落下来的行程与之一致，才像同一个图标。
    val drop = with(LocalDensity.current) { 55.dp.roundToPx() }
    // 框右端只留 12dp：图标压在框的右内侧（框把它包住），中心距右 36dp 与顶栏图标同心。
    Box(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 2.dp, bottom = 10.dp)) {
        TextField(state = search, label = "搜索文件名或来源", useLabelAsPlaceholder = true,
            lineLimits = TextFieldLineLimits.SingleLine,
            // 给图标让出位置，文字不会钻到图标底下。
            trailingIcon = { Spacer(Modifier.width(40.dp)) },
            modifier = Modifier.fillMaxWidth().focusRequester(focus))
        // 叠在搜索框上层：向上飞出去时不会被框挡住，也不会被裁掉。
        // 必须由 open 驱动：写成常量 true 的话退出动画永远不跑，关闭时不会淡出，
        // 就会和顶栏升上来的那个同时存在 —— 看着就是"两个搜索图标"。
        AnimatedVisibility(open,
            enter = fadeIn(tween(100, delayMillis = 120)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 620f)) { -drop },
            exit = fadeOut(tween(70)),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
            label = "searchGlyph") {
            IconButton(onClick = { focus.requestFocus() }) {
                Icon(Icons.Rounded.Search, "搜索", tint = MiuixTheme.colorScheme.onSurface)
            }
        }
    }
}


/** 多选操作栏：下载页进入多选时替换底部导航，未选中的项不可删除。 */
@Composable
internal fun SelectionActionBar(count: Int, total: Int, allSelected: Boolean,
    onClose: () -> Unit, onToggleAll: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    Card(modifier.height(64.dp), cornerRadius = 32.dp, insideMargin = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
        // 三枚按钮之间要留出间距，否则相邻胶囊会粘成一条；圆角取按钮半高，与栏本身的胶囊一致。
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(20.dp))
                .background(colors.onSurface.copy(alpha = .06f))) {
                Icon(Icons.Rounded.Close, "退出多选", tint = colors.onSurface, modifier = Modifier.size(19.dp))
            }
            Text(if (count > 0) "已选 $count 项" else "选择任务", fontSize = 14.sp, maxLines = 1,
                fontWeight = FontWeight.Medium,
                color = if (count > 0) colors.onSurface else colors.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 4.dp).weight(1f))
            TextButton(if (allSelected) "取消全选" else "全选", onClick = onToggleAll, enabled = total > 0,
                minWidth = 0.dp, minHeight = 0.dp, cornerRadius = 21.dp,
                insideMargin = PaddingValues(horizontal = 13.dp, vertical = 11.dp))
            TextButton("删除", onClick = onDelete, enabled = count > 0,
                minWidth = 0.dp, minHeight = 0.dp, cornerRadius = 21.dp,
                insideMargin = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
                colors = ButtonDefaults.textButtonColors(
                    color = colors.error.copy(alpha = 0.13f),
                    disabledColor = colors.onSurface.copy(alpha = 0.05f),
                    textColor = colors.error,
                    disabledTextColor = colors.onSurfaceVariantSummary.copy(alpha = 0.5f)))
        }
    }
}

/** 多选标记：未选中是细描边圆圈，选中时填充并让对勾分段画出（圆先弹一下）。 */
@Composable
private fun SelectMark(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    val progress by animateFloatAsState(if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 1200f), label = "selectMark")
    val fill by animateColorAsState(if (selected) colors.primary else Color.Transparent,
        animationSpec = tween(200), label = "selectFill")
    val ring by animateColorAsState(if (selected) colors.primary else colors.onSurface.copy(alpha = .3f),
        animationSpec = tween(200), label = "selectRing")
    Canvas(modifier.size(22.dp).semantics { stateDescription = if (selected) "已选中" else "未选中" }) {
        val radius = size.minDimension / 2f
        val center = this.center
        drawCircle(fill, radius, center)
        drawCircle(ring, radius - 0.8.dp.toPx(), center, style = Stroke(1.6.dp.toPx()))
        val start = Offset(size.width * .28f, size.height * .52f)
        val elbow = Offset(size.width * .44f, size.height * .68f)
        val tip = Offset(size.width * .74f, size.height * .33f)
        val stroke = 2.2.dp.toPx()
        val first = ((progress - 0.08f) / 0.45f).coerceIn(0f, 1f)
        val second = ((progress - 0.45f) / 0.55f).coerceIn(0f, 1f)
        val alpha = (progress * 1.6f).coerceIn(0f, 1f)
        if (first > 0f) drawLine(colors.onPrimary.copy(alpha = alpha), start,
            start + (elbow - start) * first, stroke, StrokeCap.Round)
        if (second > 0f) drawLine(colors.onPrimary.copy(alpha = alpha), elbow,
            elbow + (tip - elbow) * second, stroke, StrokeCap.Round)
    }
}

/** ReactBits Animated List 式入场（对应其 useInView）：条目进入视口后才按行序错峰弹出
 *  （缩放 + 淡入 + 轻微上移）。首屏条目逐个显示；滚动进入的远端条目立即弹出，不拖慢滚动；
 *  压栈页面传 baseDelayMs 避开 NavDisplay 转场，否则动画会在转场背后提前播完。 */
@Composable
internal fun StaggeredEntrance(index: Int, baseDelayMs: Long = 0, content: @Composable () -> Unit) {
    var inView by remember { mutableStateOf(false) }
    var appeared by remember { mutableStateOf(false) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    // 屏幕高度首帧即有效；略加前瞻让条目在临进视口时就开始弹出。
    val viewportBottomPx = with(density) { configuration.screenHeightDp.dp.toPx() } + 64f
    LaunchedEffect(inView) {
        if (inView && !appeared) {
            delay(baseDelayMs + if (index in 1..10) index * 110L else 0L)
            appeared = true
        }
    }
    // 保留 State 而不是解包成值：动画只在绘制阶段读取，入场期间不会逐帧重组列表项
    // （切换筛选时同时有近十条在播，组合期读取是当时掉帧的主因）。
    val progress = animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "entrance")
    Box(Modifier
        .onGloballyPositioned { coords ->
            if (!inView && coords.boundsInWindow().top < viewportBottomPx) inView = true
        }
        .graphicsLayer {
            val value = progress.value
            alpha = value
            val scale = 0.8f + 0.2f * value
            scaleX = scale
            scaleY = scale
            translationY = (1f - value) * 22.dp.toPx()
        }) { content() }
}

@Composable
private fun FilterBar(selected: Int, tasks: List<Task>, onSelect: (Int) -> Unit) {
    val colors = MiuixTheme.colorScheme
    val labels = listOf("进行中", "已停止", "已完成", "全部")
    // 分段控件：一条轨道 + 一块滑动的选中指示。按住可左右拖动、松手吸附到最近的分段，
    // 与底部悬浮导航栏共用同一套 DampedDragAnimation，手感保持一致。
    val inset = 3.dp
    val density = LocalDensity.current
    val animationScope = rememberCoroutineScope()
    var trackWidth by remember { mutableFloatStateOf(0f) }
    val insetPx = with(density) { inset.toPx() }
    val segmentPx = ((trackWidth - 2 * insetPx) / labels.size).coerceAtLeast(1f)
    val latestSelected by rememberUpdatedState(selected)
    val latestOnSelect by rememberUpdatedState(onSelect)
    val latestSegment by rememberUpdatedState(segmentPx)
    val damped = remember(animationScope, labels.size) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selected.toFloat(),
            valueRange = 0f..(labels.size - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1f,
            onDragStarted = {},
            onDragStopped = {},
            onDrag = { _, _ -> },
        )
    }
    // 自己接横向手势而不用 DampedDragAnimation 自带的检测器：那个不区分方向，
    // 会把在轨道上起手的竖向滑动一起吃掉，列表就滚不动了。
    val dragModifier = Modifier.pointerInput(damped, labels.size) {
        detectHorizontalDragGestures(
            onDragStart = { damped.press() },
            onDragEnd = {
                // 松手吸附到最近分段；与当前选中相同就弹回原位，否则交给外层切筛选。
                val target = damped.targetValue.roundToInt().coerceIn(0, labels.size - 1)
                if (target == latestSelected) damped.animateToValue(target.toFloat()) else latestOnSelect(target)
            },
            onDragCancel = { damped.animateToValue(latestSelected.toFloat()) },
            onHorizontalDrag = { change, delta ->
                change.consume()
                val segment = latestSegment
                if (segment > 0f) {
                    damped.updateValue((damped.targetValue + delta / segment)
                        .coerceIn(0f, (labels.size - 1).toFloat()))
                }
            },
        )
    }
    // 点击分段或拖动提交后，指示块滑到新位置。
    LaunchedEffect(selected) { damped.animateToValue(selected.toFloat()) }
    Box(Modifier.widthIn(max = 480.dp).fillMaxWidth().height(46.dp)
        .onSizeChanged { trackWidth = it.width.toFloat() }
        .then(dragModifier)
        .clip(RoundedCornerShape(12.dp)).background(colors.surfaceContainer)) {
        Box(Modifier.fillMaxSize().padding(inset)) {
            Box(Modifier
                .layout { measurable, constraints ->
                    // 内层 Box 已缩进 inset，这里只按位置平移、宽度取整段，四边留白才一致；
                    // 半径取 12 - 3 = 9dp，与轨道内缘同心，内圆角就不会和外圆角错开。
                    val segment = ((trackWidth - 2 * inset.toPx()) / labels.size).coerceAtLeast(1f)
                    val placeable = measurable.measure(Constraints.fixed(segment.roundToInt(), constraints.maxHeight))
                    layout(placeable.width, placeable.height) {
                        placeable.place((damped.value * segment).roundToInt(), 0)
                    }
                }
                .background(colors.primary, RoundedCornerShape(9.dp)))
            Row(Modifier.fillMaxSize().selectableGroup()) {
                labels.forEachIndexed { index, label ->
                    val active = index == selected
                    val foreground by animateColorAsState(if (active) colors.onPrimary else colors.onSurfaceVariantSummary,
                        animationSpec = tween(200), label = "filterFg")
                    Row(Modifier.weight(1f).fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp))
                        .selectable(active, role = Role.Tab, onClick = { onSelect(index) }),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = foreground)
                        // 计数只挂在选中段上；直接显示，不做宽度动画，避免逐帧改变分段布局。
                        if (active) Text("${tasks.count { it.matchesFilter(index) }}", fontSize = 13.sp,
                            color = foreground.copy(alpha = .75f), modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TransferRow(task: Task, expanded: Boolean, onClick: () -> Unit, onPrimary: () -> Unit,
    onCancel: () -> Unit, onDelete: () -> Unit, selecting: Boolean = false, selected: Boolean = false) {
    val context = LocalContext.current
    val colors = MiuixTheme.colorScheme
    var linkMenu by remember { mutableStateOf(false) }
    val ratio = if (task.total > 0) (task.done.toDouble() / task.total).toFloat().coerceIn(0f, 1f) else 0f
    val completed = task.state == "已完成"
    // 选中态整行染色：底色在卡片自身颜色上叠加主色，避免半透明卡片透出下层。
    val container by animateColorAsState(
        if (selected) colors.primary.copy(alpha = .16f).compositeOver(colors.surfaceContainer) else colors.surfaceContainer,
        animationSpec = tween(220), label = "rowContainer")
    val mark = selected
    Card(Modifier.fillMaxWidth(), cornerRadius = 20.dp, insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = container)) {
        Column(Modifier.fillMaxWidth().combinedClickable(role = if (selecting) Role.Checkbox else Role.Button,
            onClickLabel = when {
                selecting && mark -> "取消选择"
                selecting -> "选择"
                expanded -> "收起详情"
                else -> "展开详情"
            },
            onLongClickLabel = "打开快捷菜单",
            onLongClick = { linkMenu = true }, onClick = onClick)
            .semantics {
                stateDescription = when {
                    selecting -> if (mark) "已选中" else "未选中"
                    expanded -> "已展开"
                    else -> "已收起"
                }
                this.selected = selecting && mark
            }.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 勾选动画的宽度也随出现一起收起，行内容重排不会在结尾跳一下。
                AnimatedVisibility(selecting, enter = fadeIn(tween(160)) + expandHorizontally(tween(240, easing = FastOutSlowInEasing)),
                    exit = fadeOut(tween(100)) + shrinkHorizontally(tween(200, easing = FastOutSlowInEasing))) {
                    SelectMark(mark, Modifier.padding(end = 12.dp))
                }
                IconTile(TransferIcons.File)
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(task.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    MutedText(listOf(if (completed) bytes(task.done) else if (task.total > 0) bytes(task.total) else "大小未知", task.source).joinToString(" · "), 12, maxLines = 1)
                }
                // 多选时收起主操作按钮：整行的点击都用于勾选，避免「点按」有两种含义。
                AnimatedVisibility(!selecting, enter = fadeIn(tween(160)) + expandHorizontally(tween(240, easing = FastOutSlowInEasing)),
                    exit = fadeOut(tween(100)) + shrinkHorizontally(tween(200, easing = FastOutSlowInEasing))) {
                    IconButton(onClick = onPrimary, modifier = Modifier.padding(start = 12.dp).size(48.dp).clip(RoundedCornerShape(16.dp))
                        .background(colors.primary.copy(alpha = .09f))) {
                        Icon(when {
                            completed -> Icons.Rounded.Check
                            task.isTransferring() -> TransferIcons.Pause
                            task.state == "已取消" -> Icons.Rounded.MoreVert
                            else -> Icons.Rounded.PlayArrow
                        }, when {
                            completed -> "打开文件"
                            task.isTransferring() -> "暂停下载"
                            task.state == "待确认" -> "确认下载"
                            task.state == "已取消" -> "任务详情"
                            else -> "继续下载"
                        }, tint = colors.primary, modifier = Modifier.size(22.dp))
                    }
                }
            }
            if (task.isTransferring() || task.state == "已暂停") {
                Spacer(Modifier.height(14.dp))
                if (task.total > 0) LinearProgressIndicator(progress = ratio, modifier = Modifier.fillMaxWidth().height(4.dp))
                else if (task.isTransferring()) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                StateLabel(task.state)
                Text(when {
                    task.state == "下载中" -> "${(ratio * 100).toInt()}% · ${bytes(task.speed)}/s".let { if (task.total > 0) it else "${bytes(task.speed)}/s" }
                    completed -> "${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(task.finished))}  ·  ${if (expanded) "收起" else "详情"}"
                    task.total > 0 && task.done > 0 -> "${bytes(task.done)} / ${bytes(task.total)}"
                    else -> if (expanded) "收起详情" else "查看详情"
                }, fontSize = 11.sp, color = colors.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp).weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
            if (task.error.isNotEmpty()) Text(task.error, modifier = Modifier.padding(top = 10.dp), fontSize = 12.sp,
                color = colors.error, maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
            if (task.state == "待确认" && task.source.contains("原")) {
                Text("原应用仍在下载。确认接管前，请先取消原任务。", fontSize = 12.sp,
                    color = colors.onSurfaceVariantSummary, modifier = Modifier.padding(top = 10.dp))
            }
            if (linkMenu) {
                // 长按快捷菜单：锚在卡片右上角的操作按钮附近；已取消任务的链接已清除，置灰提示。
                Popup(alignment = Alignment.TopEnd,
                    offset = with(LocalDensity.current) { IntOffset((-4).dp.roundToPx(), 52.dp.roundToPx()) },
                    onDismissRequest = { linkMenu = false },
                    properties = PopupProperties(focusable = true)) {
                    Box(Modifier.clip(RoundedCornerShape(14.dp)).background(colors.surfaceContainer)
                        .border(0.5.dp, colors.onSurface.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                        .padding(5.dp)) {
                        Text("复制下载链接", fontSize = 14.sp, fontWeight = FontWeight.Medium,
                            color = if (task.url.isNotEmpty()) colors.onSurface else colors.onSurfaceVariantSummary.copy(alpha = 0.5f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 220.dp).clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = task.url.isNotEmpty()) {
                                    context.getSystemService(android.content.ClipboardManager::class.java)
                                        ?.setPrimaryClip(android.content.ClipData.newPlainText("下载链接", task.url))
                                    // Android 13+ 系统会显示自己的复制提示，避免重复。
                                    if (Build.VERSION.SDK_INT < 33)
                                        Toast.makeText(context, "已复制下载链接", Toast.LENGTH_SHORT).show()
                                    linkMenu = false
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp))
                    }
                }
            }
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Hairline()
                Text(task.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                TaskDetailTabs(task)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (completed) {
                        TextButton("打开", onClick = onPrimary, modifier = Modifier.weight(1f))
                        TextButton("分享", onClick = { fileAction(context, task, true) }, modifier = Modifier.weight(1f))
                        TextButton("删除", onClick = onDelete, modifier = Modifier.weight(1f))
                    } else if (task.state == "已取消") {
                        TextButton("移除记录", onClick = onDelete, modifier = Modifier.fillMaxWidth())
                    } else {
                        TextButton(if (task.isTransferring()) "暂停下载" else if (task.state == "待确认") "确认下载" else "继续下载",
                            onClick = onPrimary, modifier = Modifier.weight(1f))
                        TextButton("取消下载", onClick = onCancel, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** 任务详情三视图:信息 / 分段点阵 / 速度曲线,数据来自内存遥测注册表。 */@Composable
private fun TaskDetailTabs(task: Task) {
    val stats by (LocalContext.current.applicationContext as LeiFetchApp).telemetry.flow(task.id).collectAsStateWithLifecycle()
    var view by rememberSaveable(task.id) { mutableStateOf(0) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("信息", "分段", "速度").forEachIndexed { index, label ->
                val active = index == view
                Text(label, fontSize = 12.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (active) MiuixTheme.colorScheme.primary.copy(alpha = .12f)
                            else MiuixTheme.colorScheme.onSurface.copy(alpha = .05f))
                        .selectable(active, role = Role.Tab, onClick = { view = index })
                        .padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
        when (view) {
            0 -> InfoCard(task, stats)
            1 -> PiecesCard(task, stats)
            else -> SpeedCard(task, stats)
        }
    }
}

@Composable
private fun InfoCard(task: Task, stats: TransferStatus) {
    val completed = task.state == "已完成"
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCell("传输", if (task.isTransferring()) "${bytes(task.speed)}/s" else if (completed) "已完成" else "待机", Modifier.weight(1f))
            StatCell("剩余时间", if (task.isTransferring() && task.total > 0 && task.speed > 0)
                "${(task.total - task.done).coerceAtLeast(0) / task.speed} 秒" else "—", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCell("进度", if (task.total > 0) "${bytes(task.done)} / ${bytes(task.total)}" else bytes(task.done), Modifier.weight(1f))
            StatCell("并行连接", "${if (stats.connections > 0) stats.connections else if (task.isTransferring()) 1 else 0} 个", Modifier.weight(1f))
        }
        DetailLine("类型", "HTTP 直链 · NSFX 内核")
        // 镜像在下载开始时择优并记录；已完成任务的原始链接会被清除，无从判断是否直连。
        if (task.mirror.isNotEmpty()) DetailLine("下载线路", "GitHub 镜像 · ${task.mirror.removePrefix("https://").removePrefix("http://")}")
        else if (task.url.isNotEmpty() && GithubMirrors.isGithubUrl(task.url)) DetailLine("下载线路", "GitHub 直连")
        DetailLine("创建时间", DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(task.created)))
        if (completed) DetailLine("完成时间", DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(task.finished)))
        DetailLine("保存位置", displayTree(task.tree).ifEmpty { "应用内 downloads 目录" })
        if (task.url.isNotEmpty()) DetailLine("来源站点", Uri.parse(task.url).host ?: "未知来源")
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(MiuixTheme.colorScheme.onSurface.copy(alpha = .04f)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        MutedText(label, 11)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PiecesCard(task: Task, stats: TransferStatus) {
    if (stats.pieceSize <= 0L || stats.fills.isEmpty()) {
        MutedText(if (task.state == "已完成") "任务已完成" else "开始下载后显示分段进度")
        return
    }
    val colors = MiuixTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cell = 9.dp; val gap = 3.dp
            val columns = maxOf(1, (maxWidth / (cell + gap)).toInt())
            val rows = (stats.fills.size + columns - 1) / columns
            Canvas(Modifier.fillMaxWidth().height(cell * rows + gap * (rows - 1).coerceAtLeast(0))
                .semantics { contentDescription = "分段进度，${stats.donePieces}/${stats.fills.size} 片已完成" }) {
                val cw = cell.toPx(); val g = gap.toPx(); val corner = CornerRadius(2f)
                stats.fills.forEachIndexed { i, fill ->
                    val x = (i % columns) * (cw + g); val y = (i / columns) * (cw + g)
                    val level = fill.toInt() and 0xFF
                    val fraction = level / 255f
                    if (level >= 255) drawRoundRect(colors.primary, topLeft = Offset(x, y), size = Size(cw, cw), cornerRadius = corner)
                    else {
                        drawRoundRect(colors.onSurface.copy(alpha = .07f), topLeft = Offset(x, y), size = Size(cw, cw), cornerRadius = corner)
                        if (level > 0) drawRoundRect(colors.primary.copy(alpha = .55f), topLeft = Offset(x, y + cw * (1 - fraction)),
                            size = Size(cw, cw * fraction), cornerRadius = corner)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(colors.primary))
            MutedText("已下载 ${stats.donePieces}", 11)
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(colors.onSurface.copy(alpha = .12f)))
            MutedText("未开始 ${stats.fills.size - stats.donePieces}", 11)
        }
        MutedText("每片 ${bytes(stats.pieceSize)} · 共 ${stats.fills.size} 片", 11)
    }
}

@Composable
private fun SpeedCard(task: Task, stats: TransferStatus) {
    if (stats.history.isEmpty()) { MutedText("开始下载后记录速度曲线"); return }
    var lifetime by rememberSaveable("${task.id}-speed") { mutableStateOf(false) }
    val colors = MiuixTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("会话 (60秒)" to false, "生命周期" to true).forEach { (label, mode) ->
                val active = lifetime == mode
                Text(label, fontSize = 12.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (active) colors.primary else colors.onSurfaceVariantSummary,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                        .background(if (active) colors.primary.copy(alpha = .12f) else colors.onSurface.copy(alpha = .05f))
                        .selectable(active, role = Role.Tab, onClick = { lifetime = mode })
                        .padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell("当前", "${bytes(task.speed)}/s", Modifier.weight(1f))
            StatCell("平均", "${bytes(stats.mean)}/s", Modifier.weight(1f))
            StatCell("峰值", "${bytes(stats.peak)}/s", Modifier.weight(1f))
            StatCell("活跃", formatDuration(stats.activeMs), Modifier.weight(1f))
        }
        val samples = if (lifetime) stats.history else stats.history.takeLast(60)
        Canvas(Modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = "速度曲线，共 ${samples.size} 个样本" }) {
            val baseline = size.height - 2.dp.toPx()
            val maximum = maxOf(samples.maxOf { it.speed }, stats.peak, 1024L)
            repeat(3) { i ->
                val y = baseline * i / 2f
                drawLine(colors.onSurface.copy(alpha = .06f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            val stripe = size.width / samples.size
            samples.forEachIndexed { i, s ->
                val h = (s.speed.toFloat() / maximum) * (baseline - 2.dp.toPx())
                if (h > 0f) drawRect(colors.primary.copy(alpha = .85f),
                    topLeft = Offset(i * stripe + stripe * .18f, baseline - h), size = Size(stripe * .64f, h))
            }
            val dashes = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            listOf(stats.peak, stats.mean).forEach { v ->
                if (v > 0) {
                    val y = baseline - (v.toFloat() / maximum) * (baseline - 2.dp.toPx())
                    drawLine(colors.onSurfaceVariantSummary.copy(alpha = .4f), Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = dashes)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MutedText(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(samples.first().time)), 10)
            MutedText(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(samples.last().time)), 10)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    return if (total < 60) "$total 秒" else "%d 分 %02d 秒".format(total / 60, total % 60)
}

// 原 NewDownloadDialog 居中弹窗已由 NewDownloadSheet 的顶弹层替代（顶栏下拉唤出）。

@Composable
private fun StateLabel(state: String) {
    val color = when (state) {
        "失败" -> MiuixTheme.colorScheme.error
        "下载中", "传输中", "待确认" -> MiuixTheme.colorScheme.primary
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(5.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(state, fontSize = 12.sp, color = color)
    }
}

@Composable
private fun IconTile(icon: ImageVector) {
    Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(MiuixTheme.colorScheme.onSurface.copy(alpha = .05f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.size(23.dp))
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.width(72.dp))
        Text(value, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SectionHeading(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onClick).padding(vertical = 14.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            MutedText(action, 12)
            Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(18.dp), tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun EmptyTransfers(title: String, description: String, onNew: (() -> Unit)?) {
    Card(Modifier.fillMaxWidth(), cornerRadius = 24.dp, insideMargin = PaddingValues(26.dp)) {
        IconTile(TransferIcons.Download)
        Text(title, modifier = Modifier.padding(top = 20.dp), fontSize = 19.sp, fontWeight = FontWeight.Medium)
        Text(description, modifier = Modifier.padding(top = 8.dp), fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        if (onNew != null) TextButton("添加下载链接", onClick = onNew, modifier = Modifier.padding(top = 20.dp))
    }
}

@Composable
private fun MutedText(text: String, size: Int = 13, maxLines: Int = Int.MAX_VALUE) {
    Text(text, fontSize = size.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(MiuixTheme.colorScheme.onSurface.copy(alpha = .06f)))
}
