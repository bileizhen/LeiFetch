package io.github.bileizhen.leifetch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.PI
import kotlin.math.sin

/** Decorative scheduling diagram, never presented as actual segment telemetry.
 * Compose's frame clock pauses offscreen and respects the system animator duration scale.
 */
@Composable
internal fun NsfxEngineMotion(active: Boolean, lanes: Int, modifier: Modifier = Modifier) {
    val count = lanes.coerceIn(1, 16)
    val travel = remember { Animatable(0f) }
    val emphasis = remember { Animatable(if (active) 1f else 0f) }
    var frame by remember { mutableStateOf(NsfxMotionFrame()) }
    LaunchedEffect(active, count) {
        val next = frame.transition(active, count, travel.value)
        travel.snapTo(0f)
        frame = next
        when (next.mode) {
            NsfxMotionMode.Running -> while (true) {
                travel.animateTo(1f, tween(1200, easing = LinearEasing))
                val particles = frame.sample(1f)
                travel.snapTo(0f)
                frame = frame.copy(particles = particles)
            }
            NsfxMotionMode.Draining -> {
                travel.animateTo(1f, tween(850, easing = LinearOutSlowInEasing))
                frame = NsfxMotionFrame()
            }
            NsfxMotionMode.Idle -> Unit
        }
    }
    LaunchedEffect(active) {
        emphasis.animateTo(if (active) 1f else 0f, tween(if (active) 200 else 850))
    }
    val transition = rememberInfiniteTransition(label = "NSFX engine")
    val phase by transition.animateFloat(0f, 1f,
        infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "Core breathing")
    val colors = MiuixTheme.colorScheme
    Canvas(modifier.semantics { contentDescription = "NSFX $count 线程调度示意，${when (frame.mode) {
        NsfxMotionMode.Running -> "传输中"
        NsfxMotionMode.Draining -> "收尾中"
        NsfxMotionMode.Idle -> "待机"
    }}" }) {
        val particles = frame.sample(travel.value)
        val laneParticles = particles.dropLast(1)
        val output = particles.lastOrNull()
        val accent = colors.primary
        val center = Offset(size.width * .77f, size.height * .5f)
        val pulse = (.5f + .5f * sin(phase * 2 * PI).toFloat())
        val coreSize = 34.dp.toPx().coerceAtMost(size.width * .27f)
        for (lane in 0 until count) {
            val y = if (count == 1) size.height / 2 else size.height * (.13f + .74f * lane / (count - 1))
            val nodes = listOf(Offset(size.width * .04f, y), Offset(size.width * .3f, y),
                Offset(size.width * .54f, center.y), Offset(center.x - coreSize / 2, center.y))
            val track = Path().apply { moveTo(nodes[0].x, nodes[0].y); nodes.drop(1).forEach { lineTo(it.x, it.y) } }
            drawPath(track, accent.copy(alpha = .13f + .11f * emphasis.value), style = Stroke(1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(accent.copy(alpha = .30f), 2.dp.toPx(), nodes.first())
            val particle = laneParticles.getOrNull(lane)
            if (particle != null && particle.opacity > 0f) {
                val progress = particle.position * (nodes.size - 1)
                val segment = progress.toInt().coerceAtMost(nodes.size - 2)
                val fraction = progress - segment
                val position = nodes[segment] + (nodes[segment + 1] - nodes[segment]) * fraction
                drawCircle(accent.copy(alpha = .09f * particle.opacity), 6.dp.toPx(), position)
                drawCircle(accent.copy(alpha = .9f * particle.opacity), 2.dp.toPx(), position)
            }
        }
        drawRoundRect(accent.copy(alpha = .04f + pulse * .04f),
            topLeft = center - Offset(coreSize * .75f, coreSize * .75f),
            size = Size(coreSize * 1.5f, coreSize * 1.5f), cornerRadius = CornerRadius(16.dp.toPx()))
        drawRoundRect(colors.surfaceContainer, topLeft = center - Offset(coreSize / 2, coreSize / 2),
            size = Size(coreSize, coreSize), cornerRadius = CornerRadius(10.dp.toPx()))
        drawRoundRect(accent.copy(alpha = .45f + pulse * .25f), topLeft = center - Offset(coreSize / 2, coreSize / 2),
            size = Size(coreSize, coreSize), cornerRadius = CornerRadius(10.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        val mark = Path().apply {
            moveTo(center.x - coreSize * .16f, center.y - coreSize * .22f)
            lineTo(center.x - coreSize * .16f, center.y + coreSize * .22f)
            lineTo(center.x + coreSize * .17f, center.y - coreSize * .22f)
            lineTo(center.x + coreSize * .17f, center.y + coreSize * .22f)
        }
        drawPath(mark, accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val outputStart = Offset(center.x + coreSize / 2, center.y)
        val outputEnd = Offset(size.width * .98f, center.y)
        drawLine(accent.copy(alpha = .25f), outputStart, outputEnd, 1.dp.toPx())
        if (output != null && output.opacity > 0f)
            drawCircle(accent.copy(alpha = output.opacity), 2.dp.toPx(), outputStart + (outputEnd - outputStart) * output.position)
    }
}
