package io.github.ranzlappen.synthpiano.ui.play

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import io.github.ranzlappen.synthpiano.audio.NoteSource
import io.github.ranzlappen.synthpiano.audio.SynthController
import io.github.ranzlappen.synthpiano.data.BuiltInLayouts
import io.github.ranzlappen.synthpiano.data.ChordInversion
import io.github.ranzlappen.synthpiano.data.ChordQuality
import io.github.ranzlappen.synthpiano.data.PreferencesRepository
import io.github.ranzlappen.synthpiano.data.applyInversion
import io.github.ranzlappen.synthpiano.data.buildChordIntervals
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The PERFORM tab: the workstation's primary play surface. Every visible
 * container — keyboard panels, the chord-modifier strip — is positioned
 * by the active [io.github.ranzlappen.synthpiano.data.KeyboardLayout],
 * editable from Settings → Keyboard Layout.
 */
@OptIn(FlowPreview::class)
@Composable
fun PerformTab(
    synth: SynthController,
    prefs: PreferencesRepository,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    val heldBySource by synth.heldBySource.collectAsState()
    val sticky by prefs.chordModSticky.collectAsState(initial = emptySet())
    val stickyInv by prefs.chordInvSticky.collectAsState(initial = ChordInversion.NONE)
    val zoom by prefs.pianoZoom.collectAsState(initial = 1.0f)
    val keyboardLayout by prefs.keyboardLayout.collectAsState(initial = BuiltInLayouts.DEFAULT)

    // One ScrollState per keyboard / modifier panel keyed by the panel's
    // stable UUID. Each state is seeded once from DataStore and writes back
    // its own debounced position so panels never share scroll.
    val keyboardScrollStates = remember { mutableStateMapOf<String, ScrollState>() }
    val modifierScrollStates = remember { mutableStateMapOf<String, ScrollState>() }

    LaunchedEffect(keyboardLayout) {
        val keyboardIds = keyboardLayout.panels.map { it.id }.toSet()
        val modifierIds = keyboardLayout.modifiers.map { it.id }.toSet()
        keyboardScrollStates.keys.retainAll(keyboardIds)
        modifierScrollStates.keys.retainAll(modifierIds)
        for (id in keyboardIds) {
            if (id !in keyboardScrollStates) {
                val seed = prefs.pianoScrollX(id).first().coerceAtLeast(0)
                keyboardScrollStates[id] = ScrollState(seed)
            }
        }
        for (id in modifierIds) {
            if (id !in modifierScrollStates) {
                val seed = prefs.modifierScrollY(id).first().coerceAtLeast(0)
                modifierScrollStates[id] = ScrollState(seed)
            }
        }
        prefs.pruneScrollKeys(keepKeyboardIds = keyboardIds, keepPadIds = modifierIds)
    }

    // Persist each panel's scroll position independently. Re-launches on
    // layout change because the state map's identity changes when entries
    // are added or removed.
    val keyboardStatesSnapshot = keyboardScrollStates.toMap()
    keyboardStatesSnapshot.forEach { (id, state) ->
        LaunchedEffect(id, state) {
            snapshotFlow { state.value }
                .drop(1)
                .distinctUntilChanged()
                .debounce(200)
                .collect { px -> prefs.setPianoScrollX(id, px) }
        }
    }
    val modifierStatesSnapshot = modifierScrollStates.toMap()
    modifierStatesSnapshot.forEach { (id, state) ->
        LaunchedEffect(id, state) {
            snapshotFlow { state.value }
                .drop(1)
                .distinctUntilChanged()
                .debounce(200)
                .collect { px -> prefs.setModifierScrollY(id, px) }
        }
    }

    // Stable per-id lookups passed down to KeyboardLayoutHost/PerformModifierStrip.
    // If a panel's state hasn't been seeded yet (first frame after a layout
    // swap) we hand back a transient ScrollState(0) so child composables
    // never see a null; the persisted seed will replace it next composition.
    val pianoScrollFor: (String) -> ScrollState = { id ->
        keyboardScrollStates.getOrPut(id) { ScrollState(0) }
    }
    val modifierScrollFor: (String) -> ScrollState = { id ->
        modifierScrollStates.getOrPut(id) { ScrollState(0) }
    }

    // Held (momentary) modifiers live only in memory; releasing the
    // SHIFT button or app exit clears them.
    var held by remember { mutableStateOf<Set<ChordQuality>>(emptySet()) }
    var heldInv by remember { mutableStateOf(ChordInversion.NONE) }

    // Map: piano-key MIDI root -> notes currently sounding for that root.
    // Snapshots the chord at note-on so that toggling sticky modifiers mid-
    // press doesn't re-voice the live chord; releasing the same root lets
    // us turn off the exact notes we started.
    val activeChordsByRoot = remember { mutableStateMapOf<Int, List<Int>>() }

    val onPianoNoteOn: (Int) -> Unit = { rootMidi ->
        val effectiveInv = if (heldInv != ChordInversion.NONE) heldInv else stickyInv
        val intervals = applyInversion(buildChordIntervals(sticky union held), effectiveInv)
        val notes = intervals
            .map { (rootMidi + it).coerceIn(0, 127) }
            .distinct()
        activeChordsByRoot[rootMidi] = notes
        notes.forEach { synth.noteOn(it, source = NoteSource.TOUCH) }
    }
    val onPianoNoteOff: (Int) -> Unit = { rootMidi ->
        (activeChordsByRoot.remove(rootMidi) ?: listOf(rootMidi))
            .forEach { synth.noteOff(it) }
    }

    KeyboardLayoutHost(
        layout = keyboardLayout,
        heldBySource = heldBySource,
        zoom = zoom,
        onNoteOn = onPianoNoteOn,
        onNoteOff = onPianoNoteOff,
        pianoScrollFor = pianoScrollFor,
        modifier = modifier.fillMaxSize(),
        modifierContent = { modPanel ->
            PerformModifierStrip(
                qualities = modPanel.qualities,
                inversions = modPanel.inversions,
                sticky = sticky,
                stickyInv = stickyInv,
                held = held,
                heldInv = heldInv,
                zoom = zoom,
                showLock = modPanel.showLock,
                showShift = modPanel.showShift,
                showZoom = modPanel.showZoom,
                verticalScrollState = modifierScrollFor(modPanel.id),
                onStickyToggle = { q ->
                    val next = if (q in sticky) sticky - q else sticky + q
                    scope.launch { prefs.setChordModSticky(next) }
                },
                onStickyInvToggle = { inv ->
                    val next = if (inv == stickyInv) ChordInversion.NONE else inv
                    scope.launch { prefs.setChordInvSticky(next) }
                },
                onShiftPress = { q -> held = held + q },
                onShiftRelease = { q -> held = held - q },
                onShiftInvPress = { inv -> heldInv = inv },
                onShiftInvRelease = { inv -> if (heldInv == inv) heldInv = ChordInversion.NONE },
                onZoomIn = {
                    val next = (zoom + 0.1f).coerceAtMost(ZOOM_MAX)
                    if (next != zoom) scope.launch { prefs.setPianoZoom(next) }
                },
                onZoomOut = {
                    val next = (zoom - 0.1f).coerceAtLeast(ZOOM_MIN)
                    if (next != zoom) scope.launch { prefs.setPianoZoom(next) }
                },
            )
        },
    )
}
