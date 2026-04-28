package io.github.ranzlappen.synthpiano.audio

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Origin of a note event. Used by the piano view to color each pressed key
 * with a hue matching its source (touch=primary, MIDI=secondary,
 * score=tertiary, HW keyboard=accent).
 */
enum class NoteSource { TOUCH, MIDI, SCORE, HW_KEYBOARD }

/**
 * Single note-on / note-off event tagged with a monotonic timestamp. Emitted
 * from [SynthController.noteEvents] for non-audio-thread observers
 * (recorder, MIDI thru, etc.).
 */
data class NoteCaptureEvent(
    val midi: Int,
    val velocity: Float,
    val source: NoteSource,
    val on: Boolean,
    val tNanos: Long,
)

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

    private val _filter = MutableStateFlow(FilterSettings())
    val filter: StateFlow<FilterSettings> = _filter.asStateFlow()

    private val _voiceShaping = MutableStateFlow(VoiceShaping())
    val voiceShaping: StateFlow<VoiceShaping> = _voiceShaping.asStateFlow()

    private val _polyComp = MutableStateFlow(1f)
    val polyComp: StateFlow<Float> = _polyComp.asStateFlow()

    private val _heldNotes = MutableStateFlow<Set<Int>>(emptySet())
    val heldNotes: StateFlow<Set<Int>> = _heldNotes.asStateFlow()

    private val _heldBySource = MutableStateFlow<Map<Int, NoteSource>>(emptyMap())
    val heldBySource: StateFlow<Map<Int, NoteSource>> = _heldBySource.asStateFlow()

    private val _started = MutableStateFlow(false)
    val started: StateFlow<Boolean> = _started.asStateFlow()

    private val _noteEvents = MutableSharedFlow<NoteCaptureEvent>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val noteEvents: SharedFlow<NoteCaptureEvent> = _noteEvents.asSharedFlow()

    fun start() {
        if (_started.value) return
        if (engine.start()) {
            _started.value = true
            engine.setWaveform(_waveform.value)
            with(_adsr.value) {
                engine.setAdsr(attackSec, decaySec, sustain, releaseSec)
                engine.setEnvelopeCurve(curve)
            }
            engine.setMasterAmp(_masterAmp.value)
            with(_filter.value) { engine.setFilter(cutoffHz, resonance) }
            with(_voiceShaping.value) {
                engine.setVelocitySensitivity(velocitySensitivity)
                engine.setGlideSec(glideSec)
            }
            engine.setPolyCompensation(_polyComp.value)
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
        _noteEvents.tryEmit(
            NoteCaptureEvent(midiNote, velocity, source, on = true, tNanos = System.nanoTime()),
        )
    }

    fun noteOff(midiNote: Int) {
        if (midiNote !in 0..127) return
        engine.noteOff(midiNote)
        _heldNotes.update { it - midiNote }
        _heldBySource.update { it - midiNote }
        _noteEvents.tryEmit(
            NoteCaptureEvent(midiNote, 0f, NoteSource.TOUCH, on = false, tNanos = System.nanoTime()),
        )
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

    fun setAdsr(
        attackSec: Float,
        decaySec: Float,
        sustain: Float,
        releaseSec: Float,
        curve: Float = _adsr.value.curve,
    ) {
        val safe = Adsr(
            attackSec = attackSec.coerceIn(0.001f, 5.0f),
            decaySec = decaySec.coerceIn(0.001f, 5.0f),
            sustain = sustain.coerceIn(0f, 1f),
            releaseSec = releaseSec.coerceIn(0.001f, 5.0f),
            curve = curve.coerceIn(-1f, 1f),
        )
        _adsr.value = safe
        engine.setAdsr(safe.attackSec, safe.decaySec, safe.sustain, safe.releaseSec)
        engine.setEnvelopeCurve(safe.curve)
    }

    fun setMasterAmp(a: Float) {
        val safe = a.coerceIn(0f, 1f)
        _masterAmp.value = safe
        engine.setMasterAmp(safe)
    }

    fun setFilter(cutoffHz: Float, resonance: Float) {
        val safe = FilterSettings(
            cutoffHz = cutoffHz.coerceIn(20f, 20000f),
            resonance = resonance.coerceIn(0f, 1f),
        )
        _filter.value = safe
        engine.setFilter(safe.cutoffHz, safe.resonance)
    }

    fun setVelocitySensitivity(v: Float) {
        val safe = v.coerceIn(0f, 1f)
        _voiceShaping.value = _voiceShaping.value.copy(velocitySensitivity = safe)
        engine.setVelocitySensitivity(safe)
    }

    fun setGlideSec(s: Float) {
        val safe = s.coerceIn(0f, 0.5f)
        _voiceShaping.value = _voiceShaping.value.copy(glideSec = safe)
        engine.setGlideSec(safe)
    }

    fun setPolyCompensation(v: Float) {
        val safe = v.coerceIn(0f, 1f)
        _polyComp.value = safe
        engine.setPolyCompensation(safe)
    }

    fun engine(): NativeSynth = engine

    private inline fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
        value = transform(value)
    }
}

/** Plain ADSR carrier; seconds are real-time seconds. [curve] in [-1, +1]. */
data class Adsr(
    val attackSec: Float = 0.005f,
    val decaySec: Float = 0.150f,
    val sustain: Float = 0.700f,
    val releaseSec: Float = 0.250f,
    val curve: Float = 0f,
)

/** State-variable low-pass filter settings. */
data class FilterSettings(
    val cutoffHz: Float = 18000f,
    val resonance: Float = 0f,
)

/** Per-voice shaping that isn't part of the envelope or filter. */
data class VoiceShaping(
    val velocitySensitivity: Float = 1f,
    val glideSec: Float = 0f,
)
