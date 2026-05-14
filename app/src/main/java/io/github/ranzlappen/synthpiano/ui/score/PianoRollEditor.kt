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
import io.github.ranzlappen.synthpiano.data.midi.ControlEvent
import io.github.ranzlappen.synthpiano.data.midi.ControlKind
import io.github.ranzlappen.synthpiano.data.midi.MidiScore
import io.github.ranzlappen.synthpiano.data.midi.Note
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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

private val VELOCITY_LANE_HEIGHT = 64.dp
private val SUSTAIN_LANE_HEIGHT = 28.dp
private val LANE_EDGE_GRAB = 14.dp
private val VELOCITY_BAR_WIDTH = 8.dp
// A tapped velocity bar will only edit a note if the note's start is within
// this many beats of the tap. Without a cap, an empty stretch of lane would
// silently retarget a faraway note.
private const val VELOCITY_HIT_BEATS = 0.5f

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

/**
 * Minimum relative zoom change accepted per pinch event. Per-frame finger
 * jitter on a stable pinch produces ~0.1–0.3% span oscillation; below this
 * threshold we keep the previous zoom so the canvas does not flicker. The
 * dead-band only filters per-event noise — `startSpanX` / `startZoomX` are
 * not rebased, so accumulating motion still drives the zoom on the next
 * event when the user actually moves further.
 */
private const val PINCH_ZOOM_DEADBAND = 0.002f

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
    /**
     * Replace the entire control-event list (sustain pedal + expression +
     * channel aftertouch). The piano-roll only edits SUSTAIN entries in
     * v1 but the callback hands back the full list so the parent doesn't
     * have to merge two sources.
     */
    onControlEventsChange: (List<ControlEvent>) -> Unit = {},
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
    val noteAreaHeightPx = pitchRange * pxPerSemitoneF
    val velocityLaneHeightPx = with(density) { VELOCITY_LANE_HEIGHT.toPx() }
    val sustainLaneHeightPx = with(density) { SUSTAIN_LANE_HEIGHT.toPx() }
    val laneEdgeGrabPx = with(density) { LANE_EDGE_GRAB.toPx() }
    val velocityBarWidthPx = with(density) { VELOCITY_BAR_WIDTH.toPx() }
    // The lanes sit below the note grid inside the same scrollable canvas
    // so they share the horizontal scroll position with the notes.
    val velocityLaneTopPx = noteAreaHeightPx
    val sustainLaneTopPx = velocityLaneTopPx + velocityLaneHeightPx
    val totalHeightPx = sustainLaneTopPx + sustainLaneHeightPx

    val totalWidthDp = with(density) { totalWidthPx.toDp() }
    val totalHeightDp = with(density) { totalHeightPx.toDp() }
    val noteAreaHeightDp = with(density) { noteAreaHeightPx.toDp() }

    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    // Latest-value snapshots for the gesture handlers. The pinch / tap /
    // drag pointerInput modifiers are keyed on Unit so the suspending
    // coroutine is never restarted; without these snapshots their lambdas
    // would close over the first composition's zoom (= 1f) and tap/drag
    // hit-tests would reference the un-zoomed grid forever.
    val zoomXSnap = rememberUpdatedState(zoomX)
    val zoomYSnap = rememberUpdatedState(zoomY)
    val onZoomSnap = rememberUpdatedState(onZoomChange)
    val pxPerBeatSnap = rememberUpdatedState(pxPerBeatF)
    val pxPerSemitoneSnap = rememberUpdatedState(pxPerSemitoneF)

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
                    height = noteAreaHeightPx,
                )
                drawKeyboardLaneLabels(
                    width = size.width,
                    velocityLaneTop = velocityLaneTopPx,
                    velocityLaneHeight = velocityLaneHeightPx,
                    sustainLaneTop = sustainLaneTopPx,
                    sustainLaneHeight = sustainLaneHeightPx,
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
            val onControlEventsSnap = rememberUpdatedState(onControlEventsChange)
            val resizeEdgePx = with(density) { 18.dp.toPx() }
            val velocityLaneTopSnap = rememberUpdatedState(velocityLaneTopPx)
            val velocityLaneHeightSnap = rememberUpdatedState(velocityLaneHeightPx)
            val sustainLaneTopSnap = rememberUpdatedState(sustainLaneTopPx)
            val sustainLaneHeightSnap = rememberUpdatedState(sustainLaneHeightPx)
            val laneEdgeGrabSnap = rememberUpdatedState(laneEdgeGrabPx)

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
                            // dispatch, so per-event sub-px scroll motion
                            // would round to zero and the focal point would
                            // drift toward the canvas's top-left anchor over
                            // the course of a slow zoom. Accumulate the
                            // float deltas here and dispatch only the
                            // integer part; carry the fractional + clamped
                            // remainder to the next event.
                            var pendingHScroll = 0f
                            var pendingVScroll = 0f
                            // Content-space (un-zoomed) anchor under the
                            // centroid at re-baseline. Holding this fixed
                            // and recomputing the absolute scroll target
                            // each event self-heals the 1-frame lag between
                            // committing zoom and the canvas resizing —
                            // delta accumulation would otherwise compound
                            // any clamp into permanent focal-point drift.
                            var focalContentX = 0f
                            var focalContentY = 0f
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
                                    focalContentX = if (startZoomX > 0f)
                                        (centroid.x + hScroll.value) / startZoomX else 0f
                                    focalContentY = if (startZoomY > 0f)
                                        (centroid.y + vScroll.value) / startZoomY else 0f
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

                                    // Per-event dead-band: filter sub-threshold
                                    // span jitter so a stable pinch doesn't
                                    // flicker. startSpan/startZoom aren't
                                    // rebased, so accumulating motion still
                                    // drives zoom on a later event.
                                    if (oldZoomX > 0f &&
                                        abs(nextX - oldZoomX) / oldZoomX < PINCH_ZOOM_DEADBAND
                                    ) nextX = oldZoomX
                                    if (oldZoomY > 0f &&
                                        abs(nextY - oldZoomY) / oldZoomY < PINCH_ZOOM_DEADBAND
                                    ) nextY = oldZoomY

                                    if (nextX != oldZoomX || nextY != oldZoomY) {
                                        onZoomSnap.value(nextX, nextY)
                                    }

                                    // Absolute scroll target: keep the content
                                    // point captured under the centroid at
                                    // pinch-start anchored to the live
                                    // centroid. centroid drift folds in via
                                    // the −centroid term, so this also
                                    // handles 2-finger pan along the locked
                                    // axis. Recomputed against the current
                                    // hScroll.value each event so a one-frame
                                    // canvas-resize lag self-corrects on the
                                    // next event instead of compounding.
                                    if (axis != PinchAxis.Vertical) {
                                        val targetH = focalContentX * nextX - centroid.x
                                        pendingHScroll += targetH - hScroll.value
                                        val wholeH = pendingHScroll.toInt()
                                        if (wholeH != 0) {
                                            val consumed = hScroll.dispatchRawDelta(wholeH.toFloat())
                                            pendingHScroll -= consumed
                                        }
                                    }
                                    if (axis != PinchAxis.Horizontal) {
                                        val targetV = focalContentY * nextY - centroid.y
                                        pendingVScroll += targetV - vScroll.value
                                        val wholeV = pendingVScroll.toInt()
                                        if (wholeV != 0) {
                                            val consumed = vScroll.dispatchRawDelta(wholeV.toFloat())
                                            pendingVScroll -= consumed
                                        }
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
                    // Velocity + sustain lane gestures. Lives before
                    // detectTapGestures so it can detect taps on its own and
                    // is keyed on Unit so the closure isn't restarted on every
                    // score mutation. Single-finger only — pinch is already
                    // claimed by the Initial-pass handler above.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = true)
                            val pos = down.position
                            val velTop = velocityLaneTopSnap.value
                            val velH = velocityLaneHeightSnap.value
                            val susTop = sustainLaneTopSnap.value
                            val susH = sustainLaneHeightSnap.value
                            // Only handle drags / taps that start inside a lane.
                            if (pos.y < velTop) return@awaitEachGesture

                            val s = scoreSnap.value
                            val ppq = s.ppq
                            val pxPerBeat = pxPerBeatSnap.value
                            val snap = (ppq / 16).coerceAtLeast(1)

                            if (pos.y < susTop) {
                                // VELOCITY lane: find closest note by start x and drag y → velocity.
                                val noteIdx = findClosestNoteByStartX(
                                    notes = s.notes,
                                    ppq = ppq,
                                    pxPerBeat = pxPerBeat,
                                    x = pos.x,
                                    maxBeats = VELOCITY_HIT_BEATS,
                                )
                                if (noteIdx == null) return@awaitEachGesture
                                onSelectSnap.value(noteIdx)
                                // Apply initial velocity immediately on down so tap-without-drag
                                // also edits velocity.
                                applyVelocityFromY(
                                    pos.y, velTop, velH,
                                    noteIdx, scoreSnap.value, onUpdateSnap.value,
                                )
                                down.consume()
                                drag(down.id) { change ->
                                    applyVelocityFromY(
                                        change.position.y, velTop, velH,
                                        noteIdx, scoreSnap.value, onUpdateSnap.value,
                                    )
                                    change.consume()
                                }
                            } else if (pos.y < susTop + susH) {
                                // SUSTAIN lane: tap to add/remove pair, drag near
                                // an edge to move that event.
                                val edgePx = laneEdgeGrabSnap.value
                                val hitEvent = hitTestSustainEdge(
                                    controlEvents = s.controlEvents,
                                    ppq = ppq,
                                    pxPerBeat = pxPerBeat,
                                    x = pos.x,
                                    edgePx = edgePx,
                                )
                                if (hitEvent != null) {
                                    // Drag this event's tick.
                                    down.consume()
                                    drag(down.id) { change ->
                                        val newTick = ((change.position.x / pxPerBeat) * ppq)
                                            .toInt()
                                            .coerceAtLeast(0)
                                        val snapped = (newTick / snap) * snap
                                        val cur = scoreSnap.value.controlEvents
                                        if (hitEvent !in cur.indices) return@drag
                                        val target = cur[hitEvent]
                                        val updated = cur.toMutableList()
                                        updated[hitEvent] = target.copy(tick = snapped)
                                        // Keep sustain events monotonic to avoid
                                        // crossing the matched up/down boundaries.
                                        updated.sortBy { it.tick }
                                        onControlEventsSnap.value(updated)
                                        change.consume()
                                    }
                                } else {
                                    // Tap (no drag past slop): consume down. If
                                    // tap lands inside a segment, delete the
                                    // segment's pair; otherwise insert a new
                                    // 1-beat segment at the snapped tick.
                                    val tappedTick = ((pos.x / pxPerBeat) * ppq).toInt()
                                        .coerceAtLeast(0)
                                    val snappedTick = (tappedTick / snap) * snap
                                    val segment = findSustainSegmentAtTick(
                                        controlEvents = s.controlEvents,
                                        tick = tappedTick,
                                    )
                                    val newControls = if (segment != null) {
                                        deleteSustainSegment(s.controlEvents, segment)
                                    } else {
                                        addSustainSegment(
                                            current = s.controlEvents,
                                            downTick = snappedTick,
                                            upTick = snappedTick + ppq,
                                        )
                                    }
                                    onControlEventsSnap.value(newControls)
                                    down.consume()
                                }
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                // Lane area is owned by the lane handler — skip
                                // here so tap-in-lane doesn't fall through to
                                // "add note at this pitch" (which would also
                                // pick a nonsense pitch from the y position).
                                if (offset.y >= velocityLaneTopSnap.value) return@detectTapGestures
                                val s = scoreSnap.value
                                val hit = hitTestNote(
                                    offset = offset,
                                    notes = s.notes,
                                    ppq = s.ppq,
                                    pxPerBeat = pxPerBeatSnap.value,
                                    pxPerSemitone = pxPerSemitoneSnap.value,
                                    maxPitch = maxPitch,
                                )
                                if (hit != null) {
                                    onSelectSnap.value(hit)
                                } else {
                                    val (midi, tick) = pixelToMusic(
                                        offset = offset,
                                        ppq = s.ppq,
                                        pxPerBeat = pxPerBeatSnap.value,
                                        pxPerSemitone = pxPerSemitoneSnap.value,
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
                                if (offset.y >= velocityLaneTopSnap.value) return@detectTapGestures
                                val s = scoreSnap.value
                                val hit = hitTestNote(
                                    offset = offset,
                                    notes = s.notes,
                                    ppq = s.ppq,
                                    pxPerBeat = pxPerBeatSnap.value,
                                    pxPerSemitone = pxPerSemitoneSnap.value,
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
                            // Lane area drags are owned by the lane handler.
                            if (down.position.y >= velocityLaneTopSnap.value) return@awaitEachGesture
                            val s = scoreSnap.value
                            val hit = hitTestNote(
                                offset = down.position,
                                notes = s.notes,
                                ppq = s.ppq,
                                pxPerBeat = pxPerBeatSnap.value,
                                pxPerSemitone = pxPerSemitoneSnap.value,
                                maxPitch = maxPitch,
                            ) ?: return@awaitEachGesture

                            val n = s.notes[hit]
                            val noteX = n.startTicks.toFloat() / s.ppq * pxPerBeatSnap.value
                            val noteW = (n.durationTicks.toFloat() / s.ppq * pxPerBeatSnap.value).coerceAtLeast(2f)
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
                                            pxPerBeat = pxPerBeatSnap.value,
                                            pxPerSemitone = pxPerSemitoneSnap.value,
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
                                        val newEndTick = ((change.position.x / pxPerBeatSnap.value) * curScore.ppq).toInt()
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
                    noteAreaHeight = noteAreaHeightPx,
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
                drawVelocityLane(
                    notes = score.notes,
                    ppq = score.ppq,
                    pxPerBeat = pxPerBeatF,
                    laneTop = velocityLaneTopPx,
                    laneHeight = velocityLaneHeightPx,
                    barWidth = velocityBarWidthPx,
                    canvasWidth = size.width,
                    selectedIndex = selectedIndex,
                )
                drawSustainLane(
                    controlEvents = score.controlEvents,
                    ppq = score.ppq,
                    pxPerBeat = pxPerBeatF,
                    laneTop = sustainLaneTopPx,
                    laneHeight = sustainLaneHeightPx,
                    canvasWidth = size.width,
                )
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
    height: Float,
) {
    drawRect(color = Color(0xFFEEEEEE), size = Size(width, height))
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawKeyboardLaneLabels(
    width: Float,
    velocityLaneTop: Float,
    velocityLaneHeight: Float,
    sustainLaneTop: Float,
    sustainLaneHeight: Float,
) {
    drawRect(
        color = Color(0xFFE0E0E0),
        topLeft = Offset(0f, velocityLaneTop),
        size = Size(width, velocityLaneHeight),
    )
    drawRect(
        color = Color(0xFFD7D7D7),
        topLeft = Offset(0f, sustainLaneTop),
        size = Size(width, sustainLaneHeight),
    )
    drawLine(
        color = Color(0x66000000),
        start = Offset(0f, velocityLaneTop),
        end = Offset(width, velocityLaneTop),
        strokeWidth = 1f,
    )
    drawLine(
        color = Color(0x33000000),
        start = Offset(0f, sustainLaneTop),
        end = Offset(width, sustainLaneTop),
        strokeWidth = 0.8f,
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGrid(
    minPitch: Int,
    maxPitch: Int,
    pxPerSemitone: Float,
    pxPerBeat: Float,
    totalBeats: Float,
    canvasSize: Size,
    noteAreaHeight: Float,
) {
    drawRect(color = Color(0xFFFAFAFA), size = Size(canvasSize.width, noteAreaHeight))
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
    // Vertical beat lines — drawn down the whole canvas so they line up
    // through the velocity / sustain lanes too.
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVelocityLane(
    notes: List<Note>,
    ppq: Int,
    pxPerBeat: Float,
    laneTop: Float,
    laneHeight: Float,
    barWidth: Float,
    canvasWidth: Float,
    selectedIndex: Int?,
) {
    drawRect(
        color = Color(0xFFEFEFEF),
        topLeft = Offset(0f, laneTop),
        size = Size(canvasWidth, laneHeight),
    )
    drawLine(
        color = Color(0x33000000),
        start = Offset(0f, laneTop),
        end = Offset(canvasWidth, laneTop),
        strokeWidth = 0.8f,
    )
    val halfBar = barWidth * 0.5f
    for ((idx, n) in notes.withIndex()) {
        val x = n.startTicks.toFloat() / ppq * pxPerBeat
        val vel = n.velocity.coerceIn(1, 127)
        val h = (vel / 127f) * laneHeight
        val color = CHANNEL_COLORS[n.channel.coerceIn(0, 15)]
        drawRect(
            color = color.copy(alpha = if (idx == selectedIndex) 1f else 0.85f),
            topLeft = Offset(x - halfBar, laneTop + (laneHeight - h)),
            size = Size(barWidth, h),
        )
        drawRect(
            color = if (idx == selectedIndex) Color(0xFFFFEB3B) else Color(0x66000000),
            topLeft = Offset(x - halfBar, laneTop + (laneHeight - h)),
            size = Size(barWidth, h),
            style = Stroke(width = if (idx == selectedIndex) 1.6f else 0.6f),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSustainLane(
    controlEvents: List<ControlEvent>,
    ppq: Int,
    pxPerBeat: Float,
    laneTop: Float,
    laneHeight: Float,
    canvasWidth: Float,
) {
    drawRect(
        color = Color(0xFFE7E7E7),
        topLeft = Offset(0f, laneTop),
        size = Size(canvasWidth, laneHeight),
    )
    val sustain = controlEvents
        .filter { it.kind == ControlKind.SUSTAIN }
        .sortedBy { it.tick }
    var openTick: Int? = null
    for (ev in sustain) {
        val held = ev.value >= 64
        if (held && openTick == null) {
            openTick = ev.tick
        } else if (!held && openTick != null) {
            val xStart = openTick!!.toFloat() / ppq * pxPerBeat
            val xEnd = ev.tick.toFloat() / ppq * pxPerBeat
            val w = (xEnd - xStart).coerceAtLeast(2f)
            drawRect(
                color = Color(0x88FF8F00),
                topLeft = Offset(xStart, laneTop + 4f),
                size = Size(w, laneHeight - 8f),
            )
            drawLine(
                color = Color(0xFF8B5300),
                start = Offset(xStart, laneTop + 4f),
                end = Offset(xStart, laneTop + laneHeight - 4f),
                strokeWidth = 1.5f,
            )
            drawLine(
                color = Color(0xFF8B5300),
                start = Offset(xEnd, laneTop + 4f),
                end = Offset(xEnd, laneTop + laneHeight - 4f),
                strokeWidth = 1.5f,
            )
            openTick = null
        }
    }
    // Trailing down with no up: draw an open-ended segment to the canvas edge.
    openTick?.let { t ->
        val xStart = t.toFloat() / ppq * pxPerBeat
        drawRect(
            color = Color(0x55FF8F00),
            topLeft = Offset(xStart, laneTop + 4f),
            size = Size(canvasWidth - xStart, laneHeight - 8f),
        )
    }
    drawLine(
        color = Color(0x33000000),
        start = Offset(0f, laneTop),
        end = Offset(canvasWidth, laneTop),
        strokeWidth = 0.5f,
    )
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
 * Closest note (by absolute distance to startTicks) within [maxBeats] beats
 * of the tapped x position. Returns null if none qualify.
 */
private fun findClosestNoteByStartX(
    notes: List<Note>,
    ppq: Int,
    pxPerBeat: Float,
    x: Float,
    maxBeats: Float,
): Int? {
    if (notes.isEmpty() || pxPerBeat <= 0f || ppq <= 0) return null
    val maxBeatsPx = maxBeats * pxPerBeat
    var bestIdx = -1
    var bestDist = Float.MAX_VALUE
    for (i in notes.indices) {
        val noteX = notes[i].startTicks.toFloat() / ppq * pxPerBeat
        val d = abs(noteX - x)
        if (d < bestDist) {
            bestDist = d
            bestIdx = i
        }
    }
    return if (bestIdx >= 0 && bestDist <= maxBeatsPx) bestIdx else null
}

/**
 * Map a y position inside the velocity lane to a 1..127 velocity and push
 * the update through the existing [onUpdateNote] callback.
 */
private fun applyVelocityFromY(
    y: Float,
    laneTop: Float,
    laneHeight: Float,
    noteIdx: Int,
    score: MidiScore,
    onUpdate: (Int, Note) -> Unit,
) {
    if (noteIdx !in score.notes.indices || laneHeight <= 0f) return
    val rel = ((y - laneTop) / laneHeight).coerceIn(0f, 1f)
    val velocity = ((1f - rel) * 127f).toInt().coerceIn(1, 127)
    val n = score.notes[noteIdx]
    if (n.velocity == velocity) return
    onUpdate(noteIdx, n.copy(velocity = velocity))
}

/**
 * Returns the index of a sustain CC event whose tick maps to within
 * [edgePx] of [x]. Used for grabbing a sustain segment's boundary to drag it.
 */
private fun hitTestSustainEdge(
    controlEvents: List<ControlEvent>,
    ppq: Int,
    pxPerBeat: Float,
    x: Float,
    edgePx: Float,
): Int? {
    if (pxPerBeat <= 0f || ppq <= 0) return null
    var best = -1
    var bestDist = edgePx
    for (i in controlEvents.indices) {
        val e = controlEvents[i]
        if (e.kind != ControlKind.SUSTAIN) continue
        val ex = e.tick.toFloat() / ppq * pxPerBeat
        val d = abs(ex - x)
        if (d <= bestDist) {
            bestDist = d
            best = i
        }
    }
    return if (best >= 0) best else null
}

/**
 * If [tick] falls inside a sustain segment (down ≤ tick ≤ up), return the
 * pair of indices into [controlEvents] for the down and up events. The
 * editor uses this to delete a segment on tap-inside.
 */
private fun findSustainSegmentAtTick(
    controlEvents: List<ControlEvent>,
    tick: Int,
): SustainSegment? {
    // Walk sustain events in tick order, pairing alternating down→up.
    val indexed = controlEvents
        .withIndex()
        .filter { it.value.kind == ControlKind.SUSTAIN }
        .sortedBy { it.value.tick }
    var openIdx = -1
    var openTick = 0
    for (e in indexed) {
        val held = e.value.value >= 64
        if (held && openIdx < 0) {
            openIdx = e.index
            openTick = e.value.tick
        } else if (!held && openIdx >= 0) {
            if (tick in openTick..e.value.tick) {
                return SustainSegment(downIndex = openIdx, upIndex = e.index)
            }
            openIdx = -1
        }
    }
    return null
}

private data class SustainSegment(val downIndex: Int, val upIndex: Int)

private fun deleteSustainSegment(
    current: List<ControlEvent>,
    segment: SustainSegment,
): List<ControlEvent> {
    val out = current.toMutableList()
    // Remove the higher index first so the lower index doesn't shift.
    val high = max(segment.downIndex, segment.upIndex)
    val low = min(segment.downIndex, segment.upIndex)
    if (high in out.indices) out.removeAt(high)
    if (low in out.indices) out.removeAt(low)
    return out
}

private fun addSustainSegment(
    current: List<ControlEvent>,
    downTick: Int,
    upTick: Int,
): List<ControlEvent> {
    val safeUp = upTick.coerceAtLeast(downTick + 1)
    val out = current.toMutableList()
    out += ControlEvent(downTick, 0, ControlKind.SUSTAIN, 127)
    out += ControlEvent(safeUp, 0, ControlKind.SUSTAIN, 0)
    out.sortBy { it.tick }
    return out
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
