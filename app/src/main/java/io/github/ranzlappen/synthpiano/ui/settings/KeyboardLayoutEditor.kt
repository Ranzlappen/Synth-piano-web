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
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Tune
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
import io.github.ranzlappen.synthpiano.data.ModifierPanel
import io.github.ranzlappen.synthpiano.data.midiToNoteName

/**
 * Fullscreen drag-and-drop editor for the on-screen Perform tab layout.
 * Manages two kinds of containers — keyboard panels and chord-modifier
 * panels — both placeable, resizable, and rotatable inside the play area.
 *
 * Each panel renders as a draggable card in the editor canvas. Long-press
 * the card body to move it, drag the bottom-right handle to resize, tap
 * ↻ to cycle 90°, tap × to delete (when more than one panel of that
 * kind remains). The toolbar's "+ Keys" / "+ Mods" buttons append a new
 * panel of the chosen kind.
 *
 * Panels are shown un-rotated in this editor view so gestures stay
 * predictable; the rotation value is shown as a badge and applied at play
 * time by [io.github.ranzlappen.synthpiano.ui.play.KeyboardLayoutHost].
 */
@Composable
fun KeyboardLayoutEditor(
    initial: KeyboardLayout,
    onSave: (KeyboardLayout) -> Unit,
    onCancel: () -> Unit,
) {
    val draftKbs = remember {
        mutableStateListOf<KeyboardPanel>().apply { addAll(initial.panels.map { it.normalized() }) }
    }
    val draftMods = remember {
        mutableStateListOf<ModifierPanel>().apply { addAll(initial.modifiers.map { it.normalized() }) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            EditorTopBar(
                onCancel = onCancel,
                onReset = {
                    draftKbs.clear()
                    draftKbs.addAll(BuiltInLayouts.DEFAULT.panels)
                    draftMods.clear()
                    draftMods.addAll(BuiltInLayouts.DEFAULT.modifiers)
                },
                onAddKeyboard = {
                    draftKbs += KeyboardPanel(
                        xFraction = 0.30f,
                        yFraction = 0.30f,
                        widthFraction = 0.40f,
                        heightFraction = 0.40f,
                        rotationDeg = 0,
                        firstMidi = 60,
                        whiteKeyCount = 14,
                    )
                },
                onAddModifier = {
                    draftMods += ModifierPanel(
                        xFraction = 0.10f,
                        yFraction = 0.10f,
                        widthFraction = 0.80f,
                        heightFraction = 0.20f,
                        rotationDeg = 0,
                    )
                },
                onSave = {
                    val layout = KeyboardLayout(
                        name = if (initial.builtin) "Custom" else initial.name,
                        panels = draftKbs.map { it.normalized() },
                        modifiers = draftMods.map { it.normalized() },
                        builtin = false,
                    )
                    onSave(layout)
                },
                canSave = draftKbs.isNotEmpty() || draftMods.isNotEmpty(),
            )
            Spacer(Modifier.size(8.dp))
            EditorCanvas(
                draftKbs = draftKbs,
                draftMods = draftMods,
                onChangeKb = { idx, p -> draftKbs[idx] = p.normalized() },
                onDeleteKb = { idx -> if (draftKbs.size > 1) draftKbs.removeAt(idx) },
                onChangeMod = { idx, p -> draftMods[idx] = p.normalized() },
                onDeleteMod = { idx -> draftMods.removeAt(idx) },
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
    onAddKeyboard: () -> Unit,
    onAddModifier: () -> Unit,
    onSave: () -> Unit,
    canSave: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TextButton(onClick = onCancel) { Text("Cancel") }
        Text(
            "Edit Layout",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onReset) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Reset")
        }
        TextButton(onClick = onAddKeyboard) {
            Icon(Icons.Filled.Piano, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("+ Keys")
        }
        TextButton(onClick = onAddModifier) {
            Icon(Icons.Filled.Tune, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("+ Mods")
        }
        Button(onClick = onSave, enabled = canSave) { Text("Save") }
    }
}

@Composable
private fun EditorCanvas(
    draftKbs: MutableList<KeyboardPanel>,
    draftMods: MutableList<ModifierPanel>,
    onChangeKb: (Int, KeyboardPanel) -> Unit,
    onDeleteKb: (Int) -> Unit,
    onChangeMod: (Int, ModifierPanel) -> Unit,
    onDeleteMod: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val cw = constraints.maxWidth
        val ch = constraints.maxHeight
        draftMods.forEachIndexed { idx, panel ->
            EditableModifierView(
                panel = panel,
                containerWPx = cw,
                containerHPx = ch,
                onChange = { onChangeMod(idx, it) },
                onDelete = { onDeleteMod(idx) },
            )
        }
        draftKbs.forEachIndexed { idx, panel ->
            EditableKeyboardView(
                panel = panel,
                containerWPx = cw,
                containerHPx = ch,
                canDelete = draftKbs.size > 1,
                onChange = { onChangeKb(idx, it) },
                onDelete = { onDeleteKb(idx) },
            )
        }
        if (draftKbs.isEmpty() && draftMods.isEmpty()) {
            Text(
                "Empty layout — tap + Keys or + Mods to add a panel.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
            )
        } else {
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
private fun EditableKeyboardView(
    panel: KeyboardPanel,
    containerWPx: Int,
    containerHPx: Int,
    canDelete: Boolean,
    onChange: (KeyboardPanel) -> Unit,
    onDelete: () -> Unit,
) {
    val latestPanel by rememberUpdatedState(panel)
    val latestOnChange by rememberUpdatedState(onChange)

    EditableChrome(
        id = panel.id,
        xFraction = panel.xFraction,
        yFraction = panel.yFraction,
        widthFraction = panel.widthFraction,
        heightFraction = panel.heightFraction,
        rotationDeg = panel.rotationDeg,
        containerWPx = containerWPx,
        containerHPx = containerHPx,
        accentColor = MaterialTheme.colorScheme.primary,
        canDelete = canDelete,
        onMove = { dx, dy ->
            val cur = latestPanel
            latestOnChange(cur.copy(
                xFraction = cur.xFraction + dx,
                yFraction = cur.yFraction + dy,
            ).normalized())
        },
        onResize = { dw, dh ->
            val cur = latestPanel
            latestOnChange(cur.copy(
                widthFraction = cur.widthFraction + dw,
                heightFraction = cur.heightFraction + dh,
            ).normalized())
        },
        onRotate = {
            val cur = latestPanel
            latestOnChange(cur.copy(rotationDeg = (cur.rotationDeg + 90) % 360))
        },
        onDelete = onDelete,
    ) {
        val firstName = midiToNoteName(panel.firstMidi)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                Icons.Filled.Piano,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text("${panel.whiteKeyCount} white keys", style = MaterialTheme.typography.labelMedium)
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
    }
}

@Composable
private fun EditableModifierView(
    panel: ModifierPanel,
    containerWPx: Int,
    containerHPx: Int,
    onChange: (ModifierPanel) -> Unit,
    onDelete: () -> Unit,
) {
    val latestPanel by rememberUpdatedState(panel)
    val latestOnChange by rememberUpdatedState(onChange)

    EditableChrome(
        id = panel.id,
        xFraction = panel.xFraction,
        yFraction = panel.yFraction,
        widthFraction = panel.widthFraction,
        heightFraction = panel.heightFraction,
        rotationDeg = panel.rotationDeg,
        containerWPx = containerWPx,
        containerHPx = containerHPx,
        accentColor = MaterialTheme.colorScheme.tertiary,
        canDelete = true,
        onMove = { dx, dy ->
            val cur = latestPanel
            latestOnChange(cur.copy(
                xFraction = cur.xFraction + dx,
                yFraction = cur.yFraction + dy,
            ).normalized())
        },
        onResize = { dw, dh ->
            val cur = latestPanel
            latestOnChange(cur.copy(
                widthFraction = cur.widthFraction + dw,
                heightFraction = cur.heightFraction + dh,
            ).normalized())
        },
        onRotate = {
            val cur = latestPanel
            latestOnChange(cur.copy(rotationDeg = (cur.rotationDeg + 90) % 360))
        },
        onDelete = onDelete,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                Icons.Filled.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Text("Modifiers", style = MaterialTheme.typography.labelMedium)
            val flags = buildList {
                if (panel.showLock) add("LOCK")
                if (panel.showShift) add("SHIFT")
                if (panel.showZoom) add("Zoom")
            }.joinToString(" · ").ifEmpty { "(empty)" }
            Text(
                flags,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (panel.rotationDeg != 0) {
                Text(
                    "↻ ${panel.rotationDeg}°",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun EditableChrome(
    id: String,
    xFraction: Float,
    yFraction: Float,
    widthFraction: Float,
    heightFraction: Float,
    rotationDeg: Int,
    containerWPx: Int,
    containerHPx: Int,
    accentColor: Color,
    canDelete: Boolean,
    onMove: (dx: Float, dy: Float) -> Unit,
    onResize: (dw: Float, dh: Float) -> Unit,
    onRotate: () -> Unit,
    onDelete: () -> Unit,
    body: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val xPx = (xFraction * containerWPx).toInt()
    val yPx = (yFraction * containerHPx).toInt()
    val wPx = (widthFraction * containerWPx).toInt()
    val hPx = (heightFraction * containerHPx).toInt()
    val widthDp = with(density) { wPx.toDp() }
    val heightDp = with(density) { hPx.toDp() }

    Box(
        modifier = Modifier
            .offset { IntOffset(xPx, yPx) }
            .size(width = widthDp, height = heightDp)
            .clip(RoundedCornerShape(10.dp))
            .background(accentColor.copy(alpha = 0.15f))
            .border(width = 2.dp, color = accentColor, shape = RoundedCornerShape(10.dp))
            .pointerInput(id, containerWPx, containerHPx) {
                detectDragGesturesAfterLongPress(
                    onDrag = { _, drag ->
                        if (containerWPx > 0 && containerHPx > 0) {
                            onMove(drag.x / containerWPx, drag.y / containerHPx)
                        }
                    },
                )
            },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(8.dp),
        ) { body() }

        if (canDelete) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(2.dp)
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), CircleShape),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Delete panel",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        IconButton(
            onClick = onRotate,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(32.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), CircleShape),
        ) {
            Icon(Icons.Filled.RotateRight, contentDescription = "Rotate panel")
        }

        // Hint icon for movability — shown subtly in the corner.
        Icon(
            Icons.Filled.OpenWith,
            contentDescription = null,
            tint = accentColor.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .size(14.dp),
        )

        // Bottom-right resize handle.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(2.dp)
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(accentColor.copy(alpha = 0.85f))
                .pointerInput(id, containerWPx, containerHPx) {
                    detectDragGestures(
                        onDrag = { _, drag ->
                            if (containerWPx > 0 && containerHPx > 0) {
                                onResize(drag.x / containerWPx, drag.y / containerHPx)
                            }
                        },
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(8.dp)
                    .background(Color.White, CircleShape),
            )
        }
    }
}
