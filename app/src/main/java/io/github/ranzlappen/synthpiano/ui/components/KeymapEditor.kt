package io.github.ranzlappen.synthpiano.ui.components

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.R
import io.github.ranzlappen.synthpiano.data.midiToNoteName
import io.github.ranzlappen.synthpiano.input.HwKeyboardMapper
import kotlinx.coroutines.launch

/**
 * Hardware-keyboard rebinding editor. Each row shows a current binding —
 * the human-readable key label, its MIDI offset relative to C4, and the
 * resulting note name. Tap a row to enter "press a key" capture mode;
 * the next hardware key press becomes the new binding.
 *
 * Uses [HwKeyboardMapper.setCaptureCallback] so the input dispatcher
 * suppresses notes while capturing and routes the keycode here.
 */
@Composable
fun KeymapEditor(
    mapper: HwKeyboardMapper,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var bindings by remember {
        mutableStateOf(
            mapper.bindingsSnapshot()
                .toList()
                .sortedBy { it.second }
        )
    }
    var captureFor by remember { mutableStateOf<Int?>(null) } // offset being assigned
    var addOffsetText by remember { mutableStateOf("") }
    var addingOffset by remember { mutableStateOf(false) }

    fun refresh() {
        bindings = mapper.bindingsSnapshot()
            .toList()
            .sortedBy { it.second }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.settings_remap_key),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(items = bindings, key = { it.first }) { (keyCode, offset) ->
                BindingRow(
                    keyCode = keyCode,
                    offset = offset,
                    onTap = { captureFor = offset },
                    onClear = {
                        mapper.clearBinding(keyCode)
                        scope.launch { mapper.saveCurrentBindings() }
                        refresh()
                    },
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(onClick = { addingOffset = true }) { Text("+ Bind new key") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                mapper.resetToDefaults()
                scope.launch { mapper.saveCurrentBindings() }
                refresh()
            }) {
                Text(stringResource(R.string.settings_reset_keys))
            }
        }
    }

    if (addingOffset) {
        AlertDialog(
            onDismissRequest = { addingOffset = false; addOffsetText = "" },
            title = { Text("Bind new key") },
            text = {
                Column {
                    Text("Enter a MIDI offset relative to C4 (e.g. 0 = C4, 7 = G4):", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.padding(4.dp))
                    OutlinedTextField(
                        value = addOffsetText,
                        onValueChange = { addOffsetText = it.filter { c -> c.isDigit() || c == '-' } },
                        label = { Text("Offset") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val v = addOffsetText.toIntOrNull()
                    if (v != null) {
                        captureFor = v
                    }
                    addingOffset = false
                    addOffsetText = ""
                }) { Text("Capture key") }
            },
            dismissButton = {
                TextButton(onClick = { addingOffset = false; addOffsetText = "" }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    val capturing = captureFor
    if (capturing != null) {
        DisposableEffect(capturing) {
            mapper.setCaptureCallback { keyCode ->
                mapper.setBinding(keyCode, capturing)
                scope.launch {
                    mapper.saveCurrentBindings()
                    refresh()
                }
                captureFor = null
            }
            onDispose { mapper.setCaptureCallback(null) }
        }

        AlertDialog(
            onDismissRequest = { captureFor = null },
            title = { Text(stringResource(R.string.settings_capture_prompt)) },
            text = {
                Text(
                    "Binding offset $capturing → ${midiToNoteName(60 + capturing)}",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(onClick = { captureFor = null }) {
                    Text(stringResource(R.string.settings_capture_cancel))
                }
            },
        )
    }
}

@Composable
private fun BindingRow(
    keyCode: Int,
    offset: Int,
    onTap: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                keyLabel(keyCode),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            "→ +$offset  (${midiToNoteName(60 + offset)})",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClear) {
            Text(stringResource(R.string.settings_clear_binding))
        }
    }
}

private fun keyLabel(keyCode: Int): String {
    val raw = KeyEvent.keyCodeToString(keyCode)
    return raw.removePrefix("KEYCODE_").replace('_', ' ')
}
