package io.github.ranzlappen.synthpiano.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.R
import io.github.ranzlappen.synthpiano.data.BuiltInLayouts
import io.github.ranzlappen.synthpiano.data.ChordInversion
import io.github.ranzlappen.synthpiano.data.ChordQuality
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
    onSaveAs: ((KeyboardLayout) -> Unit)? = null,
    existingNames: Set<String> = emptySet(),
) {
    val draftKbs = remember {
        mutableStateListOf<KeyboardPanel>().apply { addAll(initial.panels.map { it.normalized() }) }
    }
    val draftMods = remember {
        mutableStateListOf<ModifierPanel>().apply { addAll(initial.modifiers.map { it.normalized() }) }
    }
    var showSaveAs by remember { mutableStateOf(false) }

    fun snapshotLayout(name: String, builtin: Boolean = false): KeyboardLayout =
        KeyboardLayout(
            name = name,
            panels = draftKbs.map { it.normalized() },
            modifiers = draftMods.map { it.normalized() },
            builtin = builtin,
        )

    Surface(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        color = MaterialTheme.colorScheme.background,
    ) {
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
                    onSave(snapshotLayout(
                        name = if (initial.builtin) "Custom" else initial.name,
                    ))
                },
                onSaveAs = if (onSaveAs != null) {
                    { showSaveAs = true }
                } else null,
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

    if (showSaveAs && onSaveAs != null) {
        SaveLayoutAsDialog(
            initialName = if (initial.builtin) "" else initial.name,
            takenNames = existingNames + BuiltInLayouts.ALL.map { it.name }.toSet(),
            onDismiss = { showSaveAs = false },
            onConfirm = { name ->
                onSaveAs(snapshotLayout(name = name))
                showSaveAs = false
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditorTopBar(
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onAddKeyboard: () -> Unit,
    onAddModifier: () -> Unit,
    onSave: () -> Unit,
    onSaveAs: (() -> Unit)?,
    canSave: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            Text(
                stringResource(R.string.editor_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = onReset) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.settings_layout_reset))
            }
            TextButton(onClick = onAddKeyboard) {
                Icon(Icons.Filled.Piano, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.editor_add_keys))
            }
            TextButton(onClick = onAddModifier) {
                Icon(Icons.Filled.Tune, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.editor_add_mods))
            }
            if (onSaveAs != null) {
                TextButton(onClick = onSaveAs, enabled = canSave) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.score_save_as))
                }
            }
            Button(onClick = onSave, enabled = canSave) { Text(stringResource(R.string.action_save)) }
        }
    }
}

@Composable
private fun SaveLayoutAsDialog(
    initialName: String,
    takenNames: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val trimmed = name.trim()
    val builtinNames = BuiltInLayouts.ALL.map { it.name }.toSet()
    val collidesBuiltIn = trimmed in builtinNames
    val isValid = trimmed.isNotEmpty() && !collidesBuiltIn

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_save_as_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.editor_layout_name)) },
                    singleLine = true,
                )
                when {
                    collidesBuiltIn -> Text(
                        stringResource(R.string.editor_name_reserved),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    trimmed in takenNames -> Text(
                        stringResource(R.string.editor_name_overwrite),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(trimmed) }, enabled = isValid) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
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
                stringResource(R.string.editor_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
            )
        } else {
            Text(
                stringResource(R.string.editor_gesture_hint),
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
    var showEdit by remember { mutableStateOf(false) }

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
        onEdit = { showEdit = true },
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
            Text(
                pluralStringResource(
                    R.plurals.editor_white_key_count,
                    panel.whiteKeyCount,
                    panel.whiteKeyCount,
                ),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                stringResource(R.string.editor_panel_from_note, firstName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (panel.rotationDeg != 0) {
                Text(
                    stringResource(R.string.editor_panel_rotation, panel.rotationDeg),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    if (showEdit) {
        KeyboardPanelEditDialog(
            panel = panel,
            onDismiss = { showEdit = false },
            onApply = { onChange(it.normalized()); showEdit = false },
        )
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
    var showEdit by remember { mutableStateOf(false) }

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
        onEdit = { showEdit = true },
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
            Text(
                stringResource(R.string.editor_modifiers_label),
                style = MaterialTheme.typography.labelMedium,
            )
            val emptyLabel = stringResource(R.string.editor_modifiers_empty)
            val flags = buildList {
                if (panel.showLock) add("LOCK")
                if (panel.showShift) add("SHIFT")
                if (panel.showZoom) add("Zoom")
            }.joinToString(" · ").ifEmpty { emptyLabel }
            Text(
                flags,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (panel.rotationDeg != 0) {
                Text(
                    stringResource(R.string.editor_panel_rotation, panel.rotationDeg),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }

    if (showEdit) {
        ModifierPanelEditDialog(
            panel = panel,
            onDismiss = { showEdit = false },
            onApply = { onChange(it.normalized()); showEdit = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModifierPanelEditDialog(
    panel: ModifierPanel,
    onDismiss: () -> Unit,
    onApply: (ModifierPanel) -> Unit,
) {
    var showLock by remember { mutableStateOf(panel.showLock) }
    var showShift by remember { mutableStateOf(panel.showShift) }
    var showZoom by remember { mutableStateOf(panel.showZoom) }
    val selectedQualities = remember {
        mutableStateListOf<ChordQuality>().apply { addAll(panel.qualities) }
    }
    val selectedInversions = remember {
        mutableStateListOf<ChordInversion>().apply { addAll(panel.inversions) }
    }
    val allInversions = ModifierPanel.DEFAULT_INVERSIONS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_modifier_panel_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.editor_modifier_subcontrols),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ToggleRow(
                    label = stringResource(R.string.editor_show_lock),
                    checked = showLock,
                    onChange = { showLock = it },
                )
                ToggleRow(
                    label = stringResource(R.string.editor_show_shift),
                    checked = showShift,
                    onChange = { showShift = it },
                )
                ToggleRow(
                    label = stringResource(R.string.editor_show_zoom),
                    checked = showZoom,
                    onChange = { showZoom = it },
                )
                Text(
                    stringResource(R.string.editor_chord_qualities),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ChordQuality.entries.forEach { q ->
                        FilterChip(
                            selected = q in selectedQualities,
                            onClick = {
                                if (q in selectedQualities) selectedQualities.remove(q)
                                else selectedQualities.add(q)
                            },
                            label = { Text(q.label()) },
                        )
                    }
                }
                Text(
                    stringResource(R.string.editor_inversions),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    allInversions.forEach { inv ->
                        FilterChip(
                            selected = inv in selectedInversions,
                            onClick = {
                                if (inv in selectedInversions) selectedInversions.remove(inv)
                                else selectedInversions.add(inv)
                            },
                            label = { Text(inv.label()) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = ChordQuality.entries.filter { it in selectedQualities }
                val i = allInversions.filter { it in selectedInversions }
                onApply(
                    panel.copy(
                        showLock = showLock,
                        showShift = showShift,
                        showZoom = showZoom,
                        qualities = q,
                        inversions = i,
                    ),
                )
            }) { Text(stringResource(R.string.action_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun KeyboardPanelEditDialog(
    panel: KeyboardPanel,
    onDismiss: () -> Unit,
    onApply: (KeyboardPanel) -> Unit,
) {
    // Lowest note: snap to nearest white key in the slider's value.
    var lowestMidi by remember { mutableStateOf(panel.firstMidi.toFloat()) }
    var whiteKeyCount by remember { mutableStateOf(panel.whiteKeyCount.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_keyboard_panel_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        R.string.editor_lowest_note,
                        midiToNoteName(snapToWhiteKey(lowestMidi.toInt())),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = lowestMidi,
                    onValueChange = { lowestMidi = it },
                    valueRange = 21f..96f,
                    steps = 96 - 21 - 1,
                )
                Text(
                    stringResource(R.string.editor_white_keys_count, whiteKeyCount.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = whiteKeyCount,
                    onValueChange = { whiteKeyCount = it },
                    valueRange = 7f..52f,
                    steps = 52 - 7 - 1,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(panel.copy(
                    firstMidi = snapToWhiteKey(lowestMidi.toInt()),
                    whiteKeyCount = whiteKeyCount.toInt().coerceIn(7, 52),
                ))
            }) { Text(stringResource(R.string.action_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private val WHITE_KEY_PCS_LOCAL: Set<Int> = setOf(0, 2, 4, 5, 7, 9, 11)

private fun snapToWhiteKey(midi: Int): Int {
    if ((midi % 12) in WHITE_KEY_PCS_LOCAL) return midi
    // Snap up to the next white key.
    var m = midi
    while (m < 127 && (m % 12) !in WHITE_KEY_PCS_LOCAL) m++
    return m
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
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
    onEdit: () -> Unit,
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
                    contentDescription = stringResource(R.string.editor_delete_panel),
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
            Icon(
                Icons.Filled.RotateRight,
                contentDescription = stringResource(R.string.editor_rotate_panel),
            )
        }

        // Bottom-left: per-panel options.
        IconButton(
            onClick = onEdit,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(2.dp)
                .size(32.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), CircleShape),
        ) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = stringResource(R.string.editor_panel_options),
                tint = accentColor,
            )
        }

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
