package io.github.ranzlappen.synthpiano.ui.play

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
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
 * Multi-touch piano keyboard with pinch-zoom and horizontal scroll across
 * a [totalOctaves]-octave virtual keyboard.
 *
 * Gesture model:
 * - 1 active pointer  -> note play. Pointer position maps to a key; sliding
 *   a finger to a different key releases the previous note and triggers
 *   the new one. Multiple single-finger touches play multiple notes.
 * - 2+ active pointers -> transform mode. Any in-flight notes are released.
 *   Pinch changes [visibleKeys] (4..21); horizontal pan changes
 *   [firstWhiteKey] (0..totalWhiteKeys-visibleKeys). Returning to <=1
 *   pointer resumes play mode.
 *
 * @param firstMidiNote MIDI note of the leftmost (lowest) C of the rendered
 *   range. Default 48 = C3. The full range spans [firstMidiNote, firstMidiNote
 *   + totalOctaves * 12).
 * @param totalOctaves How many octaves of white keys exist. Default 5
 *   (= 35 white keys).
 * @param initialVisibleKeys Starting zoom level (white keys on screen).
 * @param initialFirstKey Starting scroll position (offset within the range).
 * @param heldNotes Notes currently sounding (driven by [SynthController]'s
 *   StateFlow).
 */
@Composable
fun PianoKeyboard(
    modifier: Modifier = Modifier,
    firstMidiNote: Int = 48,
    totalOctaves: Int = 5,
    initialVisibleKeys: Float = 14f,
    initialFirstKey: Float = 0f,
    heldNotes: Set<Int>,
    onNoteOn: (Int) -> Unit,
    onNoteOff: (Int) -> Unit,
) {
    val whiteKeySemitones = remember { listOf(0, 2, 4, 5, 7, 9, 11) }
    val blackKeysInOctave = remember {
        // (semitone offset from C, white-key offset center)
        listOf(
            0 to 0.65f,  // C#
            2 to 1.65f,  // D#
            5 to 3.65f,  // F#
            7 to 4.65f,  // G#
            9 to 5.65f,  // A#
        )
    }
    val totalWhiteKeys = totalOctaves * 7
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Per-pointer last note for play mode.
    val pointerNotes = remember { mutableMapOf<Long, Int>() }
    var size by remember { mutableStateOf(IntSize.Zero) }

    // Zoom + scroll state (clamped on every update).
    var visibleKeys by remember { mutableFloatStateOf(initialVisibleKeys.coerceIn(4f, 21f)) }
    var firstWhiteKey by remember {
        mutableFloatStateOf(
            initialFirstKey.coerceIn(0f, (totalWhiteKeys - visibleKeys).coerceAtLeast(0f))
        )
    }

    fun whiteIndexToMidi(i: Int): Int {
        val octave = i / 7
        val degree = i % 7
        return firstMidiNote + octave * 12 + whiteKeySemitones[degree]
    }

    fun midiAt(x: Float, y: Float, w: Float, h: Float): Int {
        val keyW = w / visibleKeys
        // Black-key strike zone is the top 60% of the keyboard.
        if (y < h * 0.60f) {
            for ((semi, off) in blackKeysInOctave) {
                for (oct in 0 until totalOctaves) {
                    val whiteIdxOfBlackBase = oct * 7 + off
                    if (whiteIdxOfBlackBase + 1 > totalWhiteKeys) continue
                    val cx = (whiteIdxOfBlackBase - firstWhiteKey) * keyW
                    val bw = keyW * 0.7f
                    if (x >= cx && x < cx + bw) {
                        return firstMidiNote + oct * 12 + semi + 1
                    }
                }
            }
        }
        // White key.
        val rawIdx = (x / keyW + firstWhiteKey).toInt()
            .coerceIn(0, totalWhiteKeys - 1)
        return whiteIndexToMidi(rawIdx)
    }

    Box(modifier = modifier
        .background(Color.Transparent)
        .onSizeChanged { size = it }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                var inTransform = false
                while (true) {
                    val event = awaitPointerEvent()
                    val activeCount = event.changes.count { it.pressed }

                    if (activeCount >= 2) {
                        // Transitioning into transform mode releases any
                        // notes the play mode was tracking.
                        if (!inTransform) {
                            pointerNotes.values.toList().forEach { onNoteOff(it) }
                            pointerNotes.clear()
                            inTransform = true
                        }
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        if (zoom != 1f) {
                            // calculateZoom > 1 => fingers spread apart => more keys per finger-span
                            // means we want FEWER keys visible (zoom in). Hence dividing.
                            val newVisible = (visibleKeys / zoom).coerceIn(4f, 21f)
                            visibleKeys = newVisible
                        }
                        if (pan.x != 0f && size.width > 0) {
                            val keyWPx = size.width / visibleKeys
                            firstWhiteKey = (firstWhiteKey - pan.x / keyWPx)
                                .coerceIn(0f, (totalWhiteKeys - visibleKeys).coerceAtLeast(0f))
                        }
                        event.changes.forEach { it.consume() }
                    } else {
                        if (inTransform && activeCount == 0) {
                            inTransform = false
                        }
                        if (!inTransform) {
                            for (change in event.changes) {
                                handlePlayChange(
                                    change = change,
                                    midiAt = { x, y -> midiAt(x, y, size.width.toFloat(), size.height.toFloat()) },
                                    pointerNotes = pointerNotes,
                                    onNoteOn = onNoteOn,
                                    onNoteOff = onNoteOff,
                                )
                            }
                        }
                    }
                }
            }
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            if (w <= 0f || h <= 0f) return@Canvas
            val keyW = w / visibleKeys

            // White keys: render only those at least partially visible to
            // save draw calls when zoomed-in or panned to one edge.
            val firstVisible = firstWhiteKey.toInt().coerceAtLeast(0)
            val lastVisible = (firstWhiteKey + visibleKeys).toInt()
                .coerceAtMost(totalWhiteKeys - 1) + 1
            for (i in firstVisible..lastVisible) {
                if (i >= totalWhiteKeys) break
                val midi = whiteIndexToMidi(i)
                val pressed = midi in heldNotes
                val color = if (pressed) KeyWhitePressed else KeyWhite
                val xLeft = (i - firstWhiteKey) * keyW
                drawRect(color = color, topLeft = Offset(xLeft, 0f), size = Size(keyW, h))
                drawRect(
                    color = KeyLabel.copy(alpha = 0.35f),
                    topLeft = Offset(xLeft, 0f),
                    size = Size(keyW, h),
                    style = Stroke(width = 1f),
                )
                if (i % 7 == 0) {
                    val label = "C${(midi / 12) - 1}"
                    drawText(
                        textMeasurer = measurer,
                        text = AnnotatedString(label),
                        topLeft = Offset(xLeft + 4f, h - with(density) { 16.sp.toPx() }),
                        style = TextStyle(color = KeyLabel, fontSize = 11.sp),
                    )
                }
            }

            // Black keys (drawn last so they overlay).
            val blackH = h * 0.60f
            val blackW = keyW * 0.7f
            for ((semi, off) in blackKeysInOctave) {
                for (oct in 0 until totalOctaves) {
                    val whiteIdxOfBlackBase = oct * 7 + off
                    if (whiteIdxOfBlackBase + 1 > totalWhiteKeys) continue
                    val xPos = (whiteIdxOfBlackBase - firstWhiteKey) * keyW
                    if (xPos + blackW < 0 || xPos > w) continue
                    val midi = firstMidiNote + oct * 12 + semi + 1
                    val pressed = midi in heldNotes
                    drawRect(
                        color = if (pressed) KeyBlackPressed else KeyBlack,
                        topLeft = Offset(xPos, 0f),
                        size = Size(blackW, blackH),
                    )
                }
            }
        }
    }
}

private fun handlePlayChange(
    change: PointerInputChange,
    midiAt: (Float, Float) -> Int,
    pointerNotes: MutableMap<Long, Int>,
    onNoteOn: (Int) -> Unit,
    onNoteOff: (Int) -> Unit,
) {
    val id = change.id.value
    when {
        change.pressed && !change.previousPressed -> {
            val midi = midiAt(change.position.x, change.position.y)
            pointerNotes[id]?.let { onNoteOff(it) }
            pointerNotes[id] = midi
            onNoteOn(midi)
            change.consume()
        }
        !change.pressed && change.previousPressed -> {
            pointerNotes.remove(id)?.let { onNoteOff(it) }
            change.consume()
        }
        change.pressed -> {
            val midi = midiAt(change.position.x, change.position.y)
            val prev = pointerNotes[id]
            if (prev != midi) {
                prev?.let { onNoteOff(it) }
                pointerNotes[id] = midi
                onNoteOn(midi)
            }
        }
    }
}
