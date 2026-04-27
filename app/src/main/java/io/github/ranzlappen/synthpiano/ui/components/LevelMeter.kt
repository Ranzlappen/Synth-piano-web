package io.github.ranzlappen.synthpiano.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.audio.SynthController
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * Animated peak meter bound to [SynthController.engine].masterPeak().
 *
 * The native peak is an atomic-exchange max-since-last-read, so polling
 * every ~33ms gives us a clean VU-style readout without losing transients.
 * The display peak holds for a short window then decays linearly, matching
 * conventional studio meter ballistics.
 */
@Composable
fun LevelMeter(
    synth: SynthController,
    modifier: Modifier = Modifier,
    width: Dp = 120.dp,
    height: Dp = 10.dp,
    pollIntervalMs: Long = 33L,
    decayPerSecond: Float = 1.6f,
) {
    var peak by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var displayed = 0f
        var lastNs = System.nanoTime()
        while (true) {
            val now = System.nanoTime()
            val dt = ((now - lastNs).coerceAtLeast(0L)).toFloat() / 1_000_000_000f
            lastNs = now

            val raw = runCatching { synth.engine().masterPeak() }.getOrDefault(0f)
            val decayed = (displayed - decayPerSecond * dt).coerceAtLeast(0f)
            displayed = max(decayed, raw)
            peak = displayed.coerceIn(0f, 1f)
            delay(pollIntervalMs)
        }
    }

    val animated by animateFloatAsState(targetValue = peak, label = "levelMeter")

    Box(modifier = modifier.width(width).height(height)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Background trough.
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.45f),
                topLeft = Offset(0f, 0f),
                size = Size(w, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f),
            )
            // Filled bar with green→amber→red segments based on level.
            val v = animated.coerceIn(0f, 1f)
            if (v > 0f) {
                val barW = w * v
                val color = when {
                    v < 0.7f -> Color(0xFF8AE08A)
                    v < 0.9f -> Color(0xFFFFC872)
                    else -> Color(0xFFFF7A7A)
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(0f, 0f),
                    size = Size(barW, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2f),
                )
            }
        }
    }
}

