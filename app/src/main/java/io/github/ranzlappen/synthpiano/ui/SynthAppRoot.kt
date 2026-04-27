package io.github.ranzlappen.synthpiano.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.R
import io.github.ranzlappen.synthpiano.audio.RecordingSession
import io.github.ranzlappen.synthpiano.audio.SynthController
import io.github.ranzlappen.synthpiano.audio.WavRecorder
import io.github.ranzlappen.synthpiano.data.PreferencesRepository
import io.github.ranzlappen.synthpiano.input.HwKeyboardMapper
import io.github.ranzlappen.synthpiano.midi.MidiManager
import io.github.ranzlappen.synthpiano.ui.components.AppGradientBackground
import io.github.ranzlappen.synthpiano.ui.components.HeaderStrip
import io.github.ranzlappen.synthpiano.ui.play.PerformTab
import io.github.ranzlappen.synthpiano.ui.score.ScoreScreen
import io.github.ranzlappen.synthpiano.ui.settings.SettingsScreen
import io.github.ranzlappen.synthpiano.ui.sound.SoundTab

private enum class Tab(val titleRes: Int) {
    Perform(R.string.nav_perform),
    Sound(R.string.nav_sound),
    Compose(R.string.nav_compose),
    Setup(R.string.nav_setup),
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SynthAppRoot(
    synth: SynthController,
    prefs: PreferencesRepository,
    midi: MidiManager,
    hwKeys: HwKeyboardMapper,
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    var tab by rememberSaveable { mutableStateOf(Tab.Perform) }

    val masterAmp by synth.masterAmp.collectAsState()
    val midiDevices by midi.connectedDeviceNames.collectAsState()

    val recorder = remember { WavRecorder(synth) }
    val session = remember { RecordingSession(recorder, scope) }
    val isRecording by session.isRecording.collectAsState()
    val elapsed by session.elapsedMs.collectAsState()
    val lastPath by session.lastPath.collectAsState()

    var midiSheetOpen by remember { mutableStateOf(false) }

    val widthDp = LocalConfiguration.current.screenWidthDp
    val railWide = widthDp >= 840

    AppGradientBackground {
        Column(modifier = Modifier.fillMaxSize()) {
            HeaderStrip(
                synth = synth,
                masterAmp = masterAmp,
                onMasterAmp = synth::setMasterAmp,
                midiDevices = midiDevices,
                onMidiClick = { midiSheetOpen = true },
                isRecording = isRecording,
                elapsedMs = elapsed,
                hasLastRecording = lastPath != null,
                onRecordToggle = { session.toggle(ctx) },
                onShareLast = { session.shareLast(ctx) },
                appName = stringResource(R.string.app_name),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))

            Row(modifier = Modifier.fillMaxSize()) {
                AppRail(
                    selected = tab,
                    onSelect = { tab = it },
                    wide = railWide,
                )
                VerticalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    when (tab) {
                        Tab.Perform -> PerformTab(synth = synth, prefs = prefs)
                        Tab.Sound -> SoundTab(synth = synth, prefs = prefs)
                        Tab.Compose -> ScoreScreen(synth = synth, prefs = prefs)
                        Tab.Setup -> SettingsScreen(synth = synth, prefs = prefs, midi = midi)
                    }
                }
            }
        }

        if (midiSheetOpen) {
            ModalBottomSheet(onDismissRequest = { midiSheetOpen = false }) {
                MidiDeviceSheetContent(deviceNames = midiDevices)
            }
        }
    }
}

@Composable
private fun AppRail(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    wide: Boolean,
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
        modifier = Modifier.width(if (wide) 96.dp else 72.dp),
    ) {
        Tab.entries.forEach { t ->
            val (icon, contentDesc) = when (t) {
                Tab.Perform -> Icons.Filled.Piano to "Perform"
                Tab.Sound -> Icons.Filled.Tune to "Sound"
                Tab.Compose -> Icons.Filled.LibraryMusic to "Compose"
                Tab.Setup -> Icons.Filled.Settings to "Setup"
            }
            NavigationRailItem(
                selected = t == selected,
                onClick = { onSelect(t) },
                icon = { Icon(icon, contentDescription = contentDesc) },
                label = if (wide) { @Composable { Text(stringResource(t.titleRes)) } } else null,
                alwaysShowLabel = wide,
            )
        }
    }
}

@Composable
private fun MidiDeviceSheetContent(deviceNames: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("MIDI Devices", style = MaterialTheme.typography.titleLarge)
        if (deviceNames.isEmpty()) {
            Text(
                stringResource(R.string.midi_no_devices),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                stringResource(R.string.midi_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            deviceNames.forEach { name ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.Equalizer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(name, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

