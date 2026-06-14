package io.github.ranzlappen.synthpiano.ui.dj

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Milliseconds of seek per full revolution of the platter (vinyl-like feel). */
private const val MS_PER_REVOLUTION = 1800f

/**
 * Large circular jog wheel. Dragging around the platter scratches/seeks the
 * deck in real time (angular delta → milliseconds), mirroring the
 * Canvas + `pointerInput` drag pattern used by `EnvelopeEditor` /
 * `PianoKeyboard`. The platter marker rotates with [positionMs] so the wheel
 * visibly spins while a track plays.
 */
@Composable
fun JogWheel(
    positionMs: Int,
    isPlaying: Boolean,
    onScratch: (deltaMs: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rim = MaterialTheme.colorScheme.primary
    val plate = MaterialTheme.colorScheme.surfaceVariant
    val marker = MaterialTheme.colorScheme.onSurface
    val glow = MaterialTheme.colorScheme.primary.copy(alpha = if (isPlaying) 0.55f else 0.18f)

    val onScratchRef = rememberUpdatedState(onScratch)
    var lastAngle by remember { mutableFloatStateOf(0f) }

    val piF = PI.toFloat()
    val twoPi = (2 * PI).toFloat()

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        lastAngle = angleOf(offset, size.width.toFloat(), size.height.toFloat())
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val a = angleOf(change.position, size.width.toFloat(), size.height.toFloat())
                        var d = a - lastAngle
                        if (d > piF) d -= twoPi
                        if (d < -piF) d += twoPi
                        lastAngle = a
                        val deltaMs = (d / twoPi * MS_PER_REVOLUTION).toInt()
                        if (deltaMs != 0) onScratchRef.value(deltaMs)
                    },
                )
            },
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = min(cx, cy)

        // Outer glow ring (brighter while playing).
        drawCircle(color = glow, radius = radius, center = Offset(cx, cy), style = Stroke(width = 6f))
        // Platter body.
        drawCircle(color = plate.copy(alpha = 0.85f), radius = radius * 0.92f, center = Offset(cx, cy))
        drawCircle(
            color = rim,
            radius = radius * 0.92f,
            center = Offset(cx, cy),
            style = Stroke(width = 3f),
        )
        // Spindle.
        drawCircle(color = rim, radius = radius * 0.10f, center = Offset(cx, cy))

        // Rotating position marker.
        val angle = (positionMs % MS_PER_REVOLUTION.toInt()) / MS_PER_REVOLUTION * twoPi - piF / 2f
        val inner = radius * 0.18f
        val outer = radius * 0.88f
        drawLine(
            color = marker,
            start = Offset(cx + inner * cos(angle), cy + inner * sin(angle)),
            end = Offset(cx + outer * cos(angle), cy + outer * sin(angle)),
            strokeWidth = 4f,
        )
        drawCircle(
            color = marker,
            radius = radius * 0.06f,
            center = Offset(cx + outer * cos(angle), cy + outer * sin(angle)),
        )
    }
}

private fun angleOf(p: Offset, width: Float, height: Float): Float {
    val dx = p.x - width / 2f
    val dy = p.y - height / 2f
    return atan2(dy, dx)
}
