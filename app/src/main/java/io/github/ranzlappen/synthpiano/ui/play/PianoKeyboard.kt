package io.github.ranzlappen.synthpiano.ui.play

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ranzlappen.synthpiano.audio.NoteSource
import io.github.ranzlappen.synthpiano.ui.theme.KeyBlack
import io.github.ranzlappen.synthpiano.ui.theme.KeyLabel
import io.github.ranzlappen.synthpiano.ui.theme.KeyWhite
import io.github.ranzlappen.synthpiano.ui.theme.SourceHwKey
import io.github.ranzlappen.synthpiano.ui.theme.SourceMidi
import io.github.ranzlappen.synthpiano.ui.theme.SourceScore
import io.github.ranzlappen.synthpiano.ui.theme.SourceTouch

/** Lowest white-key MIDI note rendered (A0). */
const val PIANO_FIRST_MIDI: Int = 21

/** Total white keys A0..C8 inclusive. */
const val PIANO_WHITE_KEY_COUNT: Int = 52

/** Width of one white key at 1.0× zoom, in dp. */
val PIANO_WHITE_KEY_DP: Dp = 56.dp

/** Zoom clamps for the +/- buttons in PerformTab. */
internal const val ZOOM_MIN = 0.1f
internal const val ZOOM_MAX = 2.0f

/** White-key pitch classes (C, D, E, F, G, A, B). */
private val WHITE_KEY_PCS: Set<Int> = setOf(0, 2, 4, 5, 7, 9, 11)

/** A black key positioned 65% of the way across its left-side white key. */
private data class BlackKeyDef(val midi: Int, val leftWhiteIndex: Int)

/**
 * Multi-touch piano keyboard with per-source key coloring.
 * Renders a fixed-pitch, full-piano-range Canvas wrapped in a horizontal
 * scroll container so the user can pan from A0 to C8.
 *
 * Each pointer is mapped to one MIDI note; sliding between keys releases
 * the previous note and triggers the new one. Pressed keys color according
 * to which input device fired them. N-finger play is unrestricted — there
 * is no in-keyboard zoom gesture; zoom is driven by external buttons.
 *
 * @param scrollState hoisted scroll state so the parent can derive the
 * leftmost visible C and persist scroll position.
 * @param heldBySource Map of pressed MIDI note → source for coloring.
 * @param zoom Current zoom factor (clamped to [ZOOM_MIN]..[ZOOM_MAX]).
 */
@Composable
fun PianoKeyboard(
    modifier: Modifier = Modifier,
    scrollState: ScrollState,
    heldBySource: Map<Int, NoteSource>,
    zoom: Float,
    onNoteOn: (Int) -> Unit,
    onNoteOff: (Int) -> Unit,
    firstMidi: Int = PIANO_FIRST_MIDI,
    whiteKeyCount: Int = PIANO_WHITE_KEY_COUNT,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val pointerNotes = remember { mutableMapOf<Long, Int>() }
    var size by remember { mutableStateOf(IntSize.Zero) }

    // Forward latest note callbacks via rememberUpdatedState so the
    // long-lived pointerInput block always invokes the freshest closure
    // (which reads current sticky/held modifier sets in PerformTab).
    val currentOnNoteOn by rememberUpdatedState(onNoteOn)
    val currentOnNoteOff by rememberUpdatedState(onNoteOff)

    val effectiveKeyDp = PIANO_WHITE_KEY_DP * zoom.coerceIn(ZOOM_MIN, ZOOM_MAX)
    val totalWidthDp = effectiveKeyDp * whiteKeyCount

    // Build white/black key mappings for this panel's range. Works for any
    // firstMidi (not just A0), so layout panels can render arbitrary slices.
    val whiteMidis = remember(firstMidi, whiteKeyCount) {
        val out = IntArray(whiteKeyCount)
        var i = 0
        var m = firstMidi
        while (m < 128 && (m % 12) !in WHITE_KEY_PCS) m++
        while (i < whiteKeyCount && m < 128) {
            if ((m % 12) in WHITE_KEY_PCS) {
                out[i] = m
                i++
            }
            m++
        }
        // If firstMidi was high enough that we ran off the end, pad by repeating last.
        while (i < whiteKeyCount) { out[i] = out[(i - 1).coerceAtLeast(0)]; i++ }
        out
    }
    val blackKeys: List<BlackKeyDef> = remember(whiteMidis) {
        val list = mutableListOf<BlackKeyDef>()
        for (idx in 0 until whiteMidis.size - 1) {
            if (whiteMidis[idx + 1] - whiteMidis[idx] == 2) {
                list += BlackKeyDef(midi = whiteMidis[idx] + 1, leftWhiteIndex = idx)
            }
        }
        list
    }

    fun whiteKeyIndexFromX(x: Float, w: Float): Int {
        val keyW = w / whiteKeyCount
        return (x / keyW).toInt().coerceIn(0, whiteKeyCount - 1)
    }

    fun midiAt(x: Float, y: Float, w: Float, h: Float): Int {
        val keyW = w / whiteKeyCount
        if (y < h * 0.60f) {
            val bw = keyW * 0.7f
            for (bk in blackKeys) {
                val cx = (bk.leftWhiteIndex + 0.65f) * keyW
                if (x >= cx && x < cx + bw) return bk.midi
            }
        }
        return whiteMidis[whiteKeyIndexFromX(x, w)]
    }

    Box(
        modifier = modifier
            .horizontalScroll(scrollState),
    ) {
        Box(
            modifier = Modifier
                .width(totalWidthDp)
                .fillMaxHeight()
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    keyboardPlayLoop(
                        onDown = { id, pos ->
                            val midi = midiAt(pos.x, pos.y, size.width.toFloat(), size.height.toFloat())
                            pointerNotes[id]?.let { currentOnNoteOff(it) }
                            pointerNotes[id] = midi
                            currentOnNoteOn(midi)
                        },
                        onMove = { id, pos ->
                            val midi = midiAt(pos.x, pos.y, size.width.toFloat(), size.height.toFloat())
                            val prev = pointerNotes[id]
                            if (prev != midi) {
                                prev?.let { currentOnNoteOff(it) }
                                pointerNotes[id] = midi
                                currentOnNoteOn(midi)
                            }
                        },
                        onUp = { id ->
                            pointerNotes.remove(id)?.let { currentOnNoteOff(it) }
                        },
                    )
                },
        ) {
            Canvas(modifier = Modifier.fillMaxHeight().width(totalWidthDp)) {
                val w = this.size.width
                val h = this.size.height
                val keyW = w / whiteKeyCount

                // White keys.
                for (i in 0 until whiteKeyCount) {
                    val midi = whiteMidis[i]
                    val pressedColor = heldBySource[midi]?.let(::sourceColor)
                    val baseFill = pressedColor ?: KeyWhite
                    drawRect(color = baseFill, topLeft = Offset(i * keyW, 0f), size = Size(keyW, h))
                    drawRect(
                        color = KeyLabel.copy(alpha = 0.35f),
                        topLeft = Offset(i * keyW, 0f),
                        size = Size(keyW, h),
                        style = Stroke(width = 1f),
                    )
                    if ((midi % 12) == 0) {
                        val label = "C${(midi / 12) - 1}"
                        drawText(
                            textMeasurer = measurer,
                            text = AnnotatedString(label),
                            topLeft = Offset(i * keyW + 4f, h - with(density) { 18.sp.toPx() }),
                            style = TextStyle(color = KeyLabel, fontSize = 11.sp),
                        )
                    }
                }

                // Black keys.
                val blackH = h * 0.60f
                val blackW = keyW * 0.7f
                for (bk in blackKeys) {
                    val xPos = (bk.leftWhiteIndex + 0.65f) * keyW
                    val pressedColor = heldBySource[bk.midi]?.let(::sourceColor)
                    val fill = pressedColor ?: KeyBlack
                    drawRect(color = fill, topLeft = Offset(xPos, 0f), size = Size(blackW, blackH))
                }
            }
        }
    }

}

private fun sourceColor(s: NoteSource): Color = when (s) {
    NoteSource.TOUCH -> SourceTouch
    NoteSource.MIDI -> SourceMidi
    NoteSource.SCORE -> SourceScore
    NoteSource.HW_KEYBOARD -> SourceHwKey
}

/**
 * Play-only multi-touch loop: every active pointer maps to one note.
 * No mode switching, so two-, three-, four-finger play all sustain.
 */
private suspend fun PointerInputScope.keyboardPlayLoop(
    onDown: (Long, Offset) -> Unit,
    onMove: (Long, Offset) -> Unit,
    onUp: (Long) -> Unit,
) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            for (change in event.changes) {
                handleChange(change, change.id.value, onDown, onMove, onUp)
            }
        }
    }
}

private fun handleChange(
    change: PointerInputChange,
    id: Long,
    onDown: (Long, Offset) -> Unit,
    onMove: (Long, Offset) -> Unit,
    onUp: (Long) -> Unit,
) {
    if (change.changedToDown()) {
        onDown(id, change.position)
        change.consume()
    } else if (change.changedToUp()) {
        onUp(id)
        change.consume()
    } else if (change.pressed) {
        onMove(id, change.position)
    }
}

private fun PointerInputChange.changedToDown(): Boolean =
    this.pressed && !this.previousPressed

private fun PointerInputChange.changedToUp(): Boolean =
    !this.pressed && this.previousPressed
