package io.github.ranzlappen.synthpiano.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.ranzlappen.synthpiano.audio.Adsr

/**
 * Live ADSR shape preview. Renders the classic Attack-Decay-Sustain-
 * Release envelope as a polyline scaled to the canvas. Sustain is drawn
 * for a fixed visual length so the shape always reads cleanly regardless
 * of the actual seconds-per-stage values.
 */
@Composable
fun AdsrPreview(
    adsr: Adsr,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Visual time budget per stage, normalized so the plot fits.
        // Compress real seconds into a friendly visual range.
        fun budget(sec: Float, max: Float = 2.0f): Float =
            (sec / max).coerceIn(0.05f, 1f)

        val attackLen = budget(adsr.attackSec)
        val decayLen = budget(adsr.decaySec)
        val sustainLen = 0.5f
        val releaseLen = budget(adsr.releaseSec, max = 3.0f)
        val total = attackLen + decayLen + sustainLen + releaseLen

        val xA = 0f
        val xPeak = (attackLen / total) * w
        val xSustainStart = ((attackLen + decayLen) / total) * w
        val xSustainEnd = ((attackLen + decayLen + sustainLen) / total) * w
        val xEnd = w

        val yPeak = h * 0.05f
        val ySustain = h - (h - yPeak) * adsr.sustain
        val yZero = h * 0.95f

        val path = Path().apply {
            moveTo(xA, yZero)
            lineTo(xPeak, yPeak)                 // attack
            lineTo(xSustainStart, ySustain)      // decay
            lineTo(xSustainEnd, ySustain)        // sustain
            lineTo(xEnd, yZero)                  // release
        }

        // Faint baseline.
        drawLine(
            color = Color.White.copy(alpha = 0.15f),
            start = Offset(0f, yZero),
            end = Offset(w, yZero),
            strokeWidth = 1f,
        )

        drawPath(path = path, color = color, style = Stroke(width = 2.5f))
    }
}
