package io.github.ranzlappen.synthpiano.data.midi

import android.util.Log
import io.github.ranzlappen.synthpiano.audio.ControlCaptureEvent
import io.github.ranzlappen.synthpiano.audio.NoteCaptureEvent
import io.github.ranzlappen.synthpiano.audio.SynthController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/**
 * Captures live note events from [SynthController.noteEvents] for the
 * duration of a recording, then on [stop] serialises them directly to a
 * Standard MIDI File (`.mid`) on disk. Operates entirely off the audio
 * thread.
 *
 * Replaces the legacy `ScoreRecorder`, which produced a quantised JSON
 * score. This recorder preserves microsecond timing and per-note
 * velocity — a `.mid` file written here can be re-imported losslessly
 * into the editor (modulo the simple fixed-tempo timing model we use).
 *
 * Timing model: real-time elapsed → ticks at [recordingBpm], PPQ
 * [MidiTiming.DEFAULT_PPQ]. Tempo defaults to 120 BPM but can be set
 * per-recording (e.g. from a metronome BPM). Note On / Note Off pairs
 * match by FIFO on (channel, midi), so overlapping retriggers of the
 * same key round-trip correctly.
 *
 * All captured sources (touch, hardware keyboard, MIDI input, score
 * playback) are written to the same channel 0 in v1 — the file format
 * supports per-source channel mapping but the recorder doesn't yet
 * expose that.
 */
class SmfRecorder(
    private val synth: SynthController,
    private val scope: CoroutineScope,
    private val recordingBpm: Int = MidiTiming.DEFAULT_BPM,
) {
    private var noteJob: Job? = null
    private var controlJob: Job? = null
    private var startNanos: Long = 0L
    private var capture: MutableList<NoteCaptureEvent> = mutableListOf()
    private var controlCapture: MutableList<ControlCaptureEvent> = mutableListOf()
    private var currentPath: String? = null

    fun start(filePath: String) {
        if (noteJob != null) return
        currentPath = filePath
        // Fresh lists per recording. Previous runs' collector lambdas
        // (already cancelled) are still bound to their own lists, so any
        // stray event they emit during cancellation will land there, not here.
        val captureList = mutableListOf<NoteCaptureEvent>()
        capture = captureList
        val controlList = mutableListOf<ControlCaptureEvent>()
        controlCapture = controlList
        startNanos = System.nanoTime()
        noteJob = scope.launch {
            synth.noteEvents.collect { ev ->
                synchronized(captureList) { captureList += ev }
            }
        }
        controlJob = scope.launch {
            synth.controlEvents.collect { ev ->
                synchronized(controlList) { controlList += ev }
            }
        }
    }

    /** Stop collecting, write the SMF file, and return its path (or null on failure / nothing recorded). */
    fun stop(title: String? = null): String? {
        val nJob = noteJob ?: return null
        nJob.cancel()
        noteJob = null
        controlJob?.cancel()
        controlJob = null
        val path = currentPath ?: return null
        currentPath = null

        val captureList = capture
        val snapshot: List<NoteCaptureEvent> = synchronized(captureList) { captureList.toList() }
        val controlList = controlCapture
        val controlSnapshot: List<ControlCaptureEvent> = synchronized(controlList) { controlList.toList() }
        // Belt-and-braces: if the recording ended while a pedal was held the
        // engine will keep voices sustained until something releases it.
        // Force pedal-off here so we leave the engine in a clean state.
        synth.setSustainPedal(false)
        val score = buildScore(snapshot, controlSnapshot, title)
        return try {
            val out = File(path)
            out.parentFile?.mkdirs()
            out.writeBytes(SmfWriter.write(score))
            Log.i(
                TAG,
                "Wrote SMF: $path (${snapshot.size} note events, ${controlSnapshot.size} control events, ${score.notes.size} notes)",
            )
            path
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to write SMF to $path", t)
            null
        }
    }

    private fun buildScore(
        events: List<NoteCaptureEvent>,
        controlEvents: List<ControlCaptureEvent>,
        title: String?,
    ): MidiScore {
        val tempoMap = mutableListOf(
            TempoEvent(0, MidiTiming.bpmToMicrosPerQuarter(recordingBpm)),
        )
        val controls = buildControlEvents(controlEvents)
        if (events.isEmpty()) {
            return MidiScore(
                ppq = MidiTiming.DEFAULT_PPQ,
                title = title,
                tempoMap = tempoMap,
                controlEvents = controls,
            )
        }

        val sorted = events.sortedBy { it.tNanos }
        // FIFO queue keyed by (channel, midi) — channel always 0 in v1.
        val pending = HashMap<Int, ArrayDeque<PendingOn>>()
        val notes = mutableListOf<Note>()

        for (ev in sorted) {
            val microsRel = (ev.tNanos - startNanos) / 1_000L
            val tick = MidiTiming.microsToTicks(microsRel, recordingBpm, MidiTiming.DEFAULT_PPQ)
            val key = ev.midi and 0xFF
            if (ev.on) {
                val velocityByte = (ev.velocity.coerceIn(0f, 1f) * 127f).toInt().coerceIn(1, 127)
                pending.getOrPut(key) { ArrayDeque() }.addLast(PendingOn(tick, velocityByte))
            } else {
                val on = pending[key]?.removeFirstOrNull() ?: continue
                val dur = (tick - on.tick).coerceAtLeast(1)
                notes += Note(
                    channel = 0,
                    midi = ev.midi,
                    velocity = on.velocity,
                    startTicks = on.tick,
                    durationTicks = dur,
                )
            }
        }

        // Close any still-held notes at the end of the recording (clean shutdown
        // semantics — release at the timestamp of the last event).
        if (pending.values.any { it.isNotEmpty() }) {
            val lastTick = MidiTiming.microsToTicks(
                (sorted.last().tNanos - startNanos) / 1_000L,
                recordingBpm,
                MidiTiming.DEFAULT_PPQ,
            )
            for ((key, queue) in pending) {
                val midi = key
                while (queue.isNotEmpty()) {
                    val on = queue.removeFirst()
                    val dur = (lastTick - on.tick).coerceAtLeast(1)
                    notes += Note(
                        channel = 0,
                        midi = midi,
                        velocity = on.velocity,
                        startTicks = on.tick,
                        durationTicks = dur,
                    )
                }
            }
        }

        notes.sortWith(compareBy({ it.startTicks }, { it.midi }))

        return MidiScore(
            ppq = MidiTiming.DEFAULT_PPQ,
            title = title,
            tempoMap = tempoMap,
            notes = notes,
            controlEvents = controls,
        )
    }

    private fun buildControlEvents(events: List<ControlCaptureEvent>): MutableList<ControlEvent> {
        if (events.isEmpty()) return mutableListOf()
        val out = mutableListOf<ControlEvent>()
        for (ev in events.sortedBy { it.tNanos }) {
            val microsRel = (ev.tNanos - startNanos) / 1_000L
            val tick = MidiTiming.microsToTicks(microsRel, recordingBpm, MidiTiming.DEFAULT_PPQ)
                .coerceAtLeast(0)
            val value = when (ev.kind) {
                ControlKind.SUSTAIN -> if (ev.value >= 0.5f) 127 else 0
                else -> (ev.value.coerceIn(0f, 1f) * 127f).toInt().coerceIn(0, 127)
            }
            out += ControlEvent(tick = tick, channel = 0, kind = ev.kind, value = value)
        }
        return out
    }

    private data class PendingOn(val tick: Int, val velocity: Int)

    companion object {
        private const val TAG = "SmfRecorder"
    }
}
