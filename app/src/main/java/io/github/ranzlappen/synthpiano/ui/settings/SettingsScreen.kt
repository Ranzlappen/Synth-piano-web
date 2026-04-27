package io.github.ranzlappen.synthpiano.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.audio.SynthController
import io.github.ranzlappen.synthpiano.data.PreferencesRepository
import io.github.ranzlappen.synthpiano.midi.MidiManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    synth: SynthController,
    prefs: PreferencesRepository,
    midi: MidiManager,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val devices by midi.connectedDeviceNames.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to play",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        // Audio section
        SettingsCard(title = "Audio") {
            val rate = synth.engine().sampleRate()
            val started by synth.started.collectAsState()
            val rateText = if (rate > 0) "$rate Hz" else "engine not started"
            Text("Sample rate: $rateText", style = MaterialTheme.typography.bodyLarge)
            Text("Polyphony: 16 voices", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (started) "Engine: running" else "Engine: stopped",
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        // MIDI section
        SettingsCard(title = "MIDI") {
            if (devices.isEmpty()) {
                Text("No MIDI devices connected", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Plug a USB-MIDI controller via OTG. Bluetooth MIDI is not supported in this build.",
                    style = MaterialTheme.typography.labelMedium,
                )
            } else {
                devices.forEach { name ->
                    Text("• $name", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        // Hardware keyboard
        SettingsCard(title = "Hardware Keyboard") {
            Text(
                "Defaults match the Python source: ASDFGHJKL play C-major white keys, " +
                    "WERTYUOP black keys.",
                style = MaterialTheme.typography.bodyLarge,
            )
            TextButton(onClick = {
                scope.launch { prefs.setKeymapJson("") }
            }) {
                Text("Reset keymap to defaults")
            }
        }

        // About
        SettingsCard(title = "About") {
            Text("Synth Piano (Android)", style = MaterialTheme.typography.titleMedium)
            Text("Port of Ranzlappen/synth-piano (Python)", style = MaterialTheme.typography.bodyLarge)
            Text("Audio engine: Google Oboe", style = MaterialTheme.typography.bodyLarge)
            Text("MIT License", style = MaterialTheme.typography.bodyLarge)
        }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            Spacer(Modifier.height(2.dp))
            content()
        }
    }
}
