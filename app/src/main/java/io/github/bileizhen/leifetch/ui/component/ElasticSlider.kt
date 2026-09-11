package io.github.bileizhen.leifetch.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/** 越界最大位移 30dp：图标外扩这么多仍不出屏。 */
private val MaxOverflow = 30.dp

/** 轨道高度：静息 10dp，按下 15dp。 */
private val BarRestHeight = 10.dp
private val BarActiveHeight = 15.dp

/** 触摸区 44dp；± 字形 18dp 画在 36dp 的命中区里。 */
private val TouchHeight = 44.dp
private val StepIconBox = 36.dp
private val StepIconSize = 18.dp

/** 拉满时轨道的纵向压缩比例 0.8。 */
private const val BarStretchScaleY = 0.8f

/** 图标弹跳峰值 1.4 倍，单程 125ms。 */
private const val IconPopScale = 1.4f
private const val IconPopDuration = 125

/** 按下时 ± 图标放大 8%。 */
private const val IconPressScale = 0.08f

/** ± 字形线宽 2.2dp，臂长为画布半宽的 0.82。 */
private val GlyphStroke = 2.2.dp
private const val GlyphArmRatio = 0.82f

private enum class ElasticRegion { Left, Middle, Right }

@Composable
fun ElasticSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    step: Float = 0f,
    enabled: Boolean = true,
    contentDescription: String? = null,
    onValueChangeFinished: ((Float) -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val maxOverflowPx = with(LocalDensity.current) { MaxOverflow.toPx() }
    val rangeStart = valueRange.start
    val rangeEnd = valueRange.endInclusive
    val span = (rangeEnd - rangeStart).takeIf { it > 0f } ?: 1f
    val description = contentDescription

    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val currentValue = rememberUpdatedState(value)

    var pressed by remember { mutableStateOf(false) }
    var overflowTarget by remember { mutableFloatStateOf(0f) }
    var stretchFromRight by remember { mutableStateOf(true) }
    var region by remember { mutableStateOf(ElasticRegion.Middle) }
    var trackWidth by remember { mutableIntStateOf(0) }
    var lastEmitted by remember { mutableFloatStateOf(value) }

    val overflow by animateFloatAsState(
        targetValue = overflowTarget,
        animationSpec = if (pressed) snap() else spring(dampingRatio = 0.5f, stiffness = 420f),
        label = "ElasticSliderOverflow",
    )
    val active by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 900f),
        label = "ElasticSliderActive",
    )
    val barHeight = lerp(BarRestHeight, BarActiveHeight, active)
    val trackColor = if (enabled) {
        MiuixTheme.colorScheme.sliderBackground
    } else {
        MiuixTheme.colorScheme.disabledSecondary
    }
    val fillColor = if (enabled) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.disabledPrimarySlider
    }

    val leftPop = remember { Animatable(1f) }
    val rightPop = remember { Animatable(1f) }

    fun popIcon(target: Animatable<Float, AnimationVector1D>) {
        scope.launch {
            target.snapTo(1f)
            target.animateTo(IconPopScale, tween(IconPopDuration))
            target.animateTo(1f, tween(IconPopDuration))
        }
    }

    fun emit(next: Float) {
        val clamped = next.coerceIn(rangeStart, rangeEnd)
        currentOnValueChange(clamped)
        if (clamped != lastEmitted) {
            lastEmitted = clamped
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun updateFromX(x: Float, width: Float) {
        if (width <= 0f) return
        val raw = rangeStart + (x / width).coerceIn(0f, 1f) * span
        emit(if (step > 0f) (raw / step).roundToInt() * step else raw)

        val nextRegion = when {
            x < 0f -> ElasticRegion.Left
            x > width -> ElasticRegion.Right
            else -> ElasticRegion.Middle
        }
        if (region != nextRegion) {
            region = nextRegion
            when (nextRegion) {
                ElasticRegion.Left -> {
                    popIcon(leftPop)
                    haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                }

                ElasticRegion.Right -> {
                    popIcon(rightPop)
                    haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                }

                ElasticRegion.Middle -> Unit
            }
        }
        stretchFromRight = x < width / 2f
        val beyond = when {
            x < 0f -> -x
            x > width -> x - width
            else -> 0f
        }
        overflowTarget = decay(beyond, maxOverflowPx)
    }

    fun nudge(direction: Int) {
        val delta = (if (step > 0f) step else span / 10f) * direction
        val next = (currentValue.value + delta).coerceIn(rangeStart, rangeEnd)
        if (next == currentValue.value) return
        emit(next)
        currentOnValueChangeFinished?.invoke(next)
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StepIcon(
            plus = false,
            label = "减少",
            enabled = enabled && value > rangeStart,
            pop = leftPop.value,
            pressScale = 1f + IconPressScale * active,
            translationX = if (region == ElasticRegion.Left) -overflow else 0f,
            onTriggered = { popIcon(leftPop) },
            onClick = { nudge(-1) },
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(TouchHeight)
                .onSizeChanged { trackWidth = it.width }
                .pointerInput(enabled, rangeStart, rangeEnd, step) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val pointerId = down.id
                        pressed = true
                        var dragging = false
                        var cancelled = false

                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == pointerId }
                            if (change == null || change.isConsumed) {
                                cancelled = true
                                break
                            }
                            if (!change.pressed) break
                            if (!dragging) {
                                val dx = change.position.x - down.position.x
                                val dy = change.position.y - down.position.y
                                if (abs(dy) > viewConfiguration.touchSlop && abs(dy) > abs(dx)) {
                                    cancelled = true
                                    break
                                }
                                if (abs(dx) > viewConfiguration.touchSlop) dragging = true
                            }
                            if (dragging) {
                                updateFromX(change.position.x, size.width.toFloat())
                                change.consume()
                            }
                        }

                        if (!cancelled) {
                            if (!dragging) updateFromX(down.position.x, size.width.toFloat())
                            currentOnValueChangeFinished?.invoke(lastEmitted)
                        }
                        pressed = false
                        overflowTarget = 0f
                    }
                }
                .semantics {
                    if (description != null) this.contentDescription = description
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = value,
                        range = rangeStart..rangeEnd,
                        steps = if (step > 0f) ((span / step).roundToInt() - 1).coerceAtLeast(0) else 0,
                    )
                    if (enabled) {
                        setProgress { target ->
                            val clamped = target.coerceIn(rangeStart, rangeEnd)
                            val snapped = if (step > 0f) (clamped / step).roundToInt() * step else clamped
                            emit(snapped)
                            currentOnValueChangeFinished?.invoke(snapped)
                            true
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .graphicsLayer {
                        scaleX = if (trackWidth > 0) 1f + overflow / trackWidth else 1f
                        scaleY = 1f + (overflow / maxOverflowPx) * (BarStretchScaleY - 1f)
                        transformOrigin = TransformOrigin(if (stretchFromRight) 1f else 0f, 0.5f)
                    },
            ) {
                val radius = size.height / 2f
                drawRoundRect(color = trackColor, cornerRadius = CornerRadius(radius, radius))
                val fraction = ((value - rangeStart) / span).coerceIn(0f, 1f)
                val fillWidth = (size.width * fraction).coerceAtLeast(size.height)
                drawRoundRect(
                    color = fillColor,
                    size = Size(fillWidth, size.height),
                    cornerRadius = CornerRadius(radius, radius),
                )
            }
        }
        StepIcon(
            plus = true,
            label = "增加",
            enabled = enabled && value < rangeEnd,
            pop = rightPop.value,
            pressScale = 1f + IconPressScale * active,
            translationX = if (region == ElasticRegion.Right) overflow else 0f,
            onTriggered = { popIcon(rightPop) },
            onClick = { nudge(1) },
        )
    }
}

@Composable
private fun StepIcon(
    plus: Boolean,
    label: String,
    enabled: Boolean,
    pop: Float,
    pressScale: Float,
    translationX: Float,
    onTriggered: () -> Unit,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(StepIconBox)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClickLabel = label,
                        role = Role.Button,
                        onClick = {
                            onTriggered()
                            onClick()
                        },
                    )
                } else {
                    Modifier
                },
            )
            .semantics { contentDescription = label }
            .graphicsLayer {
                scaleX = pop * pressScale
                scaleY = pop * pressScale
                this.translationX = translationX
            },
        contentAlignment = Alignment.Center,
    ) {
        StepGlyph(
            plus = plus,
            tint = if (enabled) {
                MiuixTheme.colorScheme.onSurfaceVariantActions
            } else {
                MiuixTheme.colorScheme.disabledOnSecondaryVariant
            },
        )
    }
}

@Composable
private fun StepGlyph(plus: Boolean, tint: Color) {
    Canvas(Modifier.size(StepIconSize)) {
        val stroke = GlyphStroke.toPx()
        val center = size.width / 2f
        val arm = size.minDimension / 2f * GlyphArmRatio
        drawLine(
            color = tint,
            start = Offset(center - arm, center),
            end = Offset(center + arm, center),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        if (plus) {
            drawLine(
                color = tint,
                start = Offset(center, center - arm),
                end = Offset(center, center + arm),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun decay(input: Float, max: Float): Float {
    if (max <= 0f) return 0f
    val entry = input / max
    val sigmoid = 2f * (1f / (1f + exp(-entry)) - 0.5f)
    return sigmoid * max
}
