package io.github.ranzlappen.synthpiano.ui.score

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.data.midi.MidiScore
import io.github.ranzlappen.synthpiano.data.midi.Note
import kotlin.math.max

/**
 * Read-only piano-roll renderer. Editing affordances arrive in subsequent
 * batches; this component first establishes the coordinate system and the
 * baseline visual the editor will mutate.
 *
 * Coordinate system:
 *   x  =  tick   * pxPerTick      (= ticks * pxPerBeat / ppq)
 *   y  =  (maxPitch - midi) * pxPerSemitone
 *
 * Time runs left → right. Higher pitches are at the top, matching DAW
 * convention. Notes from every channel are rendered together; channel
 * is colour-encoded so polyphonic / multi-instrument SMFs are legible.
 */

private const val DEFAULT_MIN_PITCH = 24   // C1
private const val DEFAULT_MAX_PITCH = 108  // C8

private val CHANNEL_COLORS = listOf(
    Color(0xFF1976D2), // 0  blue
    Color(0xFFD32F2F), // 1  red
    Color(0xFF388E3C), // 2  green
    Color(0xFFF57C00), // 3  orange
    Color(0xFF7B1FA2), // 4  purple
    Color(0xFF00838F), // 5  teal
    Color(0xFFFBC02D), // 6  yellow
    Color(0xFF5D4037), // 7  brown
    Color(0xFFC2185B), // 8  pink
    Color(0xFF689F38), // 9  light green
    Color(0xFF455A64), // 10 blue grey  (drum kit by GM convention)
    Color(0xFFE64A19), // 11 deep orange
    Color(0xFF512DA8), // 12 deep purple
    Color(0xFF0288D1), // 13 light blue
    Color(0xFFAFB42B), // 14 lime
    Color(0xFF6D4C41), // 15 brown 2
)

@Composable
fun PianoRollEditor(
    score: MidiScore,
    modifier: Modifier = Modifier,
    currentTick: Int = -1,
    pxPerBeat: Dp = 80.dp,
    pxPerSemitone: Dp = 12.dp,
    keyboardWidth: Dp = 56.dp,
    minPitch: Int = DEFAULT_MIN_PITCH,
    maxPitch: Int = DEFAULT_MAX_PITCH,
) {
    val density = LocalDensity.current
    val pxPerBeatF = with(density) { pxPerBeat.toPx() }
    val pxPerSemitoneF = with(density) { pxPerSemitone.toPx() }
    val keyboardWidthPx = with(density) { keyboardWidth.toPx() }

    val pitchRange = (maxPitch - minPitch + 1).coerceAtLeast(1)
    val totalBeats = beatsTotal(score)
    val totalWidthPx = max(pxPerBeatF * totalBeats, pxPerBeatF * 8f)  // min 8 beats wide
    val totalHeightPx = pitchRange * pxPerSemitoneF

    val totalWidthDp = with(density) { totalWidthPx.toDp() }
    val totalHeightDp = with(density) { totalHeightPx.toDp() }

    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    Row(modifier = modifier.fillMaxSize()) {
        // Fixed keyboard column on the left
        Box(
            modifier = Modifier
                .width(keyboardWidth)
                .fillMaxHeight()
                .verticalScroll(vScroll),
        ) {
            Canvas(
                modifier = Modifier.size(keyboardWidth, totalHeightDp),
            ) {
                drawKeyboard(
                    minPitch = minPitch,
                    maxPitch = maxPitch,
                    pxPerSemitone = pxPerSemitoneF,
                    width = size.width,
                )
            }
        }

        // Scrollable note grid (fills remaining width).
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(hScroll)
                .verticalScroll(vScroll),
        ) {
            Canvas(
                modifier = Modifier.size(totalWidthDp, totalHeightDp),
            ) {
                drawGrid(
                    minPitch = minPitch,
                    maxPitch = maxPitch,
                    pxPerSemitone = pxPerSemitoneF,
                    pxPerBeat = pxPerBeatF,
                    totalBeats = totalBeats.coerceAtLeast(8f),
                    canvasSize = size,
                )
                for (n in score.notes) {
                    drawNote(
                        note = n,
                        ppq = score.ppq,
                        pxPerBeat = pxPerBeatF,
                        pxPerSemitone = pxPerSemitoneF,
                        minPitch = minPitch,
                        maxPitch = maxPitch,
                    )
                }
                if (currentTick >= 0) {
                    val playheadX = currentTick.toFloat() / score.ppq * pxPerBeatF
                    drawLine(
                        color = Color(0xFFFF1744),
                        start = Offset(playheadX, 0f),
                        end = Offset(playheadX, size.height),
                        strokeWidth = 2f,
                    )
                }
            }
        }
    }
}

private fun beatsTotal(score: MidiScore): Float {
    if (score.notes.isEmpty()) return 0f
    val maxEndTick = score.notes.maxOf { it.startTicks + it.durationTicks }
    return maxEndTick.toFloat() / score.ppq
}

private fun isBlackKey(midi: Int): Boolean {
    return when (midi % 12) {
        1, 3, 6, 8, 10 -> true
        else -> false
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawKeyboard(
    minPitch: Int,
    maxPitch: Int,
    pxPerSemitone: Float,
    width: Float,
) {
    drawRect(color = Color(0xFFEEEEEE), size = Size(width, size.height))
    for (midi in minPitch..maxPitch) {
        val y = (maxPitch - midi) * pxPerSemitone
        if (isBlackKey(midi)) {
            drawRect(
                color = Color(0xFF424242),
                topLeft = Offset(0f, y),
                size = Size(width * 0.65f, pxPerSemitone),
            )
        }
        // Faint horizontal divider
        drawLine(
            color = Color(0x33000000),
            start = Offset(0f, y + pxPerSemitone),
            end = Offset(width, y + pxPerSemitone),
            strokeWidth = 0.5f,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrid(
    minPitch: Int,
    maxPitch: Int,
    pxPerSemitone: Float,
    pxPerBeat: Float,
    totalBeats: Float,
    canvasSize: Size,
) {
    drawRect(color = Color(0xFFFAFAFA), size = canvasSize)
    // Horizontal pitch rows: tint black-key rows slightly darker for orientation.
    for (midi in minPitch..maxPitch) {
        val y = (maxPitch - midi) * pxPerSemitone
        if (isBlackKey(midi)) {
            drawRect(
                color = Color(0x14000000),
                topLeft = Offset(0f, y),
                size = Size(canvasSize.width, pxPerSemitone),
            )
        }
        // C-row separator emphasis
        if (midi % 12 == 0) {
            drawLine(
                color = Color(0x55000000),
                start = Offset(0f, y + pxPerSemitone),
                end = Offset(canvasSize.width, y + pxPerSemitone),
                strokeWidth = 1f,
            )
        }
    }
    // Vertical beat lines
    val totalBeatsCeil = (totalBeats.toInt() + 1)
    for (b in 0..totalBeatsCeil) {
        val x = b * pxPerBeat
        val isBar = b % 4 == 0
        drawLine(
            color = if (isBar) Color(0x66000000) else Color(0x22000000),
            start = Offset(x, 0f),
            end = Offset(x, canvasSize.height),
            strokeWidth = if (isBar) 1.2f else 0.6f,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNote(
    note: Note,
    ppq: Int,
    pxPerBeat: Float,
    pxPerSemitone: Float,
    minPitch: Int,
    maxPitch: Int,
) {
    if (note.midi < minPitch || note.midi > maxPitch) return
    val x = note.startTicks.toFloat() / ppq * pxPerBeat
    val y = (maxPitch - note.midi) * pxPerSemitone
    val w = (note.durationTicks.toFloat() / ppq * pxPerBeat).coerceAtLeast(2f)
    val h = pxPerSemitone
    val color = CHANNEL_COLORS[note.channel.coerceIn(0, 15)]
    // Velocity controls alpha (50%..100%)
    val alpha = 0.5f + 0.5f * (note.velocity.coerceIn(1, 127) / 127f)
    drawRect(
        color = color.copy(alpha = alpha),
        topLeft = Offset(x, y),
        size = Size(w, h),
    )
    drawRect(
        color = Color(0x88000000),
        topLeft = Offset(x, y),
        size = Size(w, h),
        style = Stroke(width = 0.8f),
    )
}
