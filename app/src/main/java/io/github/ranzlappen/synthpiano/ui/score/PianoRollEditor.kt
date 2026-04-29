package io.github.ranzlappen.synthpiano.ui.score

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.data.midi.MidiScore
import io.github.ranzlappen.synthpiano.data.midi.Note
import kotlin.math.abs
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
 *
 * Zoom: the caller owns [zoomX] / [zoomY]. The grid + drawing + gesture
 * math all run against scaled metrics (`pxPerBeat * zoomX`, etc.) so taps,
 * long-presses, and drags continue to work at any zoom level. A two-finger
 * pinch updates both axes via [onZoomChange]; single-finger gestures
 * pass through to the existing tap / long-press / drag detectors.
 */

private const val DEFAULT_MIN_PITCH = 24   // C1
private const val DEFAULT_MAX_PITCH = 108  // C8

const val PIANO_ROLL_ZOOM_MIN_X = 0.25f
const val PIANO_ROLL_ZOOM_MAX_X = 8f
const val PIANO_ROLL_ZOOM_MIN_Y = 0.5f
const val PIANO_ROLL_ZOOM_MAX_Y = 4f

/**
 * Axis the active pinch is locked to, decided once at slop crossing from
 * the finger pair's geometry. Locking the axis keeps natural off-axis
 * finger jitter from leaking into the other zoom factor — a horizontal
 * pinch only ever drives [zoomX], a vertical pinch only ever [zoomY].
 */
private enum class PinchAxis { Horizontal, Vertical, Both }

/**
 * How much more "spread out" the finger pair has to be along one axis
 * than the other before we lock the pinch to that axis. With 1.5, a
 * pinch within ±~34° of horizontal or vertical locks; pinches in the
 * diagonal band [≈34°, ≈56°] drive both axes.
 */
private const val PINCH_AXIS_LOCK_RATIO = 1.5f

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
    selectedIndex: Int? = null,
    onSelectNote: (Int?) -> Unit = {},
    onAddNote: (midi: Int, startTicks: Int) -> Unit = { _, _ -> },
    onUpdateNote: (index: Int, newNote: Note) -> Unit = { _, _ -> },
    onDeleteNote: (index: Int) -> Unit = {},
    pxPerBeat: Dp = 80.dp,
    pxPerSemitone: Dp = 12.dp,
    keyboardWidth: Dp = 56.dp,
    minPitch: Int = DEFAULT_MIN_PITCH,
    maxPitch: Int = DEFAULT_MAX_PITCH,
    zoomX: Float = 1f,
    zoomY: Float = 1f,
    onZoomChange: (zoomX: Float, zoomY: Float) -> Unit = { _, _ -> },
) {
    val density = LocalDensity.current
    val pxPerBeatBase = with(density) { pxPerBeat.toPx() }
    val pxPerSemitoneBase = with(density) { pxPerSemitone.toPx() }

    val pxPerBeatF = pxPerBeatBase * zoomX
    val pxPerSemitoneF = pxPerSemitoneBase * zoomY

    val pitchRange = (maxPitch - minPitch + 1).coerceAtLeast(1)
    val totalBeats = beatsTotal(score)
    val totalWidthPx = max(pxPerBeatF * totalBeats, pxPerBeatF * 8f)  // min 8 beats wide
    val totalHeightPx = pitchRange * pxPerSemitoneF

    val totalWidthDp = with(density) { totalWidthPx.toDp() }
    val totalHeightDp = with(density) { totalHeightPx.toDp() }

    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    // Latest-value snapshots for the pinch handler so changing zoom doesn't
    // re-key the gesture coroutine and cancel an in-flight pinch.
    val zoomXSnap = rememberUpdatedState(zoomX)
    val zoomYSnap = rememberUpdatedState(zoomY)
    val onZoomSnap = rememberUpdatedState(onZoomChange)

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
            // Drag state shared between the drag detector and the renderer.
            // Use rememberUpdatedState so gesture closures always see the latest
            // score without restarting (re-keying) the gesture coroutine —
            // otherwise updating a note mid-drag would cancel the drag.
            val dragState = remember { mutableStateOf<DragState?>(null) }
            val scoreSnap = rememberUpdatedState(score)
            val onSelectSnap = rememberUpdatedState(onSelectNote)
            val onAddSnap = rememberUpdatedState(onAddNote)
            val onUpdateSnap = rememberUpdatedState(onUpdateNote)
            val onDeleteSnap = rememberUpdatedState(onDeleteNote)
            val resizeEdgePx = with(density) { 18.dp.toPx() }

            val pinchSlopPx = with(density) { 8.dp.toPx() }
            Canvas(
                modifier = Modifier
                    .size(totalWidthDp, totalHeightDp)
                    // 2-finger pinch + pan. Runs on the Initial pass so it
                    // gets first dibs to claim multi-touch gestures before
                    // tap / drag detectors at Main pass. Only consumes
                    // events when pinching is actually active, so single-
                    // finger gestures pass through untouched.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            var active = false
                            // Cumulative-from-start (not incremental) ratios
                            // keep floating-point drift bounded across the
                            // gesture; re-baseline whenever the pointer pair
                            // changes (a finger lifts, a different finger
                            // joins) so the next event isn't computed against
                            // a stale start span.
                            var pinchIdA = -1L
                            var pinchIdB = -1L
                            var startSpanX = 0f
                            var startSpanY = 0f
                            var startZoomX = 0f
                            var startZoomY = 0f
                            // Decided at re-baseline from the finger pair
                            // geometry; only the matching axis's zoom
                            // updates while the gesture is active.
                            var axis = PinchAxis.Both
                            // Sub-pixel accumulators for scroll deltas.
                            // ScrollState rounds its value to Int on every
                            // dispatch, so per-event focal-point and pan
                            // deltas smaller than 1 px would round to zero
                            // and the focal point would drift toward y=0
                            // (i.e., the canvas's natural top anchor) over
                            // the course of a slow zoom. Accumulate the
                            // float deltas here and dispatch only the
                            // integer part; carry the fractional + clamped
                            // remainder to the next event.
                            var pendingHScroll = 0f
                            var pendingVScroll = 0f
                            // Captured in canvas pixels at the previous
                            // event's canvas frame; rescaled by the zoom
                            // ratio before computing the pan delta so a
                            // stationary finger pair pans by zero.
                            var lastCentroid = Offset.Zero

                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                // Sort by pointer id so the [0]/[1] indices
                                // don't swap mid-gesture (which would jitter
                                // the span calculation).
                                val pressed = event.changes.filter { it.pressed }
                                    .sortedBy { it.id.value }
                                if (pressed.size < 2) {
                                    if (active) active = false
                                    pinchIdA = -1L
                                    pinchIdB = -1L
                                    if (pressed.isEmpty()) break
                                    continue
                                }

                                val a = pressed[0].position
                                val b = pressed[1].position
                                val spanX = abs(a.x - b.x)
                                val spanY = abs(a.y - b.y)
                                val centroid = Offset((a.x + b.x) * 0.5f, (a.y + b.y) * 0.5f)

                                val idA = pressed[0].id.value
                                val idB = pressed[1].id.value
                                if (idA != pinchIdA || idB != pinchIdB) {
                                    // Pointer set changed (or first frame) —
                                    // baseline against current values and
                                    // require a fresh slop crossing before
                                    // we start consuming.
                                    pinchIdA = idA
                                    pinchIdB = idB
                                    startSpanX = spanX
                                    startSpanY = spanY
                                    startZoomX = zoomXSnap.value
                                    startZoomY = zoomYSnap.value
                                    lastCentroid = centroid
                                    pendingHScroll = 0f
                                    pendingVScroll = 0f
                                    active = false
                                    // Lock to whichever axis the finger pair
                                    // is more spread along; diagonal pairs
                                    // (within the ratio band on both sides)
                                    // drive both axes.
                                    val dx = abs(b.x - a.x)
                                    val dy = abs(b.y - a.y)
                                    axis = when {
                                        dx > dy * PINCH_AXIS_LOCK_RATIO -> PinchAxis.Horizontal
                                        dy > dx * PINCH_AXIS_LOCK_RATIO -> PinchAxis.Vertical
                                        else -> PinchAxis.Both
                                    }
                                }

                                if (!active) {
                                    val movedSpan = max(
                                        abs(spanX - startSpanX),
                                        abs(spanY - startSpanY),
                                    )
                                    val movedCentroid = (centroid - lastCentroid).getDistance()
                                    if (movedSpan > pinchSlopPx || movedCentroid > pinchSlopPx) {
                                        active = true
                                    }
                                }

                                if (active) {
                                    val oldZoomX = zoomXSnap.value
                                    val oldZoomY = zoomYSnap.value

                                    // Zoom = startZoom × (currentSpan / startSpan),
                                    // gated by the locked axis so an
                                    // X-only pinch can never drift Y (and
                                    // vice versa).
                                    var nextX = oldZoomX
                                    var nextY = oldZoomY
                                    if (axis != PinchAxis.Vertical && startSpanX > pinchSlopPx) {
                                        nextX = (startZoomX * (spanX / startSpanX))
                                            .coerceIn(PIANO_ROLL_ZOOM_MIN_X, PIANO_ROLL_ZOOM_MAX_X)
                                    }
                                    if (axis != PinchAxis.Horizontal && startSpanY > pinchSlopPx) {
                                        nextY = (startZoomY * (spanY / startSpanY))
                                            .coerceIn(PIANO_ROLL_ZOOM_MIN_Y, PIANO_ROLL_ZOOM_MAX_Y)
                                    }

                                    val ratioX = if (oldZoomX > 0f) nextX / oldZoomX else 1f
                                    val ratioY = if (oldZoomY > 0f) nextY / oldZoomY else 1f

                                    if (nextX != oldZoomX || nextY != oldZoomY) {
                                        onZoomSnap.value(nextX, nextY)
                                    }

                                    // Focal-point preservation: keep the
                                    // canvas point under the centroid at
                                    // the same screen coordinate after
                                    // zoom. delta = focal × (ratio − 1).
                                    // 2-finger pan: scroll opposite to the
                                    // centroid's screen-space movement.
                                    // Both deltas accumulate into the
                                    // pending* floats; the integer part is
                                    // dispatched and the residual carries
                                    // over so a slow zoom doesn't lose the
                                    // sub-pixel scroll on every event.
                                    val lastInNewFrame = Offset(
                                        lastCentroid.x * ratioX,
                                        lastCentroid.y * ratioY,
                                    )
                                    val pan = centroid - lastInNewFrame
                                    pendingHScroll += centroid.x * (ratioX - 1f) - pan.x
                                    pendingVScroll += centroid.y * (ratioY - 1f) - pan.y

                                    val wholeH = pendingHScroll.toInt()
                                    if (wholeH != 0) {
                                        val consumed = hScroll.dispatchRawDelta(wholeH.toFloat())
                                        pendingHScroll -= consumed
                                    }
                                    val wholeV = pendingVScroll.toInt()
                                    if (wholeV != 0) {
                                        val consumed = vScroll.dispatchRawDelta(wholeV.toFloat())
                                        pendingVScroll -= consumed
                                    }

                                    // Cancel any in-flight single-finger drag
                                    // so a note doesn't keep tracking finger
                                    // 0 while the user is pinching.
                                    if (dragState.value != null) dragState.value = null

                                    event.changes.forEach { it.consume() }
                                }

                                lastCentroid = centroid
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                val s = scoreSnap.value
                                val hit = hitTestNote(
                                    offset = offset,
                                    notes = s.notes,
                                    ppq = s.ppq,
                                    pxPerBeat = pxPerBeatF,
                                    pxPerSemitone = pxPerSemitoneF,
                                    maxPitch = maxPitch,
                                )
                                if (hit != null) {
                                    onSelectSnap.value(hit)
                                } else {
                                    val (midi, tick) = pixelToMusic(
                                        offset = offset,
                                        ppq = s.ppq,
                                        pxPerBeat = pxPerBeatF,
                                        pxPerSemitone = pxPerSemitoneF,
                                        maxPitch = maxPitch,
                                        minPitch = minPitch,
                                    )
                                    val snap = (s.ppq / 16).coerceAtLeast(1)
                                    if (midi in minPitch..maxPitch) {
                                        val snappedTick = (tick / snap) * snap
                                        onAddSnap.value(midi, snappedTick.coerceAtLeast(0))
                                    }
                                    onSelectSnap.value(null)
                                }
                            },
                            onLongPress = { offset ->
                                val s = scoreSnap.value
                                val hit = hitTestNote(
                                    offset = offset,
                                    notes = s.notes,
                                    ppq = s.ppq,
                                    pxPerBeat = pxPerBeatF,
                                    pxPerSemitone = pxPerSemitoneF,
                                    maxPitch = maxPitch,
                                )
                                if (hit != null) {
                                    onDeleteSnap.value(hit)
                                    onSelectSnap.value(null)
                                }
                            },
                        )
                    }
                    // Single-finger drag on a NOTE moves / resizes it. Drags
                    // started on empty grid are deliberately NOT claimed —
                    // the pointer event then bubbles up to the parent
                    // horizontal/vertical scroll, which is what the user
                    // expects when swiping the empty canvas.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = true)
                            val s = scoreSnap.value
                            val hit = hitTestNote(
                                offset = down.position,
                                notes = s.notes,
                                ppq = s.ppq,
                                pxPerBeat = pxPerBeatF,
                                pxPerSemitone = pxPerSemitoneF,
                                maxPitch = maxPitch,
                            ) ?: return@awaitEachGesture

                            val n = s.notes[hit]
                            val noteX = n.startTicks.toFloat() / s.ppq * pxPerBeatF
                            val noteW = (n.durationTicks.toFloat() / s.ppq * pxPerBeatF).coerceAtLeast(2f)
                            val onRightEdge = down.position.x >= noteX + noteW - resizeEdgePx
                            val mode = if (onRightEdge) DragMode.Resize else DragMode.Move

                            // Wait for touch slop. If user releases or
                            // gesture is cancelled by the pinch handler
                            // before slop, bail without claiming.
                            val slop = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                                change.consume()
                            } ?: return@awaitEachGesture

                            dragState.value = DragState(index = hit, mode = mode, original = n)
                            onSelectSnap.value(hit)

                            // `drag()` returns true when the pointer is
                            // released and false when the gesture is cancelled
                            // (e.g. the pinch handler consumed the event).
                            // Either way we clear the drag state when it
                            // finishes, so we ignore the return value.
                            drag(slop.id) { change ->
                                val state = dragState.value ?: return@drag
                                val curScore = scoreSnap.value
                                val original = state.original
                                val snapTicks = (curScore.ppq / 16).coerceAtLeast(1)
                                when (state.mode) {
                                    DragMode.Move -> {
                                        val (newMidi, newTick) = pixelToMusic(
                                            offset = change.position,
                                            ppq = curScore.ppq,
                                            pxPerBeat = pxPerBeatF,
                                            pxPerSemitone = pxPerSemitoneF,
                                            maxPitch = maxPitch,
                                            minPitch = minPitch,
                                        )
                                        val snapped = (newTick / snapTicks) * snapTicks
                                        onUpdateSnap.value(
                                            state.index,
                                            original.copy(
                                                midi = newMidi.coerceIn(minPitch, maxPitch),
                                                startTicks = snapped.coerceAtLeast(0),
                                            ),
                                        )
                                    }
                                    DragMode.Resize -> {
                                        val newEndTick = ((change.position.x / pxPerBeatF) * curScore.ppq).toInt()
                                        val rawDur = newEndTick - original.startTicks
                                        val snappedDur = ((rawDur + snapTicks / 2) / snapTicks) * snapTicks
                                        onUpdateSnap.value(
                                            state.index,
                                            original.copy(durationTicks = snappedDur.coerceAtLeast(snapTicks)),
                                        )
                                    }
                                }
                                change.consume()
                            }
                            dragState.value = null
                        }
                    },
            ) {
                drawGrid(
                    minPitch = minPitch,
                    maxPitch = maxPitch,
                    pxPerSemitone = pxPerSemitoneF,
                    pxPerBeat = pxPerBeatF,
                    totalBeats = totalBeats.coerceAtLeast(8f),
                    canvasSize = size,
                )
                for ((idx, n) in score.notes.withIndex()) {
                    drawNote(
                        note = n,
                        ppq = score.ppq,
                        pxPerBeat = pxPerBeatF,
                        pxPerSemitone = pxPerSemitoneF,
                        minPitch = minPitch,
                        maxPitch = maxPitch,
                        selected = (idx == selectedIndex),
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

private enum class DragMode { Move, Resize }

private data class DragState(
    val index: Int,
    val mode: DragMode,
    val original: Note,
)

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
    selected: Boolean = false,
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
        color = if (selected) Color(0xFFFFEB3B) else Color(0x88000000),
        topLeft = Offset(x, y),
        size = Size(w, h),
        style = Stroke(width = if (selected) 2.5f else 0.8f),
    )
}

/**
 * Convert a pointer offset (in canvas pixels) to (midi, tick) coordinates.
 * Returns the closest pitch / tick at the tap location.
 */
private fun pixelToMusic(
    offset: Offset,
    ppq: Int,
    pxPerBeat: Float,
    pxPerSemitone: Float,
    maxPitch: Int,
    minPitch: Int,
): Pair<Int, Int> {
    val midi = (maxPitch - (offset.y / pxPerSemitone).toInt()).coerceIn(minPitch, maxPitch)
    val tick = ((offset.x / pxPerBeat) * ppq).toInt().coerceAtLeast(0)
    return midi to tick
}

/**
 * Returns the index of the topmost note containing [offset], or null. Iterates
 * from the end of the list so notes drawn last (visually on top) win when
 * overlapping.
 */
private fun hitTestNote(
    offset: Offset,
    notes: List<Note>,
    ppq: Int,
    pxPerBeat: Float,
    pxPerSemitone: Float,
    maxPitch: Int,
): Int? {
    for (i in notes.indices.reversed()) {
        val n = notes[i]
        val x = n.startTicks.toFloat() / ppq * pxPerBeat
        val y = (maxPitch - n.midi) * pxPerSemitone
        val w = (n.durationTicks.toFloat() / ppq * pxPerBeat).coerceAtLeast(2f)
        val h = pxPerSemitone
        if (offset.x in x..(x + w) && offset.y in y..(y + h)) return i
    }
    return null
}
