package io.github.ranzlappen.synthpiano.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.data.BuiltInLayouts
import io.github.ranzlappen.synthpiano.data.KeyboardLayout
import io.github.ranzlappen.synthpiano.data.KeyboardPanel
import io.github.ranzlappen.synthpiano.data.midiToNoteName

/**
 * Fullscreen drag-and-drop editor for the on-screen keyboard layout.
 *
 * Each panel is rendered as a draggable card inside a virtual container
 * matching the play-area aspect. Long-press the card body to move it,
 * drag the bottom-right handle to resize, tap the rotate button to cycle
 * 90°, tap × to delete (when more than one remains). The "+ Add keyboard"
 * button appends a new panel at the center.
 *
 * Panels are kept un-rotated in this editor view so gestures stay
 * predictable; the rotation value is shown as a badge and applied at play
 * time by [io.github.ranzlappen.synthpiano.ui.play.KeyboardLayoutHost].
 */
@Composable
fun KeyboardLayoutEditor(
    initial: KeyboardLayout,
    onSave: (KeyboardLayout) -> Unit,
    onCancel: () -> Unit,
) {
    val draft = remember {
        mutableStateListOf<KeyboardPanel>().apply { addAll(initial.panels.map { it.normalized() }) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            EditorTopBar(
                onCancel = onCancel,
                onReset = {
                    draft.clear()
                    draft.addAll(BuiltInLayouts.DEFAULT.panels)
                },
                onAdd = {
                    draft += KeyboardPanel(
                        xFraction = 0.30f,
                        yFraction = 0.30f,
                        widthFraction = 0.40f,
                        heightFraction = 0.40f,
                        rotationDeg = 0,
                        firstMidi = 60,
                        whiteKeyCount = 14,
                    )
                },
                onSave = {
                    val layout = KeyboardLayout(
                        name = if (initial.builtin) "Custom" else initial.name,
                        panels = draft.map { it.normalized() },
                        builtin = false,
                    )
                    onSave(layout)
                },
                canSave = draft.isNotEmpty(),
            )
            Spacer(Modifier.size(8.dp))
            EditorCanvas(
                draft = draft,
                onChange = { idx, p -> draft[idx] = p.normalized() },
                onDelete = { idx -> if (draft.size > 1) draft.removeAt(idx) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp),
                    ),
            )
        }
    }
}

@Composable
private fun EditorTopBar(
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onAdd: () -> Unit,
    onSave: () -> Unit,
    canSave: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(onClick = onCancel) { Text("Cancel") }
        Text(
            "Edit Keyboard Layout",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onReset) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Reset")
        }
        TextButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Add")
        }
        Button(onClick = onSave, enabled = canSave) { Text("Save") }
    }
}

@Composable
private fun EditorCanvas(
    draft: MutableList<KeyboardPanel>,
    onChange: (Int, KeyboardPanel) -> Unit,
    onDelete: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val cw = constraints.maxWidth
        val ch = constraints.maxHeight
        draft.forEachIndexed { idx, panel ->
            EditablePanelView(
                panel = panel,
                containerWPx = cw,
                containerHPx = ch,
                canDelete = draft.size > 1,
                onChange = { onChange(idx, it) },
                onDelete = { onDelete(idx) },
            )
        }
        if (draft.isEmpty()) {
            Text(
                "No panels — tap Add to create one.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
            )
        }
        // Hint label.
        if (draft.isNotEmpty()) {
            Text(
                "Long-press to drag · drag corner to resize · ↻ to rotate",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun EditablePanelView(
    panel: KeyboardPanel,
    containerWPx: Int,
    containerHPx: Int,
    canDelete: Boolean,
    onChange: (KeyboardPanel) -> Unit,
    onDelete: () -> Unit,
) {
    val density = LocalDensity.current
    // Capture latest panel + onChange so the long-lived pointerInput closure
    // always operates on current state (drag deltas must compound).
    val latestPanel by rememberUpdatedState(panel)
    val latestOnChange by rememberUpdatedState(onChange)
    val xPx = (panel.xFraction * containerWPx).toInt()
    val yPx = (panel.yFraction * containerHPx).toInt()
    val wPx = (panel.widthFraction * containerWPx).toInt()
    val hPx = (panel.heightFraction * containerHPx).toInt()
    val widthDp = with(density) { wPx.toDp() }
    val heightDp = with(density) { hPx.toDp() }

    Box(
        modifier = Modifier
            .offset { IntOffset(xPx, yPx) }
            .size(width = widthDp, height = heightDp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(10.dp),
            )
            .pointerInput(panel.id, containerWPx, containerHPx) {
                detectDragGesturesAfterLongPress(
                    onDrag = { _, drag ->
                        if (containerWPx > 0 && containerHPx > 0) {
                            val dx = drag.x / containerWPx
                            val dy = drag.y / containerHPx
                            val cur = latestPanel
                            latestOnChange(
                                cur.copy(
                                    xFraction = (cur.xFraction + dx),
                                    yFraction = (cur.yFraction + dy),
                                ).normalized()
                            )
                        }
                    },
                )
            },
    ) {
        // Body — placeholder summarizing the panel's range.
        val firstName = midiToNoteName(panel.firstMidi)
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                Icons.Filled.OpenWith,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "${panel.whiteKeyCount} white keys",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                "from $firstName",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (panel.rotationDeg != 0) {
                Text(
                    "↻ ${panel.rotationDeg}°",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Top-left: delete (×).
        if (canDelete) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(2.dp)
                    .size(32.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        CircleShape,
                    ),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Delete panel",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        // Top-right: rotate (↻).
        IconButton(
            onClick = {
                onChange(panel.copy(rotationDeg = (panel.rotationDeg + 90) % 360))
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(32.dp)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    CircleShape,
                ),
        ) {
            Icon(
                Icons.Filled.RotateRight,
                contentDescription = "Rotate panel",
            )
        }

        // Bottom-right: resize handle.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(2.dp)
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                .pointerInput(panel.id, containerWPx, containerHPx) {
                    detectDragGestures(
                        onDrag = { _, drag ->
                            if (containerWPx > 0 && containerHPx > 0) {
                                val dw = drag.x / containerWPx
                                val dh = drag.y / containerHPx
                                val cur = latestPanel
                                latestOnChange(
                                    cur.copy(
                                        widthFraction = (cur.widthFraction + dw),
                                        heightFraction = (cur.heightFraction + dh),
                                    ).normalized()
                                )
                            }
                        },
                    )
                },
        ) {
            // Visual hint — diagonal lines drawn as two dots.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(8.dp)
                    .background(Color.White, CircleShape),
            )
        }
    }
}
