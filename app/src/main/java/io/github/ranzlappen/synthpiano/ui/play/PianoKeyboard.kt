package io.github.ranzlappen.synthpiano.ui.play

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

/** Lowest white-key MIDI note rendered (A0). */
const val PIANO_FIRST_MIDI: Int = 21

/** Total white keys A0..C8 inclusive. */
const val PIANO_WHITE_KEY_COUNT: Int = 52

/** Width of one white key at 1.0× zoom, in dp. */
val PIANO_WHITE_KEY_DP: Dp = 56.dp

/** Pinch-zoom clamps. */
private const val ZOOM_MIN = 0.5f
private const val ZOOM_MAX = 2.0f

private val WHITE_INDEX_TO_MIDI: IntArray = IntArray(PIANO_WHITE_KEY_COUNT) { i ->
    when (i) {
        0 -> PIANO_FIRST_MIDI
        1 -> PIANO_FIRST_MIDI + 2
        else -> {
            val rel = i - 2
            val whiteSemitones = intArrayOf(0, 2, 4, 5, 7, 9, 11)
            (PIANO_FIRST_MIDI + 3) + (rel / 7) * 12 + whiteSemitones[rel % 7]
        }
    }
}

/**
 * MIDI note of the leftmost C currently visible in the scrollable keyboard.
 *
 * @param scrollPx Current scroll offset in pixels.
 * @param whiteKeyPx Width of one white key in pixels (density-and-zoom-adjusted).
 */
fun leftmostVisibleC(scrollPx: Int, whiteKeyPx: Float): Int {
    if (whiteKeyPx <= 0f) return 48
    val firstVisibleWhiteIdx =
        (scrollPx / whiteKeyPx).toInt().coerceIn(0, PIANO_WHITE_KEY_COUNT - 1)
    for (i in firstVisibleWhiteIdx until PIANO_WHITE_KEY_COUNT) {
        val midi = WHITE_INDEX_TO_MIDI[i]
        if (midi % 12 == 0) return midi
    }
    return WHITE_INDEX_TO_MIDI[firstVisibleWhiteIdx].let { it - (it % 12) }
}

/**
 * Pixel scroll offset that aligns the keyboard so the given C note is the
 * leftmost visible white key.
 */
fun scrollPxForC(midiC: Int, whiteKeyPx: Float): Int {
    if (whiteKeyPx <= 0f) return 0
    val idx = WHITE_INDEX_TO_MIDI.indexOfFirst { it == midiC }
    if (idx < 0) return 0
    return (idx * whiteKeyPx).toInt()
}

/**
 * Multi-touch piano keyboard with per-source key coloring and pinch zoom.
 * Renders a fixed-pitch, full-piano-range Canvas wrapped in a horizontal
 * scroll container so the user can pan from A0 to C8.
 *
 * Each pointer is mapped to one MIDI note; sliding between keys releases
 * the previous note and triggers the new one. Pressed keys color according
 * to which input device fired them.
 *
 * Two-finger pinch directly on the keyboard re-scales white-key width
 * within [ZOOM_MIN]..[ZOOM_MAX]. The key under the pinch centroid stays
 * anchored under the user's fingers. Note-on tracking is suspended while
 * pinching; in-flight notes are released immediately when the second
 * finger lands.
 *
 * @param scrollState hoisted scroll state so the parent can derive the
 * leftmost visible C and persist scroll position.
 * @param heldBySource Map of pressed MIDI note → source for coloring.
 * @param zoom Current persisted zoom factor; serves as the initial value.
 * @param onZoomChange Invoked once at the end of each pinch gesture with
 * the new clamped zoom factor for persistence.
 */
@Composable
fun PianoKeyboard(
    modifier: Modifier = Modifier,
    scrollState: ScrollState,
    heldBySource: Map<Int, NoteSource>,
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    onNoteOn: (Int) -> Unit,
    onNoteOff: (Int) -> Unit,
) {
    val whiteKeySemitones = listOf(0, 2, 4, 5, 7, 9, 11)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val pointerNotes = remember { mutableMapOf<Long, Int>() }
    var size by remember { mutableStateOf(IntSize.Zero) }

    // Live zoom is seeded once and then driven by gestures. External
    // (DataStore) zoom changes are synced in via LaunchedEffect so the
    // state instance stays stable across recompositions — important
    // because the pointerInput closure captures it.
    var liveZoom by remember { mutableFloatStateOf(zoom.coerceIn(ZOOM_MIN, ZOOM_MAX)) }
    LaunchedEffect(zoom) {
        val target = zoom.coerceIn(ZOOM_MIN, ZOOM_MAX)
        if (target != liveZoom) liveZoom = target
    }

    // Forward latest note callbacks via rememberUpdatedState so the
    // long-lived pointerInput block always invokes the freshest closure
    // (which reads current sticky/held modifier sets in PerformTab).
    val currentOnNoteOn by rememberUpdatedState(onNoteOn)
    val currentOnNoteOff by rememberUpdatedState(onNoteOff)
    val currentOnZoomChange by rememberUpdatedState(onZoomChange)

    val firstMidiNote = PIANO_FIRST_MIDI
    val whiteKeyCount = PIANO_WHITE_KEY_COUNT

    val effectiveKeyDp = PIANO_WHITE_KEY_DP * liveZoom
    val totalWidthDp = effectiveKeyDp * whiteKeyCount

    fun whiteKeyIndexFromX(x: Float, w: Float): Int {
        val keyW = w / whiteKeyCount
        val idx = (x / keyW).toInt().coerceIn(0, whiteKeyCount - 1)
        return idx
    }

    fun whiteIndexToMidi(i: Int): Int = when (i) {
        0 -> firstMidiNote          // A0
        1 -> firstMidiNote + 2      // B0
        else -> {
            val rel = i - 2          // 0-based from C1
            val octave = rel / 7
            val degree = rel % 7
            (firstMidiNote + 3) + octave * 12 + whiteKeySemitones[degree]
        }
    }

    val blackKeyOffsetsInOctave = listOf(
        0 to 0.65f,  // C# at C+0.65
        2 to 1.65f,  // D# at D+0.65
        5 to 3.65f,  // F# at F+0.65
        7 to 4.65f,  // G# at G+0.65
        9 to 5.65f,  // A# at A+0.65
    )

    fun midiAt(x: Float, y: Float, w: Float, h: Float): Int {
        val keyW = w / whiteKeyCount
        if (y < h * 0.60f) {
            val aSharp0X = 0.65f * keyW
            val bw = keyW * 0.7f
            if (x >= aSharp0X && x < aSharp0X + bw) return firstMidiNote + 1

            val octavesOfC = (whiteKeyCount - 2 + 6) / 7
            for ((semi, off) in blackKeyOffsetsInOctave) {
                for (oct in 0 until octavesOfC) {
                    val cWhiteIndex = 2 + oct * 7
                    val cx = (cWhiteIndex + off) * keyW
                    if (x >= cx && x < cx + bw && (cWhiteIndex + off + 1) <= whiteKeyCount) {
                        return (firstMidiNote + 3) + oct * 12 + semi + 1
                    }
                }
            }
        }
        return whiteIndexToMidi(whiteKeyIndexFromX(x, w))
    }

    // Centroid-anchored zoom: keep the key under the pinch centroid stable
    // by adjusting scroll in proportion with the zoom step.
    fun applyPinchDelta(centroidX: Float, step: Float) {
        val newZoom = (liveZoom * step).coerceIn(ZOOM_MIN, ZOOM_MAX)
        if (newZoom == liveZoom) return
        val ratio = newZoom / liveZoom
        val newScroll = ((scrollState.value + centroidX) * ratio - centroidX)
            .toInt()
            .coerceAtLeast(0)
        liveZoom = newZoom
        coroutineScope.launch { scrollState.scrollTo(newScroll) }
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
                    keyboardGestureLoop(
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
                        pointerNotes = pointerNotes,
                        onPinchDelta = { centroid, step -> applyPinchDelta(centroid.x, step) },
                        onPinchEnd = { currentOnZoomChange(liveZoom) },
                    )
                },
        ) {
            Canvas(modifier = Modifier.fillMaxHeight().width(totalWidthDp)) {
                val w = this.size.width
                val h = this.size.height
                val keyW = w / whiteKeyCount

                // White keys.
                for (i in 0 until whiteKeyCount) {
                    val midi = whiteIndexToMidi(i)
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

                run {
                    val aSharp = firstMidiNote + 1
                    val xPos = 0.65f * keyW
                    val pressedColor = heldBySource[aSharp]?.let(::sourceColor)
                    val fill = pressedColor ?: KeyBlack
                    drawRect(color = fill, topLeft = Offset(xPos, 0f), size = Size(blackW, blackH))
                }

                val octavesOfC = (whiteKeyCount - 2 + 6) / 7
                for ((semi, off) in blackKeyOffsetsInOctave) {
                    for (oct in 0 until octavesOfC) {
                        val cWhiteIndex = 2 + oct * 7
                        if ((cWhiteIndex + off + 1) > whiteKeyCount) continue
                        val xPos = (cWhiteIndex + off) * keyW
                        val midi = (firstMidiNote + 3) + oct * 12 + semi + 1
                        if (midi > 108) continue
                        val pressedColor = heldBySource[midi]?.let(::sourceColor)
                        val fill = pressedColor ?: KeyBlack
                        drawRect(
                            color = fill,
                            topLeft = Offset(xPos, 0f),
                            size = Size(blackW, blackH),
                        )
                    }
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

private enum class GestureMode { Play, Zoom }

/**
 * Multi-touch arbitration loop. Single pointer = play (handled per-change
 * by [handleChange]); two or more = zoom (centroid distance drives
 * [onPinchDelta]). Mode transitions release any in-flight notes so users
 * never get stuck notes on pinch start.
 */
private suspend fun PointerInputScope.keyboardGestureLoop(
    onDown: (Long, Offset) -> Unit,
    onMove: (Long, Offset) -> Unit,
    onUp: (Long) -> Unit,
    pointerNotes: MutableMap<Long, Int>,
    onPinchDelta: (centroid: Offset, step: Float) -> Unit,
    onPinchEnd: () -> Unit,
) {
    awaitPointerEventScope {
        var mode = GestureMode.Play
        var lastDist = 0f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val active = event.changes.filter { it.pressed }

            if (mode == GestureMode.Play && active.size >= 2) {
                // Second finger landed: cancel ALL in-flight notes (walk
                // pointerNotes — event.changes only contains current frame's
                // pointers).
                pointerNotes.keys.toList().forEach { id -> onUp(id) }
                pointerNotes.clear()
                lastDist = (active[0].position - active[1].position).getDistance()
                mode = GestureMode.Zoom
                event.changes.forEach { it.consume() }
                continue
            }
            if (mode == GestureMode.Zoom && active.size < 2) {
                onPinchEnd()
                mode = GestureMode.Play
                event.changes.forEach { it.consume() }
                continue
            }

            when (mode) {
                GestureMode.Play -> for (change in event.changes) {
                    handleChange(change, change.id.value, onDown, onMove, onUp)
                }
                GestureMode.Zoom -> {
                    val a = active[0].position
                    val b = active[1].position
                    val d = (a - b).getDistance()
                    if (lastDist > 0f && d > 0f) {
                        val centroid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                        onPinchDelta(centroid, d / lastDist)
                    }
                    lastDist = d
                    event.changes.forEach { it.consume() }
                }
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
