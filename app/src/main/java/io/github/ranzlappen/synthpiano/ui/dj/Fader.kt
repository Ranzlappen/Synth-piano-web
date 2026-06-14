package io.github.ranzlappen.synthpiano.ui.dj

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Vertical fader (top = max, bottom = min) drawn with `Canvas` and driven by
 * a `pointerInput` drag, matching the custom-control style of the app
 * (`EnvelopeEditor`, `PianoKeyboard`). Optional [detentAt] snaps the value to
 * a center notch — used by the pitch fader's 0% center detent.
 */
@Composable
fun VerticalFader(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    label: String,
    valueText: String,
    modifier: Modifier = Modifier,
    detentAt: Float? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    val onChange = rememberUpdatedState(onValueChange)
    val min = valueRange.start
    val max = valueRange.endInclusive
    val detentWindow = (max - min) * 0.035f

    val trackColor = Color.White.copy(alpha = 0.18f)
    val thumbColor = MaterialTheme.colorScheme.onSurface
    val notchColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .width(44.dp)
                .padding(vertical = 4.dp)
                .pointerInput(Unit) {
                    fun emit(y: Float) {
                        val frac = (1f - (y / size.height)).coerceIn(0f, 1f)
                        var v = min + frac * (max - min)
                        if (detentAt != null && abs(v - detentAt) < detentWindow) v = detentAt
                        onChange.value(v)
                    }
                    detectDragGestures(
                        onDragStart = { emit(it.y) },
                        onDrag = { change, _ ->
                            change.consume()
                            emit(change.position.y)
                        },
                    )
                },
        ) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val frac = ((value - min) / (max - min)).coerceIn(0f, 1f)
            val thumbY = (1f - frac) * h

            // Track.
            drawLine(
                color = trackColor,
                start = Offset(cx, 0f),
                end = Offset(cx, h),
                strokeWidth = 6f,
            )
            // Center detent notch.
            if (detentAt != null) {
                val detentFrac = ((detentAt - min) / (max - min)).coerceIn(0f, 1f)
                val detentY = (1f - detentFrac) * h
                drawLine(
                    color = notchColor,
                    start = Offset(cx - w * 0.32f, detentY),
                    end = Offset(cx + w * 0.32f, detentY),
                    strokeWidth = 2f,
                )
            }
            // Filled portion from bottom up to the thumb.
            drawLine(
                color = accent,
                start = Offset(cx, h),
                end = Offset(cx, thumbY),
                strokeWidth = 6f,
            )
            // Thumb cap.
            val capW = w * 0.8f
            val capH = h * 0.045f
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(cx - capW / 2f, thumbY - capH / 2f),
                size = Size(capW, capH.coerceAtLeast(8f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
            )
        }
        Text(
            valueText,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}
