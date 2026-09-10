package io.github.bileizhen.leifetch

import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

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
        moveTo(4f, 7f); lineTo(8f, 7f); moveTo(14f, 7f); lineTo(20f, 7f)
        moveTo(4f, 17f); lineTo(12f, 17f); moveTo(18f, 17f); lineTo(20f, 17f)
        moveTo(8f, 4f); lineTo(14f, 4f); lineTo(14f, 10f); lineTo(8f, 10f); close()
        moveTo(12f, 14f); lineTo(18f, 14f); lineTo(18f, 20f); lineTo(12f, 20f); close()
    }
    val File = icon("File") {
        moveTo(14f, 3f); lineTo(5f, 3f); lineTo(5f, 21f); lineTo(19f, 21f); lineTo(19f, 8f); close()
        moveTo(14f, 3f); lineTo(14f, 8f); lineTo(19f, 8f)
        moveTo(9f, 13f); lineTo(15f, 13f); moveTo(9f, 17f); lineTo(13f, 17f)
    }
    val Pause = icon("Pause") {
        moveTo(8f, 5f); lineTo(8f, 19f); moveTo(16f, 5f); lineTo(16f, 19f)
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
        TransferRow(task, false, onExpand = { onTask(task.id) },
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
    onFilter: (Int) -> Unit, showSearch: Boolean, search: TextFieldState, expanded: String,
    onExpand: (String) -> Unit, onDelete: (String) -> Unit, onNew: () -> Unit) {
    val query = search.text.toString().trim()
    val visible = tasks.filter { it.matchesFilter(filter) && (query.isEmpty() || it.name.contains(query, true) || it.source.contains(query, true)) }
    item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            MutedText("${tasks.size} 个任务 · ${tasks.count { it.isTransferring() }} 个进行中")
            Text("↓ ${bytes(tasks.sumOf { it.speed })}/s", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.primary)
        }
    }
    item {
        FilterBar(filter, tasks, onFilter)
    }
    if (showSearch) item {
        TextField(state = search, label = "搜索文件名或来源", useLabelAsPlaceholder = true,
            lineLimits = TextFieldLineLimits.SingleLine, leadingIcon = { Icon(Icons.Rounded.Search, null) }, modifier = Modifier.fillMaxWidth())
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
    if (visible.isEmpty()) item {
        val title = when {
            query.isNotEmpty() -> "没有找到相关下载"
            filter == 1 -> "没有已停止的任务"
            filter == 2 -> "还没有已完成的文件"
            else -> "下载列表很清爽"
        }
        EmptyTransfers(title, when {
            query.isNotEmpty() -> "试试其他文件名，或切换任务分类。"
            filter == 2 -> "下载完成后，可以在这里打开和分享文件。"
            filter == 1 -> "暂停、失败或取消的下载会显示在这里。"
            else -> "点右上角的加号，添加你的第一个链接。"
        }, onNew = if (query.isEmpty() && filter != 1) onNew else null)
    }
    itemsIndexed(visible, key = { _, task -> "transfer-${task.id}" }) { index, task ->
        val context = LocalContext.current
        // 分类切换时列表项淡入淡出并滑动到新位置，而不是瞬间替换。
        Box(Modifier.animateItem()) {
            StaggeredEntrance(index) {
                TransferRow(task, expanded == task.id, onExpand = { onExpand(task.id) },
                    onPrimary = {
                        when {
                            task.state == "已完成" -> fileAction(context, task, false)
                            task.isTransferring() -> vm.control(task, false)
                            task.canStart() -> vm.start(task.id)
                            else -> onExpand(task.id)
                        }
                    }, onCancel = { vm.control(task, true) }, onDelete = {
                        if (task.state == "已取消") vm.delete(task) else onDelete(task.id)
                    })
            }
        }
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
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "entrance")
    Box(Modifier
        .onGloballyPositioned { coords ->
            if (!inView && coords.boundsInWindow().top < viewportBottomPx) inView = true
        }
        .graphicsLayer {
            alpha = progress
            val scale = 0.8f + 0.2f * progress
            scaleX = scale
            scaleY = scale
            translationY = (1f - progress) * 22.dp.toPx()
        }) { content() }
}

@Composable
private fun FilterBar(selected: Int, tasks: List<Task>, onSelect: (Int) -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("进行中", "已停止", "已完成", "全部").forEachIndexed { index, label ->
            val active = index == selected
            // 选中态颜色平滑过渡，计数随选中淡入淡出，避免硬切。
            val background by animateColorAsState(if (active) colors.primary else colors.surfaceContainer, label = "filterBg")
            val foreground by animateColorAsState(if (active) colors.onPrimary else colors.onSurfaceVariantSummary, label = "filterFg")
            Row(Modifier.clip(RoundedCornerShape(12.dp))
                .background(background)
                .selectable(active, role = Role.Tab, onClick = { onSelect(index) })
                .padding(horizontal = 15.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = foreground)
                AnimatedVisibility(active, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                    Text("${tasks.count { it.matchesFilter(index) }}", fontSize = 13.sp, color = foreground.copy(alpha = .75f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TransferRow(task: Task, expanded: Boolean, onExpand: () -> Unit, onPrimary: () -> Unit,
    onCancel: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val colors = MiuixTheme.colorScheme
    var linkMenu by remember { mutableStateOf(false) }
    val ratio = if (task.total > 0) (task.done.toDouble() / task.total).toFloat().coerceIn(0f, 1f) else 0f
    val completed = task.state == "已完成"
    Card(Modifier.fillMaxWidth(), cornerRadius = 20.dp, insideMargin = PaddingValues(0.dp)) {
        Column(Modifier.fillMaxWidth().combinedClickable(role = Role.Button,
            onClickLabel = if (expanded) "收起详情" else "展开详情", onLongClickLabel = "打开快捷菜单",
            onLongClick = { linkMenu = true }, onClick = onExpand)
            .semantics { stateDescription = if (expanded) "已展开" else "已收起" }.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(TransferIcons.File)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(task.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    MutedText(listOf(if (completed) bytes(task.done) else if (task.total > 0) bytes(task.total) else "大小未知", task.source).joinToString(" · "), 12, maxLines = 1)
                }
                IconButton(onClick = onPrimary, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(16.dp))
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

/** 任务详情三视图:信息 / 分段点阵 / 速度曲线,数据来自内存遥测注册表。 */
@Composable
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

@Composable
internal fun NewDownloadDialog(show: Boolean, onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    val url = rememberTextFieldState()
    var attempted by rememberSaveable { mutableStateOf(false) }
    val parsed = Uri.parse(url.text.toString().trim())
    val valid = parsed.scheme in setOf("http", "https") && !parsed.host.isNullOrBlank()
    LaunchedEffect(show) { if (!show) { url.edit { replace(0, length, "") }; attempted = false } }
    OverlayDialog(show = show, title = "新建下载", onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            MutedText("添加 HTTP 或 HTTPS 文件链接，确认后开始下载。")
            TextField(state = url, label = "粘贴下载链接", modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), lineLimits = TextFieldLineLimits.SingleLine)
            if (attempted && !valid) Text("请输入有效的 HTTP(S) 下载地址", fontSize = 13.sp, color = MiuixTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton("取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                TextButton("添加任务", onClick = { attempted = true; if (valid) onAdd(url.text.toString().trim()) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

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
