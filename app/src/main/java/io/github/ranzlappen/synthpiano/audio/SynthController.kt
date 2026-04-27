package io.github.ranzlappen.synthpiano.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Origin of a note event. Used by the piano view to color each pressed key
 * with a hue matching its source (touch=primary, MIDI=secondary,
 * score=tertiary, HW keyboard=accent).
 */
enum class NoteSource { TOUCH, MIDI, SCORE, HW_KEYBOARD }

/**
 * Application-scoped façade over [NativeSynth]. UI code interacts with
 * StateFlows; the native engine receives plain calls.
 *
 * Held notes are tracked here so we can issue "all notes off" cleanly,
 * coalesce multiple touches on the same key, and surface a held-keys list
 * to the keyboard view for highlighting. A second flow ([heldBySource])
 * carries which input device triggered each currently-held note.
 */
class SynthController(private val engine: NativeSynth) {

    private val _waveform = MutableStateFlow(Waveform.SINE)
    val waveform: StateFlow<Waveform> = _waveform.asStateFlow()

    private val _adsr = MutableStateFlow(Adsr())
    val adsr: StateFlow<Adsr> = _adsr.asStateFlow()

    private val _masterAmp = MutableStateFlow(0.7f)
    val masterAmp: StateFlow<Float> = _masterAmp.asStateFlow()

    private val _heldNotes = MutableStateFlow<Set<Int>>(emptySet())
    val heldNotes: StateFlow<Set<Int>> = _heldNotes.asStateFlow()

    private val _heldBySource = MutableStateFlow<Map<Int, NoteSource>>(emptyMap())
    val heldBySource: StateFlow<Map<Int, NoteSource>> = _heldBySource.asStateFlow()

    private val _started = MutableStateFlow(false)
    val started: StateFlow<Boolean> = _started.asStateFlow()

    fun start() {
        if (_started.value) return
        if (engine.start()) {
            _started.value = true
            engine.setWaveform(_waveform.value)
            with(_adsr.value) { engine.setAdsr(attackSec, decaySec, sustain, releaseSec) }
            engine.setMasterAmp(_masterAmp.value)
        }
    }

    fun stop() {
        if (!_started.value) return
        engine.allNotesOff()
        engine.stop()
        _heldNotes.value = emptySet()
        _heldBySource.value = emptyMap()
        _started.value = false
    }

    fun destroy() {
        engine.destroy()
        _started.value = false
    }

    fun noteOn(
        midiNote: Int,
        velocity: Float = 0.85f,
        source: NoteSource = NoteSource.TOUCH,
    ) {
        if (midiNote !in 0..127) return
        engine.noteOn(midiNote, velocity)
        _heldNotes.update { it + midiNote }
        _heldBySource.update { it + (midiNote to source) }
    }

    fun noteOff(midiNote: Int) {
        if (midiNote !in 0..127) return
        engine.noteOff(midiNote)
        _heldNotes.update { it - midiNote }
        _heldBySource.update { it - midiNote }
    }

    fun allNotesOff() {
        engine.allNotesOff()
        _heldNotes.value = emptySet()
        _heldBySource.value = emptyMap()
    }

    fun setWaveform(w: Waveform) {
        _waveform.value = w
        engine.setWaveform(w)
    }

    fun setAdsr(attackSec: Float, decaySec: Float, sustain: Float, releaseSec: Float) {
        val safe = Adsr(
            attackSec = attackSec.coerceIn(0.001f, 5.0f),
            decaySec = decaySec.coerceIn(0.001f, 5.0f),
            sustain = sustain.coerceIn(0f, 1f),
            releaseSec = releaseSec.coerceIn(0.001f, 5.0f),
        )
        _adsr.value = safe
        engine.setAdsr(safe.attackSec, safe.decaySec, safe.sustain, safe.releaseSec)
    }

    fun setMasterAmp(a: Float) {
        val safe = a.coerceIn(0f, 1f)
        _masterAmp.value = safe
        engine.setMasterAmp(safe)
    }

    fun engine(): NativeSynth = engine

    private inline fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
        value = transform(value)
    }
}

/** Plain ADSR carrier; seconds are real-time seconds. */
data class Adsr(
    val attackSec: Float = 0.005f,
    val decaySec: Float = 0.150f,
    val sustain: Float = 0.700f,
    val releaseSec: Float = 0.250f,
)
