package io.github.ranzlappen.synthpiano.data

import io.github.ranzlappen.synthpiano.audio.NoteSource
import io.github.ranzlappen.synthpiano.audio.SynthController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Plays a [Score] step-by-step against [SynthController] in real time.
 *
 * - Each step's notes go on for (durationBeats * 60 / tempo) ms, then off.
 * - Calling [stop] aborts immediately and lifts all notes.
 * - Position is exposed via [currentStep] for UI playback indicators.
 */
class ScorePlayer(
    private val scope: CoroutineScope,
    private val synth: SynthController,
) {
    private val _currentStep = MutableStateFlow(-1)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private var job: Job? = null

    fun start(score: Score, tempoBpm: Int = score.tempoBpm ?: 120) {
        stop()
        if (score.notes.isEmpty()) return
        val msPerBeat = 60_000L / tempoBpm.coerceIn(20, 300)
        _isPlaying.value = true
        job = scope.launch {
            try {
                for ((index, step) in score.notes.withIndex()) {
                    if (!isActive) break
                    _currentStep.value = index
                    val midiNotes = step.noteNames.mapNotNull(::noteNameToMidi)
                    midiNotes.forEach { synth.noteOn(it, source = NoteSource.SCORE) }
                    val durMs = (step.durationBeats * msPerBeat).toLong().coerceAtLeast(20L)
                    delay(durMs)
                    midiNotes.forEach { synth.noteOff(it) }
                }
            } finally {
                _currentStep.value = -1
                _isPlaying.value = false
                synth.allNotesOff()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        synth.allNotesOff()
        _currentStep.value = -1
        _isPlaying.value = false
    }
}
