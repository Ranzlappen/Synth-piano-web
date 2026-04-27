package io.github.ranzlappen.synthpiano.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.ranzlappen.synthpiano.audio.SynthController
import kotlinx.coroutines.delay

/**
 * Live mono oscilloscope. Polls [SynthController.engine().drainScope] at
 * ~30fps, downsamples the latest window into [pixelColumns] points, and
 * draws a simple polyline on a black grid background.
 *
 * Enables the native scope tap on first composition. Once enabled the tap
 * stays on for the lifetime of the process — the [HeaderStrip] instance is
 * always mounted, and disabling the tap on dispose would race with other
 * mounted Oscilloscopes (e.g. the SOUND tab's larger waveform). The cost
 * of the always-on tap is negligible: a single ring-buffer write per
 * audio block.
 */
@Composable
fun Oscilloscope(
    synth: SynthController,
    color: Color,
    modifier: Modifier = Modifier,
    pixelColumns: Int = 256,
    pollIntervalMs: Long = 33L,
) {
    val buf = remember { FloatArray(2048) }
    val display = remember { FloatArray(pixelColumns) }
    var version by remember { mutableStateOf(0) }

    DisposableEffect(synth) {
        synth.engine().setScopeEnabled(true)
        onDispose { /* leave the tap on; see KDoc */ }
    }

    LaunchedEffect(synth) {
        while (true) {
            val n = runCatching { synth.engine().drainScope(buf, buf.size) }.getOrDefault(0)
            if (n > 0) {
                // Downsample most-recent n samples into display columns.
                val src = buf
                val ratio = n.toFloat() / pixelColumns
                for (col in 0 until pixelColumns) {
                    val idx = (col * ratio).toInt().coerceIn(0, n - 1)
                    display[col] = src[idx]
                }
                version++
            }
            delay(pollIntervalMs)
        }
    }

    Canvas(modifier = modifier) {
        // Read `version` so Compose treats this draw as dependent on it;
        // FloatArray contents alone aren't observable, so we bump version
        // each time we refill `display` and rely on recomposition to
        // re-run this draw lambda with the latest samples.
        @Suppress("UNUSED_EXPRESSION") version
        val w = size.width
        val h = size.height
        val mid = h / 2f

        // Faint center reference line.
        drawLine(
            color = Color.White.copy(alpha = 0.15f),
            start = Offset(0f, mid),
            end = Offset(w, mid),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
        )

        // Polyline.
        val path = Path()
        val dx = w / (pixelColumns - 1).coerceAtLeast(1)
        for (i in 0 until pixelColumns) {
            val x = i * dx
            val y = mid - display[i].coerceIn(-1f, 1f) * (h * 0.45f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2f),
        )
    }
}

