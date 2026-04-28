package io.github.ranzlappen.synthpiano.audio

import android.util.Log
import io.github.ranzlappen.synthpiano.data.Score
import io.github.ranzlappen.synthpiano.data.ScoreStep
import io.github.ranzlappen.synthpiano.data.midiToNoteName
import io.github.ranzlappen.synthpiano.data.toJsonString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Captures note events from [SynthController.noteEvents] for the duration of
 * a recording, then on [stop] converts them into a [Score] and writes it as
 * JSON next to the WAV file. Operates entirely off the audio thread.
 *
 * The on-disk format matches the canonical Composer-loadable shape produced
 * by [Score.toJsonString]:
 *
 *   {
 *     "version": 1,
 *     "title": "Recording <ts>",
 *     "tempo": 120,
 *     "notes": [ {"note": "C4", "duration": 0.5}, ... ]
 *   }
 *
 * Timing model: real-time elapsed → beats at [TEMPO_BPM]. Note-ons within
 * [CHORD_WINDOW_MS] of each other merge into a single chord step. Gaps
 * longer than [REST_THRESHOLD_MS] become rest steps. Durations quantize to
 * 1/16 of a beat with a minimum of 1/16.
 */
class ScoreRecorder(
    private val synth: SynthController,
    private val scope: CoroutineScope,
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

    /** Stop collecting, write the JSON file, and return its path (or null on failure / nothing recorded). */
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
            out.writeText(score.toJsonString(prettyPrint = true))
            Log.i(TAG, "Wrote score JSON: $path (${snapshot.size} events, ${score.notes.size} steps)")
            path
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to write score JSON to $path", t)
            null
        }
    }

    private fun buildScore(events: List<NoteCaptureEvent>, title: String?): Score {
        val steps = mutableListOf<ScoreStep>()
        if (events.isEmpty()) {
            return Score(notes = emptyList(), title = title, tempoBpm = TEMPO_BPM)
        }

        // Walk events chronologically. A "chord" is a set of midi notes that
        // share a noteOn within a small window. The chord ends when the next
        // chord starts (a noteOn outside the window) — its duration is the
        // gap until that next start, OR if no further notes, the time until
        // the *last* of its own notes turned off.
        val sorted = events.sortedBy { it.tNanos }

        // Pull out all noteOns with their times (relative to start).
        data class OnEvent(val midi: Int, val tMs: Long)
        val onsByTime = sorted.filter { it.on }.map { OnEvent(it.midi, nanosToMs(it.tNanos)) }
        if (onsByTime.isEmpty()) {
            return Score(notes = emptyList(), title = title, tempoBpm = TEMPO_BPM)
        }

        // Build chord groups by chord-window proximity.
        data class Chord(val startMs: Long, val notes: MutableList<Int>)
        val chords = mutableListOf<Chord>()
        for (on in onsByTime) {
            val cur = chords.lastOrNull()
            if (cur != null && on.tMs - cur.startMs <= CHORD_WINDOW_MS) {
                if (on.midi !in cur.notes) cur.notes += on.midi
            } else {
                chords += Chord(on.tMs, mutableListOf(on.midi))
            }
        }

        // For determining the trailing chord's duration, find the last
        // noteOff time across all events.
        val lastOffMs: Long = sorted.lastOrNull { !it.on }
            ?.let { nanosToMs(it.tNanos) }
            ?: chords.last().startMs

        var prevEndMs: Long = 0L
        for ((idx, chord) in chords.withIndex()) {
            // Insert a rest if there's a gap between previous step end and this chord start.
            val gap = chord.startMs - prevEndMs
            if (gap > REST_THRESHOLD_MS) {
                val restBeats = quantize(msToBeats(gap))
                if (restBeats > 0f) {
                    steps += ScoreStep(noteNames = emptyList(), durationBeats = restBeats)
                }
            }

            val endMs: Long = if (idx + 1 < chords.size) chords[idx + 1].startMs else max(lastOffMs, chord.startMs + 1)
            val durMs: Long = max(1L, endMs - chord.startMs)
            val durBeats = quantize(msToBeats(durMs))
            steps += ScoreStep(
                noteNames = chord.notes.sorted().map(::midiToNoteName),
                durationBeats = durBeats,
            )
            prevEndMs = endMs
        }

        return Score(notes = steps, title = title, tempoBpm = TEMPO_BPM)
    }

    private fun nanosToMs(nanos: Long): Long = (nanos - startNanos) / 1_000_000L

    private fun msToBeats(ms: Long): Float = (ms / 1000f) * (TEMPO_BPM / 60f)

    private fun quantize(beats: Float): Float {
        val sixteenths = (beats * QUANTIZE_DIV).roundToInt()
        val clamped = max(1, sixteenths)
        return clamped / QUANTIZE_DIV.toFloat()
    }

    companion object {
        private const val TAG = "ScoreRecorder"
        const val TEMPO_BPM: Int = 120
        const val CHORD_WINDOW_MS: Long = 40L
        const val REST_THRESHOLD_MS: Long = 60L
        const val QUANTIZE_DIV: Int = 16  // 1/16 beat
    }
}
