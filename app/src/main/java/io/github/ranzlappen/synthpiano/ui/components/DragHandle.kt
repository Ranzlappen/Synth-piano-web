package io.github.ranzlappen.synthpiano.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/**
 * Horizontal drag handle for resizing two stacked panes vertically.
 * [onDelta] receives the drag amount in pixels (positive = down).
 *
 * Uses [draggable] with a fresh [rememberDraggableState] so the gesture
 * integrates with parent nested-scroll containers without fighting them.
 */
@Composable
fun HorizontalDragHandle(
    onDelta: (dyPx: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberDraggableState { dy -> onDelta(dy) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .draggable(orientation = Orientation.Vertical, state = state),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 40.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        )
    }
}

/**
 * Vertical drag handle for resizing two side-by-side panes horizontally.
 * [onDelta] receives the drag amount in pixels (positive = right).
 */
@Composable
fun VerticalDragHandle(
    onDelta: (dxPx: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberDraggableState { dx -> onDelta(dx) }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(14.dp)
            .draggable(orientation = Orientation.Horizontal, state = state),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
        )
    }
}
