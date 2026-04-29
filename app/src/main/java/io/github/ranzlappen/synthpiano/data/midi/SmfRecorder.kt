package io.github.ranzlappen.synthpiano.data.midi

import android.util.Log
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
    private var collectJob: Job? = null
    private var startNanos: Long = 0L
    private var capture: MutableList<NoteCaptureEvent> = mutableListOf()
    private var currentPath: String? = null

    fun start(filePath: String) {
        if (collectJob != null) return
        currentPath = filePath
        // Fresh list per recording. The previous run's collector lambda
        // (already cancelled) is still bound to its own list, so any stray
        // event it emits during cancellation will land there, not here.
        val captureList = mutableListOf<NoteCaptureEvent>()
        capture = captureList
        startNanos = System.nanoTime()
        collectJob = scope.launch {
            synth.noteEvents.collect { ev ->
                synchronized(captureList) { captureList += ev }
            }
        }
    }

    /** Stop collecting, write the SMF file, and return its path (or null on failure / nothing recorded). */
    fun stop(title: String? = null): String? {
        val job = collectJob ?: return null
        job.cancel()
        collectJob = null
        val path = currentPath ?: return null
        currentPath = null

        val captureList = capture
        val snapshot: List<NoteCaptureEvent> = synchronized(captureList) { captureList.toList() }
        val score = buildScore(snapshot, title)
        return try {
            val out = File(path)
            out.parentFile?.mkdirs()
            out.writeBytes(SmfWriter.write(score))
            Log.i(TAG, "Wrote SMF: $path (${snapshot.size} events, ${score.notes.size} notes)")
            path
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to write SMF to $path", t)
            null
        }
    }

    private fun buildScore(events: List<NoteCaptureEvent>, title: String?): MidiScore {
        val tempoMap = mutableListOf(
            TempoEvent(0, MidiTiming.bpmToMicrosPerQuarter(recordingBpm)),
        )
        if (events.isEmpty()) {
            return MidiScore(
                ppq = MidiTiming.DEFAULT_PPQ,
                title = title,
                tempoMap = tempoMap,
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
        )
    }

    private data class PendingOn(val tick: Int, val velocity: Int)

    companion object {
        private const val TAG = "SmfRecorder"
    }
}
