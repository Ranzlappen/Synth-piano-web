package io.github.ranzlappen.synthpiano.ui.play

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import io.github.ranzlappen.synthpiano.ui.theme.KeyBlack
import io.github.ranzlappen.synthpiano.ui.theme.KeyBlackPressed
import io.github.ranzlappen.synthpiano.ui.theme.KeyLabel
import io.github.ranzlappen.synthpiano.ui.theme.KeyWhite
import io.github.ranzlappen.synthpiano.ui.theme.KeyWhitePressed

/**
 * Multi-touch piano keyboard. Each pointer is mapped to one MIDI note;
 * when the pointer slides off, the previous note is released and the new
 * one is triggered.
 *
 * @param firstMidiNote MIDI note of the leftmost white key (default C3 = 48).
 * @param whiteKeyCount Number of white keys to render (default 14 = 2 octaves).
 * @param heldNotes Notes currently held (for highlight).
 * @param onNoteOn / onNoteOff Callbacks invoked from the gesture handler.
 */
@Composable
fun PianoKeyboard(
    modifier: Modifier = Modifier,
    firstMidiNote: Int = 48,
    whiteKeyCount: Int = 14,
    heldNotes: Set<Int>,
    onNoteOn: (Int) -> Unit,
    onNoteOff: (Int) -> Unit,
) {
    val whiteKeySemitones = listOf(0, 2, 4, 5, 7, 9, 11)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Per-pointer last note, so we can release it when the pointer moves
    // to a different key or lifts.
    val pointerNotes = remember { mutableMapOf<Long, Int>() }
    var size by remember { mutableStateOf(IntSize.Zero) }

    fun whiteKeyIndexFromX(x: Float, w: Float): Int {
        val keyW = w / whiteKeyCount
        val idx = (x / keyW).toInt().coerceIn(0, whiteKeyCount - 1)
        return idx
    }

    fun whiteIndexToMidi(i: Int): Int {
        val octave = i / 7
        val degree = i % 7
        return firstMidiNote + octave * 12 + whiteKeySemitones[degree]
    }

    // Black key offsets in white-key units inside one octave.
    // Pattern starts from C: C# at 0.7, D# at 1.7, F# at 3.7, G# at 4.7, A# at 5.7
    val blackKeyOffsetsInOctave = listOf(
        0 to 0.65f,  // C# above C(0)
        2 to 1.65f,  // D# above D(1)
        5 to 3.65f,  // F# above F(3)
        7 to 4.65f,  // G# above G(4)
        9 to 5.65f,  // A# above A(5)
    )

    fun midiAt(x: Float, y: Float, w: Float, h: Float): Int {
        // Top 60% can hit black keys (where present); bottom 40% only white.
        val keyW = w / whiteKeyCount
        if (y < h * 0.60f) {
            for ((semi, off) in blackKeyOffsetsInOctave) {
                for (oct in 0..(whiteKeyCount / 7)) {
                    val cx = (oct * 7 + off) * keyW
                    val bw = keyW * 0.7f
                    if (x >= cx && x < cx + bw && (oct * 7 + off + 1) <= whiteKeyCount) {
                        return firstMidiNote + oct * 12 + semi + 1
                    }
                }
            }
        }
        return whiteIndexToMidi(whiteKeyIndexFromX(x, w))
    }

    Box(modifier = modifier
        .background(Color.Transparent)
        .onSizeChanged { size = it }
        .pointerInput(Unit) {
            keyboardGestureLoop(
                onDown = { id, pos ->
                    val midi = midiAt(pos.x, pos.y, size.width.toFloat(), size.height.toFloat())
                    pointerNotes[id]?.let { onNoteOff(it) }
                    pointerNotes[id] = midi
                    onNoteOn(midi)
                },
                onMove = { id, pos ->
                    val midi = midiAt(pos.x, pos.y, size.width.toFloat(), size.height.toFloat())
                    val prev = pointerNotes[id]
                    if (prev != midi) {
                        prev?.let { onNoteOff(it) }
                        pointerNotes[id] = midi
                        onNoteOn(midi)
                    }
                },
                onUp = { id ->
                    pointerNotes.remove(id)?.let { onNoteOff(it) }
                },
            )
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val keyW = w / whiteKeyCount

            // White keys
            for (i in 0 until whiteKeyCount) {
                val midi = whiteIndexToMidi(i)
                val pressed = midi in heldNotes
                val color = if (pressed) KeyWhitePressed else KeyWhite
                drawRect(color = color, topLeft = Offset(i * keyW, 0f), size = Size(keyW, h))
                drawRect(
                    color = KeyLabel.copy(alpha = 0.35f),
                    topLeft = Offset(i * keyW, 0f),
                    size = Size(keyW, h),
                    style = Stroke(width = 1f)
                )
                if (i % 7 == 0) {
                    val label = "C${(midi / 12) - 1}"
                    drawText(
                        textMeasurer = measurer,
                        text = AnnotatedString(label),
                        topLeft = Offset(i * keyW + 4f, h - with(density) { 16.sp.toPx() }),
                        style = TextStyle(color = KeyLabel, fontSize = 11.sp)
                    )
                }
            }

            // Black keys (drawn last so they overlay)
            val blackH = h * 0.60f
            val blackW = keyW * 0.7f
            for ((semi, off) in blackKeyOffsetsInOctave) {
                for (oct in 0..(whiteKeyCount / 7)) {
                    val xPos = (oct * 7 + off) * keyW
                    if ((oct * 7 + off + 1) > whiteKeyCount) continue
                    val midi = firstMidiNote + oct * 12 + semi + 1
                    val pressed = midi in heldNotes
                    drawRect(
                        color = if (pressed) KeyBlackPressed else KeyBlack,
                        topLeft = Offset(xPos, 0f),
                        size = Size(blackW, blackH)
                    )
                }
            }
        }
    }
}

private suspend fun PointerInputScope.keyboardGestureLoop(
    onDown: (Long, Offset) -> Unit,
    onMove: (Long, Offset) -> Unit,
    onUp: (Long) -> Unit,
) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            for (change in event.changes) {
                val id = change.id.value
                handleChange(change, id, onDown, onMove, onUp)
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
