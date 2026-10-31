package com.sandpause.app.ui.child

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.sandpause.app.R
import com.sandpause.app.ui.theme.ChildPalette
import com.sandpause.core.hourglass.Mood
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin

/**
 * The hourglass. The sand levels follow the fraction of time left, read on
 * every frame. Frames stop when the screen is off, so nothing is drawn (and no
 * battery is used) while the display is dark.
 *
 * Mood drives the colours: yellow (resting), red (impatient, with a shake) and
 * green (finished, flipped with confetti).
 */
@Composable
fun Hourglass(
    mood: Mood,
    fraction: () -> Float,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Advances the frame clock. The draw phase reads this state, so only drawing repeats, not composition.
    val frame = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { nanos -> frame.longValue = nanos }
        }
    }

    val flip by animateFloatAsState(
        targetValue = if (mood == Mood.FINISHED) 180f else 0f,
        animationSpec = tween(durationMillis = 900),
        label = "hourglass-flip",
    )
    val sandColor by animateColorAsState(
        targetValue = when (mood) {
            Mood.RESTING -> ChildPalette.YellowSand
            Mood.IMPATIENT -> ChildPalette.RedSand
            Mood.FINISHED -> ChildPalette.GreenSand
        },
        animationSpec = tween(durationMillis = 600),
        label = "hourglass-sand",
    )
    val description = stringResource(
        when (mood) {
            Mood.RESTING -> R.string.hourglass_description_resting
            Mood.IMPATIENT -> R.string.hourglass_description_impatient
            Mood.FINISHED -> R.string.hourglass_description_finished
        },
    )

    Box(
        modifier = modifier
            .aspectRatio(0.62f)
            .semantics {
                contentDescription = description
                stateDescription = mood.name
            }
            .pointerInput(onTap) { detectTapGestures(onTap = { onTap() }) },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val t = frame.longValue / 1_000_000_000.0
            val fill = fraction().coerceIn(0f, 1f)
            val shake = if (mood == Mood.IMPATIENT) (sin(t * 2 * PI * 9.0) * 10.0).toFloat() else 0f
            translate(left = shake, top = 0f) {
                rotate(degrees = flip, pivot = Offset(size.width / 2f, size.height / 2f)) {
                    drawHourglass(fill = fill, sand = sandColor, mood = mood, t = t)
                }
            }
        }
    }
}

private fun DrawScope.drawHourglass(fill: Float, sand: Color, mood: Mood, t: Double) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val top = h * 0.06f
    val bottom = h * 0.94f
    val mid = h * 0.5f
    val bulb = w * 0.36f
    val neck = w * 0.035f
    val glass = ChildPalette.Glass

    val topBulb = Path().apply {
        moveTo(cx - bulb, top)
        lineTo(cx + bulb, top)
        quadraticTo(cx + bulb, mid - h * 0.10f, cx + neck, mid)
        lineTo(cx - neck, mid)
        quadraticTo(cx - bulb, mid - h * 0.10f, cx - bulb, top)
        close()
    }
    val bottomBulb = Path().apply {
        moveTo(cx - neck, mid)
        lineTo(cx + neck, mid)
        quadraticTo(cx + bulb, mid + h * 0.10f, cx + bulb, bottom)
        lineTo(cx - bulb, bottom)
        quadraticTo(cx - bulb, mid + h * 0.10f, cx - neck, mid)
        close()
    }

    // Top bulb: sand sits on the neck and drains as time passes.
    val topLevel = mid - (mid - top) * fill
    clipPath(topBulb) {
        drawRect(color = sand, topLeft = Offset(0f, topLevel), size = Size(w, mid - topLevel))
    }
    // Bottom bulb: sand builds up from the base.
    val bottomLevel = bottom - (bottom - mid) * (1f - fill)
    clipPath(bottomBulb) {
        drawRect(color = sand, topLeft = Offset(0f, bottomLevel), size = Size(w, bottom - bottomLevel))
    }

    // Falling stream while time is running.
    if (mood != Mood.FINISHED && fill > 0f && fill < 1f) {
        val streamCount = 14
        for (i in 0 until streamCount) {
            val phase = ((t * 0.9) + i.toDouble() / streamCount).mod(1.0).toFloat()
            val y = mid + (bottomLevel - mid) * phase
            val x = cx + sin(i * 1.7).toFloat() * neck * 0.4f
            drawCircle(color = sand, radius = w * 0.018f, center = Offset(x, y))
        }
    }

    // Glass outline and the two end caps.
    val stroke = Stroke(width = w * 0.025f)
    drawPath(topBulb, color = glass, style = stroke)
    drawPath(bottomBulb, color = glass, style = stroke)
    drawRoundRect(
        color = glass,
        topLeft = Offset(cx - bulb * 1.1f, top - h * 0.035f),
        size = Size(bulb * 2.2f, h * 0.03f),
    )
    drawRoundRect(
        color = glass,
        topLeft = Offset(cx - bulb * 1.1f, bottom + h * 0.005f),
        size = Size(bulb * 2.2f, h * 0.03f),
    )

    if (mood == Mood.FINISHED) drawConfetti(w, h, t)
}

private fun DrawScope.drawConfetti(w: Float, h: Float, t: Double) {
    val colors = listOf(
        Color(0xFFFFD54F), Color(0xFFFF7043), Color(0xFF42A5F5), Color(0xFFAB47BC), Color(0xFF66BB6A),
    )
    for (i in 0 until CONFETTI_COUNT) {
        val x = ((i * 37) % 100) / 100f * w
        val y = ((t * 0.35 + i * 0.0731).mod(1.0)).toFloat() * h
        val radius = w * (0.012f + (i % 3) * 0.004f)
        drawCircle(color = colors[i % colors.size], radius = radius, center = Offset(x, y))
    }
}

private const val CONFETTI_COUNT = 70
