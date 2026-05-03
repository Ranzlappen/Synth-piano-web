package io.github.ranzlappen.synthpiano.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.audio.EnvelopeShape
import io.github.ranzlappen.synthpiano.audio.EnvelopeVertex
import kotlin.math.max

private const val MIN_VERTICES = 3
private const val MAX_TOTAL_SECONDS = 12f

/**
 * Interactive multi-segment envelope editor. Renders the envelope as a
 * polyline whose vertices are draggable handles in two axes. The
 * sustain vertex is drawn with a dashed outline so the held point is
 * visually obvious.
 *
 * Gestures:
 *   * Drag a handle — moves that vertex in time (X) and level (Y).
 *     The first vertex's time is pinned to 0.
 *   * Tap on the polyline (away from any handle) — inserts a vertex
 *     at the tapped point.
 *   * Long-press on a handle — removes that vertex (subject to a
 *     minimum of 3 vertices and never removing the sustain pin).
 *
 * All edits emit the new shape via [onShapeChange]; the parent owns
 * the state and is responsible for pushing it to the synth.
 */
@Composable
fun EnvelopeEditor(
    shape: EnvelopeShape,
    onShapeChange: (EnvelopeShape) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val sustainAccent = MaterialTheme.colorScheme.tertiary

    val activeIdx = remember { mutableIntStateOf(-1) }

    // Stable refs so the long-lived pointerInput coroutine always sees
    // fresh shape + callback without restarting (which would cancel
    // an in-progress drag).
    val shapeRef = rememberUpdatedState(shape)
    val onChangeRef = rememberUpdatedState(onShapeChange)

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val handleRadiusPx = with(density) { 7.dp.toPx() }
        val touchSlopPx = with(density) { 18.dp.toPx() }
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        Canvas(
            modifier = Modifier
                .matchParentSize()
                // Drag: pick the nearest handle on drag start, then mutate
                // it as the finger moves.
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            activeIdx.intValue = nearestVertex(
                                shapeRef.value, offset, widthPx, heightPx, touchSlopPx,
                            )
                        },
                        onDragEnd = { activeIdx.intValue = -1 },
                        onDragCancel = { activeIdx.intValue = -1 },
                        onDrag = { change, dragAmount ->
                            val idx = activeIdx.intValue
                            if (idx < 0) return@detectDragGestures
                            change.consume()
                            val s = shapeRef.value
                            val total = totalSeconds(s)
                            val v = s.vertices[idx]
                            val dt = (dragAmount.x / widthPx) * total
                            val dl = -dragAmount.y / heightPx
                            val newTime = if (idx == 0) 0f
                                          else (v.timeSec + dt).coerceIn(0f, MAX_TOTAL_SECONDS)
                            val newLevel = (v.level + dl).coerceIn(0f, 1f)
                            val mut = s.vertices.toMutableList()
                            mut[idx] = v.copy(timeSec = newTime, level = newLevel)
                            onChangeRef.value(s.copy(vertices = mut))
                        },
                    )
                }
                // Tap on empty area inserts a vertex; long-press on a
                // handle removes it (subject to MIN_VERTICES guard and
                // sustain-pin protection).
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            val s = shapeRef.value
                            val nearest = nearestVertex(
                                s, offset, widthPx, heightPx, touchSlopPx,
                            )
                            if (nearest < 0) {
                                val inserted = insertVertexAt(s, offset, widthPx, heightPx)
                                if (inserted != null) onChangeRef.value(inserted)
                            }
                        },
                        onLongPress = { offset ->
                            val s = shapeRef.value
                            val idx = nearestVertex(
                                s, offset, widthPx, heightPx, touchSlopPx,
                            )
                            if (idx > 0 &&
                                idx < s.vertices.size - 1 &&
                                idx != s.sustainIndex &&
                                s.vertices.size > MIN_VERTICES
                            ) {
                                val mut = s.vertices.toMutableList()
                                mut.removeAt(idx)
                                val newSustain = if (s.sustainIndex > idx)
                                    s.sustainIndex - 1 else s.sustainIndex
                                onChangeRef.value(
                                    s.copy(vertices = mut, sustainIndex = newSustain),
                                )
                            }
                        },
                    )
                },
        ) {
            drawEnvelope(
                shape = shapeRef.value,
                width = size.width,
                height = size.height,
                lineColor = accent,
                handleColor = onSurface,
                sustainColor = sustainAccent,
                activeIdx = activeIdx.intValue,
                handleRadius = handleRadiusPx,
            )
        }
    }
}

private fun totalSeconds(shape: EnvelopeShape): Float {
    var t = 0f
    for (v in shape.vertices) t += v.timeSec
    return max(0.001f, t)
}

private fun layoutXs(shape: EnvelopeShape, width: Float): FloatArray {
    val total = totalSeconds(shape)
    val xs = FloatArray(shape.vertices.size)
    var acc = 0f
    shape.vertices.forEachIndexed { i, v ->
        acc += v.timeSec
        xs[i] = (acc / total) * width
    }
    return xs
}

private fun layoutYs(shape: EnvelopeShape, height: Float): FloatArray {
    val ys = FloatArray(shape.vertices.size)
    shape.vertices.forEachIndexed { i, v ->
        // Top of canvas = level 1, bottom = level 0; matches the
        // existing AdsrPreview convention so the editor reads as a
        // direct upgrade.
        ys[i] = (1f - v.level) * height
    }
    return ys
}

private fun nearestVertex(
    shape: EnvelopeShape,
    offset: Offset,
    width: Float,
    height: Float,
    radius: Float,
): Int {
    val xs = layoutXs(shape, width)
    val ys = layoutYs(shape, height)
    var bestIdx = -1
    var bestSq = radius * radius
    for (i in shape.vertices.indices) {
        val dx = xs[i] - offset.x
        val dy = ys[i] - offset.y
        val d2 = dx * dx + dy * dy
        if (d2 <= bestSq) {
            bestSq = d2
            bestIdx = i
        }
    }
    return bestIdx
}

private fun insertVertexAt(
    shape: EnvelopeShape,
    offset: Offset,
    width: Float,
    height: Float,
): EnvelopeShape? {
    if (shape.vertices.size >= EnvelopeShape.MAX_VERTICES) return null
    val xs = layoutXs(shape, width)
    // Find the segment whose x-range contains offset.x.
    var segIdx = -1
    for (i in 0 until xs.size - 1) {
        if (offset.x in xs[i]..xs[i + 1]) {
            segIdx = i
            break
        }
    }
    if (segIdx < 0) {
        // Past the last vertex — append at the end (extends the release tail).
        segIdx = xs.size - 2
        if (segIdx < 0) return null
    }
    val total = totalSeconds(shape)
    val xRange = xs[segIdx + 1] - xs[segIdx]
    val frac = if (xRange <= 0.0001f) 0.5f else
        ((offset.x - xs[segIdx]) / xRange).coerceIn(0f, 1f)

    val origNext = shape.vertices[segIdx + 1]
    val origNextTime = origNext.timeSec
    val newTime = (origNextTime * frac).coerceAtLeast(0f)
    val remainingTime = (origNextTime - newTime).coerceAtLeast(0f)

    // Level: pick whatever the user tapped at, clamped to [0, 1].
    val tappedLevel = (1f - (offset.y / height)).coerceIn(0f, 1f)

    val newVertex = EnvelopeVertex(
        timeSec = newTime,
        level = tappedLevel,
        curve = 0f,
    )
    val mut = shape.vertices.toMutableList()
    mut.add(segIdx + 1, newVertex)
    // The original next vertex's incoming time shrinks to what's left
    // of the segment we split.
    mut[segIdx + 2] = origNext.copy(timeSec = remainingTime)

    val newSustain = if (shape.sustainIndex > segIdx)
        shape.sustainIndex + 1 else shape.sustainIndex

    // Coerce total to MAX_TOTAL_SECONDS to keep the visual sane after
    // inserts in already-long shapes.
    var totalAfter = 0f
    for (v in mut) totalAfter += v.timeSec
    val finalList = if (totalAfter > MAX_TOTAL_SECONDS) {
        val scale = MAX_TOTAL_SECONDS / totalAfter
        mut.map { it.copy(timeSec = it.timeSec * scale) }
    } else mut

    return shape.copy(vertices = finalList, sustainIndex = newSustain)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEnvelope(
    shape: EnvelopeShape,
    width: Float,
    height: Float,
    lineColor: Color,
    handleColor: Color,
    sustainColor: Color,
    activeIdx: Int,
    handleRadius: Float,
) {
    val xs = layoutXs(shape, width)
    val ys = layoutYs(shape, height)
    val zeroLineY = height

    // Faint baseline for orientation.
    drawLine(
        color = Color.White.copy(alpha = 0.15f),
        start = Offset(0f, zeroLineY),
        end = Offset(width, zeroLineY),
        strokeWidth = 1f,
    )

    if (xs.isEmpty()) return

    // Polyline.
    val path = Path().apply {
        moveTo(xs[0], ys[0])
        for (i in 1 until xs.size) lineTo(xs[i], ys[i])
    }
    drawPath(path = path, color = lineColor, style = Stroke(width = 2.5f))

    // Vertex handles.
    for (i in xs.indices) {
        val isSustain = i == shape.sustainIndex
        val centre = Offset(xs[i], ys[i])
        if (isSustain) {
            drawCircle(
                color = sustainColor.copy(alpha = 0.25f),
                radius = handleRadius * 1.6f,
                center = centre,
                style = Stroke(
                    width = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
                ),
            )
            drawCircle(
                color = sustainColor,
                radius = handleRadius,
                center = centre,
            )
        } else {
            val fill = if (i == activeIdx) lineColor else handleColor.copy(alpha = 0.85f)
            drawCircle(
                color = fill,
                radius = handleRadius,
                center = centre,
            )
        }
    }
}
