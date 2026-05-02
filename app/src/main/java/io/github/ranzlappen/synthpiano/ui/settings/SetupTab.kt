package io.github.ranzlappen.synthpiano.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.ranzlappen.synthpiano.BuildConfig
import io.github.ranzlappen.synthpiano.R
import io.github.ranzlappen.synthpiano.audio.SynthController
import io.github.ranzlappen.synthpiano.data.BuiltInLayouts
import io.github.ranzlappen.synthpiano.data.LayoutRepository
import io.github.ranzlappen.synthpiano.data.PreferencesRepository
import io.github.ranzlappen.synthpiano.input.HwKeyboardMapper
import io.github.ranzlappen.synthpiano.midi.MidiManager
import io.github.ranzlappen.synthpiano.ui.components.GlassCard
import io.github.ranzlappen.synthpiano.ui.components.KeymapEditor
import io.github.ranzlappen.synthpiano.ui.theme.ThemeAccent
import kotlinx.coroutines.launch

/**
 * The SETUP tab: connectivity, hardware keyboard rebinding, theme picker,
 * and about. Single vertically-scrolling column of glass cards.
 */
@Composable
fun SetupTab(
    synth: SynthController,
    prefs: PreferencesRepository,
    layouts: LayoutRepository,
    midi: MidiManager,
    hwKeys: HwKeyboardMapper,
    modifier: Modifier = Modifier,
    autoOpenEditor: Boolean = false,
    onAutoOpenConsumed: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val devices by midi.connectedDeviceNames.collectAsState()
    val started by synth.started.collectAsState()
    val accentName by prefs.themeAccent.collectAsState(initial = "AURORA")
    val accent = ThemeAccent.fromName(accentName)
    val keyboardLayout by prefs.keyboardLayout.collectAsState(initial = BuiltInLayouts.DEFAULT)
    val userLayouts by layouts.userLayouts.collectAsState(initial = emptyList())
    var editingLayout by remember { mutableStateOf(false) }
    var saveAsCurrent by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(autoOpenEditor) {
        if (autoOpenEditor) {
            editingLayout = true
            onAutoOpenConsumed()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Audio
        SettingsSection(title = stringResource(R.string.settings_audio)) {
            val rate = synth.engine().sampleRate()
            val rateText = if (rate > 0) "$rate Hz" else stringResource(R.string.settings_engine_not_started)
            InfoRow(stringResource(R.string.settings_sample_rate, rateText))
            InfoRow(stringResource(R.string.settings_polyphony) + ": " + stringResource(R.string.settings_polyphony_voices, 16))
            InfoRow(
                if (started) stringResource(R.string.settings_engine_running)
                else stringResource(R.string.settings_engine_stopped),
            )
        }

        // MIDI
        SettingsSection(title = stringResource(R.string.settings_midi)) {
            if (devices.isEmpty()) {
                InfoRow(stringResource(R.string.midi_no_devices))
                Text(
                    stringResource(R.string.midi_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                devices.forEach { name ->
                    InfoRow("• $name")
                }
            }
        }

        // Keyboard Layout (panels + drag-and-drop)
        SettingsSection(title = stringResource(R.string.settings_keyboard_layout)) {
            Text(
                stringResource(
                    R.string.settings_keyboard_layout_summary,
                    keyboardLayout.name,
                    keyboardLayout.panels.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = { editingLayout = true }) {
                    Text(stringResource(R.string.settings_layout_edit))
                }
                OutlinedButton(onClick = { saveAsCurrent = true }) {
                    Text("Save as…")
                }
                TextButton(onClick = {
                    scope.launch { prefs.setKeyboardLayout(BuiltInLayouts.DEFAULT) }
                }) {
                    Text(stringResource(R.string.settings_layout_reset))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BuiltInLayouts.ALL.forEach { preset ->
                    val isActive = preset.name == keyboardLayout.name
                    AssistChip(
                        onClick = {
                            scope.launch { prefs.setKeyboardLayout(preset) }
                        },
                        label = {
                            val displayName = when (preset.name) {
                                "Default" -> stringResource(R.string.settings_layout_default)
                                "Thumb-Friendly" -> stringResource(R.string.settings_layout_thumb_friendly)
                                else -> preset.name
                            }
                            Text(displayName)
                        },
                        leadingIcon = if (isActive) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else null,
                    )
                }
            }
            if (userLayouts.isNotEmpty()) {
                Text(
                    "Saved layouts",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    userLayouts.forEach { layout ->
                        val isActive = layout.name == keyboardLayout.name
                        InputChip(
                            selected = isActive,
                            onClick = { scope.launch { layouts.apply(layout) } },
                            label = { Text(layout.name) },
                            leadingIcon = if (isActive) {
                                { Icon(Icons.Filled.Check, contentDescription = null) }
                            } else null,
                            trailingIcon = {
                                IconButton(onClick = { pendingDelete = layout.name }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Delete ${layout.name}")
                                }
                            },
                        )
                    }
                }
            }
        }

        // Hardware Keyboard (with editor)
        SettingsSection(title = stringResource(R.string.settings_keyboard)) {
            Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                KeymapEditor(
                    mapper = hwKeys,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // Theme
        SettingsSection(title = stringResource(R.string.settings_theme)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ThemeAccent.entries.forEach { t ->
                    AccentSwatch(
                        accent = t,
                        selected = t == accent,
                        onClick = {
                            scope.launch { prefs.setThemeAccent(t.name) }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Bug Report
        SettingsSection(title = stringResource(R.string.settings_bug_report)) {
            BugReportSection(synth = synth, midi = midi)
        }

        // About
        SettingsSection(title = stringResource(R.string.settings_about)) {
            InfoRow(stringResource(R.string.about_version, BuildConfig.VERSION_NAME))
            InfoRow(stringResource(R.string.about_source_attribution))
            InfoRow(stringResource(R.string.about_engine))
            InfoRow(stringResource(R.string.about_license))
        }
    }

    if (editingLayout) {
        Dialog(
            onDismissRequest = { editingLayout = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
            ),
        ) {
            KeyboardLayoutEditor(
                initial = keyboardLayout,
                onCancel = { editingLayout = false },
                onSave = { layout ->
                    scope.launch { prefs.setKeyboardLayout(layout) }
                    editingLayout = false
                },
                onSaveAs = { layout ->
                    scope.launch {
                        layouts.saveUser(layout)
                        layouts.apply(layout)
                    }
                    editingLayout = false
                },
                existingNames = userLayouts.map { it.name }.toSet(),
            )
        }
    }

    if (saveAsCurrent) {
        SaveCurrentLayoutDialog(
            initialName = if (keyboardLayout.builtin) "" else keyboardLayout.name,
            takenNames = userLayouts.map { it.name }.toSet() +
                BuiltInLayouts.ALL.map { it.name }.toSet(),
            onDismiss = { saveAsCurrent = false },
            onConfirm = { name ->
                val toSave = keyboardLayout.copy(name = name, builtin = false)
                scope.launch {
                    layouts.saveUser(toSave)
                    layouts.apply(toSave)
                }
                saveAsCurrent = false
            },
        )
    }

    pendingDelete?.let { name ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete layout") },
            text = { Text("Remove the saved layout \"$name\"?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { layouts.deleteUser(name) }
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SaveCurrentLayoutDialog(
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
        title = { Text("Save layout as") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Layout name") },
                    singleLine = true,
                )
                when {
                    collidesBuiltIn -> Text(
                        "That name is reserved for a built-in layout.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    trimmed in takenNames -> Text(
                        "A layout with this name already exists — saving will overwrite it.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(trimmed) }, enabled = isValid) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun InfoRow(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun AccentSwatch(
    accent: ThemeAccent,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ring = if (selected) MaterialTheme.colorScheme.primary
               else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(accent.gradientTop, accent.gradientBottom)))
                .border(BorderStroke(if (selected) 2.dp else 1.dp, ring), CircleShape),
        )
        Spacer(Modifier.height(2.dp))
        Text(
            accent.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
