package io.github.ranzlappen.synthpiano.audio

import io.github.ranzlappen.synthpiano.data.midi.ControlKind
import io.github.ranzlappen.synthpiano.data.midi.MidiScore
import io.github.ranzlappen.synthpiano.data.midi.MidiTiming
import io.github.ranzlappen.synthpiano.data.midi.TempoEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Plays a [MidiScore] against [SynthController] with tick-accurate,
 * tempo-aware timing.
 *
 * Schedules each Note On / Note Off at its absolute tick
 * (converted to wall-clock microseconds via the score's tempo map). It
 * supports overlapping notes, arbitrary durations, multi-channel
 * content, and mid-song tempo changes — all things the step grid can't
 * express.
 *
 * The audio thread is never touched; events go through
 * [SynthController.noteOn] / [SynthController.noteOff], i.e. the same
 * lock-free SPSC ring used by hardware MIDI input.
 */
class MidiScorePlayer(
    private val scope: CoroutineScope,
    private val synth: SynthController,
) {
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /** Last dispatched event's absolute tick, or -1 when stopped. */
    private val _currentTick = MutableStateFlow(-1)
    val currentTick: StateFlow<Int> = _currentTick.asStateFlow()

    private var job: Job? = null

    /**
     * Start playback. Cancels any in-progress run first.
     *
     * @param tempoOverrideBpm if non-null, ignore the score's tempo map
     *   and play at this fixed BPM. Used by the editor's "preview at
     *   custom tempo" affordance.
     */
    fun start(score: MidiScore, tempoOverrideBpm: Int? = null) {
        stop()
        if (score.notes.isEmpty() && score.controlEvents.isEmpty()) return

        val tempoMap = if (tempoOverrideBpm != null) {
            listOf(TempoEvent(0, MidiTiming.bpmToMicrosPerQuarter(tempoOverrideBpm)))
        } else {
            score.tempoMap.sortedBy { it.tick }.ifEmpty {
                listOf(TempoEvent(0, MidiTiming.DEFAULT_MICROS_PER_QUARTER))
            }
        }

        val events = buildTimeline(score, tempoMap)
        if (events.isEmpty()) return

        _isPlaying.value = true
        job = scope.launch {
            val startNanos = System.nanoTime()
            try {
                for (ev in events) {
                    if (!isActive) break
                    val targetNanos = startNanos + ev.micros * 1_000L
                    val sleepMs = (targetNanos - System.nanoTime()) / 1_000_000L
                    if (sleepMs > 0) delay(sleepMs)
                    when (ev.kind) {
                        EventKind.NoteOn -> synth.noteOn(
                            ev.midi,
                            velocity = ev.value / 127f,
                            source = NoteSource.SCORE,
                        )
                        EventKind.NoteOff -> synth.noteOff(ev.midi)
                        EventKind.Sustain -> synth.setSustainPedal(
                            down = ev.value >= 64,
                            source = NoteSource.SCORE,
                        )
                        EventKind.Expression -> synth.setExpression(
                            value = ev.value / 127f,
                            source = NoteSource.SCORE,
                        )
                        EventKind.Pressure -> synth.setChannelPressure(
                            value = ev.value / 127f,
                            source = NoteSource.SCORE,
                        )
                    }
                    _currentTick.value = ev.tick
                }
            } finally {
                _currentTick.value = -1
                _isPlaying.value = false
                synth.allNotesOff()
                // Reset modulator state so leaving the editor doesn't strand
                // a pedal-held or expression-faded engine.
                synth.setSustainPedal(false, NoteSource.SCORE)
                synth.setExpression(1f, NoteSource.SCORE)
                synth.setChannelPressure(0f, NoteSource.SCORE)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        synth.allNotesOff()
        synth.setSustainPedal(false, NoteSource.SCORE)
        synth.setExpression(1f, NoteSource.SCORE)
        synth.setChannelPressure(0f, NoteSource.SCORE)
        _currentTick.value = -1
        _isPlaying.value = false
    }

    // ────────────────────────────────────────────────────────────────────

    private enum class EventKind { NoteOn, NoteOff, Sustain, Expression, Pressure }

    private data class TimedEvent(
        val tick: Int,
        val micros: Long,
        val kind: EventKind,
        val midi: Int,
        val value: Int,
    )

    private fun buildTimeline(score: MidiScore, tempoMap: List<TempoEvent>): List<TimedEvent> {
        val out = ArrayList<TimedEvent>(score.notes.size * 2 + score.controlEvents.size)
        for (n in score.notes) {
            out += TimedEvent(
                tick = n.startTicks.coerceAtLeast(0),
                micros = tickToMicros(n.startTicks, tempoMap, score.ppq),
                kind = EventKind.NoteOn,
                midi = n.midi,
                value = n.velocity.coerceIn(1, 127),
            )
            val endTick = n.startTicks + n.durationTicks
            out += TimedEvent(
                tick = endTick,
                micros = tickToMicros(endTick, tempoMap, score.ppq),
                kind = EventKind.NoteOff,
                midi = n.midi,
                value = 0,
            )
        }
        for (c in score.controlEvents) {
            val kind = when (c.kind) {
                ControlKind.SUSTAIN -> EventKind.Sustain
                ControlKind.EXPRESSION -> EventKind.Expression
                ControlKind.CHANNEL_PRESSURE -> EventKind.Pressure
            }
            out += TimedEvent(
                tick = c.tick.coerceAtLeast(0),
                micros = tickToMicros(c.tick, tempoMap, score.ppq),
                kind = kind,
                midi = -1,
                value = c.value.coerceIn(0, 127),
            )
        }
        // Stable order at the same micro tick:
        //   0 = NoteOff (release outgoing notes first)
        //   1 = Sustain / Expression / Pressure (apply modulator state before
        //       the next NoteOn so a re-pedal lands BEFORE retriggering)
        //   2 = NoteOn  (start incoming notes)
        // Without this, a sustain-off at the same tick as a re-pedal could
        // be applied after the down, leaving voices unsustained.
        out.sortWith(
            compareBy(
                { it.micros },
                {
                    when (it.kind) {
                        EventKind.NoteOff -> 0
                        EventKind.Sustain, EventKind.Expression, EventKind.Pressure -> 1
                        EventKind.NoteOn -> 2
                    }
                },
            ),
        )
        return out
    }

    private fun tickToMicros(tick: Int, tempoMap: List<TempoEvent>, ppq: Int): Long =
        tickToMicrosInternal(tick, tempoMap, ppq)
}

/**
 * Convert an absolute tick to absolute microseconds since song start
 * given a sorted tempo map. Each tempo segment runs at a constant
 * µs/quarter; cumulative time accumulates across segments. Exposed
 * `internal` for unit testing.
 */
internal fun tickToMicrosInternal(tick: Int, tempoMap: List<TempoEvent>, ppq: Int): Long {
    if (ppq <= 0) return 0L
    val t = tick.coerceAtLeast(0)
    var cum = 0L
    for (i in tempoMap.indices) {
        val seg = tempoMap[i]
        val nextTick = if (i + 1 < tempoMap.size) tempoMap[i + 1].tick else Int.MAX_VALUE
        if (t < nextTick) {
            cum += (t - seg.tick).toLong() * seg.microsPerQuarter / ppq
            return cum
        }
        cum += (nextTick - seg.tick).toLong() * seg.microsPerQuarter / ppq
    }
    return cum
}
