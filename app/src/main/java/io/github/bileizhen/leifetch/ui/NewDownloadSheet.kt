// 顶弹“新建下载”：从顶栏标题下拉唤出，弹层跟随手指、越过阈值（过半或快速下甩）
// 松手后弹性展开；展开后可从把手区上滑关闭。替代原右上角 + 按钮与居中弹窗。
package io.github.bileizhen.leifetch.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.max
import kotlin.math.min

@Stable
internal class NewDownloadSheetState {
    private enum class DragMode { None, PullOpen, Dismiss }

    /** 锚点动画值（px）：0 = 展开贴合顶部，-height = 收起于屏幕上方外。 */
    val offset = Animatable(-10000f)
    var height by mutableIntStateOf(0)
        private set
    var dragging by mutableStateOf(false)
        private set
    private var mode = DragMode.None
    // 状态化的位移：translationY 在 graphicsLayer 块里逐帧读取，普通字段的变化不会触发重绘，
    // 手指拖动会“冻结”在起点。
    private var dragPx by mutableFloatStateOf(0f)
    private var velocity = 0f
    private var lastNanos = 0L
    private var hapticArmed = true

    /** 当前视觉 translationY（px）；下拉跟手，其余时刻取弹簧动画值。 */
    val translationY: Float
        get() {
            val h = height.toFloat()
            return when {
                dragging && mode == DragMode.PullOpen -> -h + revealFromPull(dragPx)
                dragging && mode == DragMode.Dismiss -> dragPx.coerceIn(-h, 0f)
                else -> offset.value
            }
        }

    /** 展开比例 0..1，驱动遮罩透明度等。 */
    val fraction: Float
        get() = if (height == 0) 0f else (1f + translationY / height).coerceIn(0f, 1f)

    /** 下拉位移 → 展开距离：1:1 跟手到位后，过量部分按渐进阻尼压缩出细微过冲。 */
    private fun revealFromPull(raw: Float): Float {
        val h = height.toFloat()
        val direct = min(raw, h)
        val over = max(raw - h, 0f)
        return direct + over / (1f + over / (h * 0.75f))
    }

    fun onSizeChanged(px: Int) { if (px > 0) height = px }

    // —— 顶栏下拉展开 ——

    fun beginPull() {
        if (height == 0) return
        mode = DragMode.PullOpen; dragging = true
        dragPx = 0f; velocity = 0f; lastNanos = 0L; hapticArmed = true
    }
    fun pullBy(deltaPx: Float, haptic: HapticFeedback) {
        if (mode != DragMode.PullOpen) return
        dragPx = (dragPx + deltaPx).coerceAtLeast(0f)
        trackVelocity(deltaPx)
        // 越过展开阈值时给一次触感确认。
        if (revealFromPull(dragPx) >= height * 0.5f) {
            if (hapticArmed) { hapticArmed = false; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
        } else hapticArmed = true
    }
    fun endPull(scope: CoroutineScope, onOpen: () -> Unit) {
        if (mode != DragMode.PullOpen) return
        mode = DragMode.None; dragging = false
        val h = height.toFloat()
        val revealed = revealFromPull(dragPx)
        val pass = revealed > h * 0.5f || velocity > 2600f
        scope.launch {
            offset.snapTo(-h + revealed)
            if (pass) { android.util.Log.d("LeiFetchSheet", "pull pass → open"); onOpen(); offset.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 380f)) }
            else { android.util.Log.d("LeiFetchSheet", "pull fail → close"); offset.animateTo(-h, spring(dampingRatio = 0.85f, stiffness = 550f)) }
        }
    }
    fun cancelPull(scope: CoroutineScope) {
        if (mode != DragMode.PullOpen) return
        mode = DragMode.None; dragging = false
        val h = height.toFloat()
        scope.launch {
            offset.snapTo(-h + revealFromPull(dragPx))
            offset.animateTo(-h, spring(dampingRatio = 0.85f, stiffness = 550f))
        }
    }

    // —— 展开态从把手区上滑关闭 ——

    fun beginDismiss() {
        mode = DragMode.Dismiss; dragging = true
        dragPx = 0f; velocity = 0f; lastNanos = 0L
    }
    fun dismissBy(deltaPx: Float) {
        if (mode != DragMode.Dismiss) return
        dragPx = (dragPx + deltaPx).coerceAtMost(0f)
        trackVelocity(deltaPx)
    }
    fun endDismiss(scope: CoroutineScope, onDismiss: () -> Unit) {
        if (mode != DragMode.Dismiss) return
        mode = DragMode.None; dragging = false
        val h = height.toFloat()
        val visual = dragPx.coerceIn(-h, 0f)
        val dismiss = dragPx < -h * 0.3f || velocity < -2600f
        scope.launch {
            offset.snapTo(visual)
            if (dismiss) { onDismiss(); offset.animateTo(-h, spring(dampingRatio = 1f, stiffness = 800f)) }
            else offset.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 380f))
        }
    }

    private fun trackVelocity(deltaPx: Float) {
        val now = System.nanoTime()
        if (lastNanos != 0L && deltaPx != 0f) {
            val dt = (now - lastNanos) / 1e9f
            if (dt > 0.002f) velocity = 0.65f * velocity + 0.35f * (deltaPx / dt)
        }
        lastNanos = now
    }
}

/** 顶栏手势：从标题向下拖动拉出顶弹层，跟手位移、过半（或快速下甩）松手后弹性展开。
 *  检测器以 Unit 为 key，避免回调等每次重组新建的 key 让检测器重启、掐断手势。 */
internal fun Modifier.topPullToNewDownload(
    state: NewDownloadSheetState, scope: CoroutineScope, haptic: HapticFeedback, onOpen: () -> Unit,
): Modifier = this.pointerInput(Unit) {
    detectVerticalDragGestures(
        onDragStart = { state.beginPull() },
        onVerticalDrag = { change, dy -> if (state.dragging) { change.consume(); state.pullBy(dy, haptic) } },
        onDragEnd = { state.endPull(scope, onOpen) },
        onDragCancel = { state.cancelPull(scope) },
    )
}

@Composable
internal fun NewDownloadTopSheet(
    state: NewDownloadSheetState,
    open: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 首次测量后把收起锚点对准 -height。
    LaunchedEffect(state.height) {
        if (state.height > 0 && !state.offset.isRunning && state.offset.value < -state.height.toFloat())
            state.offset.snapTo(-state.height.toFloat())
    }
    // 打开 / 关闭请求的弹簧动画：按钮等入口从上方弹入，关闭快速上收。
    LaunchedEffect(open, state.height) {
        if (state.height == 0) return@LaunchedEffect
        if (open) {
            if (!state.dragging && state.offset.value < -1f) {
                if (state.offset.value < -state.height.toFloat()) state.offset.snapTo(-state.height.toFloat())
                state.offset.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 380f))
            }
        } else if (state.offset.value > -state.height.toFloat() + 0.5f) {
            state.offset.animateTo(-state.height.toFloat(), spring(dampingRatio = 1f, stiffness = 800f))
        }
    }
    BackHandler(enabled = open) { onDismiss() }

    val url = rememberTextFieldState()
    var attempted by rememberSaveable { mutableStateOf(false) }
    val parsed = Uri.parse(url.text.toString().trim())
    val valid = parsed.scheme in setOf("http", "https") && !parsed.host.isNullOrBlank()
    val focus = remember { FocusRequester() }
    LaunchedEffect(open) {
        if (!open) { url.edit { replace(0, length, "") }; attempted = false }
        else { delay(220); runCatching { focus.requestFocus() } }
    }

    val fraction = state.fraction
    // 完全收起时整体移出组合：graphicsLayer 平移不参与命中测试，收起后的弹层仍叠在顶栏上，
    // 其把手区手势与输入框会拦截顶栏下拉。height==0 时先组合一帧完成首次测量。
    val revealed = open || state.dragging || state.height == 0 || fraction > 0.001f
    Box(Modifier.fillMaxSize()) {
        if (revealed) {
        if (fraction > 0.005f) {
            // 遮罩：只有“原地未消费的抬起”才算点按关闭——detectTapGestures 不校验位移，
            // 会把弹层上拖动的松手误判成点按；这里按触摸误差自行把关。
            Box(Modifier.fillMaxSize()
                .graphicsLayer { alpha = fraction * 0.55f }
                .background(Color.Black)
                .pointerInput(open) {
                    val touchSlopRadius = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var tap = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            if ((change.position - down.position).getDistance() > touchSlopRadius) break
                            if (change.changedToUp()) { tap = true; break }
                        }
                        if (tap && open) onDismiss()
                    }
                })
        }
        val sheetShape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .graphicsLayer { translationY = state.translationY }
                .shadow(20.dp, sheetShape)
                .clip(sheetShape)
                .background(MiuixTheme.colorScheme.surfaceContainer)
                // 展开时吃掉弹层主体上的竖向拖动，避免穿透到顶栏的下拉手势（把手区除外）。
                .pointerInput(open) {
                    detectVerticalDragGestures { change, _ -> if (open) change.consume() }
                }
                .onSizeChanged { state.onSizeChanged(it.height) },
        ) {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 22.dp, end = 22.dp, bottom = 20.dp)) {
                // 把手区：指示条 + 标题；上滑关闭手势只挂在这里，避开输入框的选区手势。
                Column(
                    Modifier.fillMaxWidth().pointerInput(state, open) {
                        detectVerticalDragGestures(
                            onDragStart = { if (open) state.beginDismiss() },
                            onVerticalDrag = { change, dy -> if (state.dragging) { change.consume(); state.dismissBy(dy) } },
                            onDragEnd = { if (state.dragging) state.endDismiss(scope, onDismiss) },
                            onDragCancel = { if (state.dragging) state.endDismiss(scope, onDismiss) },
                        )
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.padding(top = 10.dp, bottom = 12.dp).size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.18f)))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Download, null, tint = MiuixTheme.colorScheme.primary)
                        Text("新建下载", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("添加 HTTP 或 HTTPS 文件链接，确认后开始下载。", fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Spacer(Modifier.height(14.dp))
                TextField(state = url, label = "粘贴下载链接",
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    lineLimits = TextFieldLineLimits.SingleLine)
                if (attempted && !valid) Text("请输入有效的 HTTP(S) 下载地址", fontSize = 13.sp, color = MiuixTheme.colorScheme.error)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton("取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                    TextButton("添加任务", onClick = { attempted = true; if (valid) onAdd(url.text.toString().trim()) },
                        modifier = Modifier.weight(1f))
                }
            }
        }
        }
    }
}
