package io.github.ranzlappen.synthpiano.ui.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.audio.SynthController
import io.github.ranzlappen.synthpiano.audio.WavRecorder
import io.github.ranzlappen.synthpiano.data.ChordPad
import io.github.ranzlappen.synthpiano.data.PreferencesRepository
import io.github.ranzlappen.synthpiano.data.defaultChordPads
import io.github.ranzlappen.synthpiano.data.parseChordPadsJson
import io.github.ranzlappen.synthpiano.data.toJson
import io.github.ranzlappen.synthpiano.ui.Tab
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun PlayScreen(
    synth: SynthController,
    prefs: PreferencesRepository,
    onNavigate: (Tab) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    val held by synth.heldNotes.collectAsState()
    val waveform by synth.waveform.collectAsState()
    val adsr by synth.adsr.collectAsState()
    val masterAmp by synth.masterAmp.collectAsState()
    val padsJson by prefs.chordPadsJson.collectAsState(initial = null)
    val octave by prefs.octave.collectAsState(initial = 0)

    // Keyboard zoom + scroll: live state, hoisted here so it's controlled
    // (PianoKeyboard is fully driven). Seeded once from DataStore on first
    // launch, then user gestures drive it and we save back to DataStore.
    var visibleKeys by remember { mutableFloatStateOf(14f) }
    var firstWhiteKey by remember { mutableFloatStateOf(0f) }
    var seededFromPrefs by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!seededFromPrefs) {
            val savedV = prefs.keyboardVisibleKeys.first()
            val savedF = prefs.keyboardFirstKey.first()
            visibleKeys = savedV.coerceIn(4f, 21f)
            firstWhiteKey = savedF.coerceAtLeast(0f)
            seededFromPrefs = true
        }
    }

    val pads = remember(padsJson) {
        padsJson?.let { runCatching { parseChordPadsJson(it) }.getOrNull() }
            ?: defaultChordPads()
    }

    val recorder = remember { WavRecorder(synth) }
    var isRecording by remember { mutableStateOf(false) }
    var lastRecordingPath by remember { mutableStateOf<String?>(null) }
    var showSynthSheet by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }

    // Persist live settings as the user adjusts them. Only fires on change.
    LaunchedEffect(waveform) { prefs.setWaveform(waveform) }
    LaunchedEffect(adsr) { prefs.setAdsr(adsr) }
    LaunchedEffect(masterAmp) { prefs.setMasterAmp(masterAmp) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar: octave, record, share
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Octave $octave",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { scope.launch { prefs.setOctave((octave - 1).coerceAtLeast(-3)) } }) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Octave −")
            }
            IconButton(onClick = { scope.launch { prefs.setOctave((octave + 1).coerceAtMost(3)) } }) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Octave +")
            }
            FilledIconButton(onClick = {
                if (!isRecording) {
                    val path = recorder.start(ctx)
                    if (path != null) {
                        isRecording = true
                        lastRecordingPath = path
                    }
                } else {
                    recorder.stop()
                    isRecording = false
                }
            }) {
                Icon(
                    imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                    contentDescription = if (isRecording) "Stop" else "Record",
                )
            }
            lastRecordingPath?.takeIf { !isRecording }?.let { path ->
                IconButton(onClick = { recorder.share(ctx, path) }) {
                    Icon(Icons.Filled.Share, contentDescription = "Share recording")
                }
            }
            IconButton(onClick = { showSynthSheet = true }) {
                Icon(Icons.Filled.Tune, contentDescription = "Synth parameters")
            }
            Box {
                IconButton(onClick = { showOverflow = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More")
                }
                DropdownMenu(
                    expanded = showOverflow,
                    onDismissRequest = { showOverflow = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Score") },
                        onClick = { showOverflow = false; onNavigate(Tab.Score) },
                    )
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = { showOverflow = false; onNavigate(Tab.Settings) },
                    )
                }
            }
        }

        if (showSynthSheet) {
            SynthParamsModal(
                waveform = waveform,
                onWaveform = synth::setWaveform,
                adsr = adsr,
                onAdsr = { synth.setAdsr(it.attackSec, it.decaySec, it.sustain, it.releaseSec) },
                masterAmp = masterAmp,
                onMasterAmp = synth::setMasterAmp,
                onDismiss = { showSynthSheet = false },
            )
        }

        // Chord pads (above keyboard)
        Box(modifier = Modifier.fillMaxWidth().height(56.dp)) {
            ChordPadsRow(
                pads = pads,
                onPadDown = { pad -> chordNotes(pad).forEach { n -> synth.noteOn(n + octave * 12) } },
                onPadUp = { pad -> chordNotes(pad).forEach { n -> synth.noteOff(n + octave * 12) } },
                onAssign = { i, newPad ->
                    val updated = pads.toMutableList().apply { this[i] = newPad }
                    scope.launch { prefs.setChordPadsJson(updated.toJson()) }
                },
            )
        }

        // The keyboard takes the remaining space. Inside a Column, fillMaxSize
        // does NOT claim leftover space — weight(1f) does. Floor at 120.dp so
        // even an extreme small landscape phone never collapses the keys.
        PianoKeyboard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = 120.dp),
            firstMidiNote = 48 + octave * 12,
            visibleKeys = visibleKeys,
            onVisibleKeysChange = { v ->
                visibleKeys = v
                scope.launch { prefs.setKeyboardVisibleKeys(v) }
            },
            firstWhiteKey = firstWhiteKey,
            onFirstWhiteKeyChange = { v ->
                firstWhiteKey = v
                scope.launch { prefs.setKeyboardFirstKey(v) }
            },
            heldNotes = held,
            onNoteOn = { synth.noteOn(it) },
            onNoteOff = { synth.noteOff(it) },
        )
    }
}

