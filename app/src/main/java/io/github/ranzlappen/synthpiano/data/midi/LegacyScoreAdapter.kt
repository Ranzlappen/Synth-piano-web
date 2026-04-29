package io.github.ranzlappen.synthpiano.data.midi

import io.github.ranzlappen.synthpiano.data.Score
import io.github.ranzlappen.synthpiano.data.ScoreStep
import io.github.ranzlappen.synthpiano.data.midiToNoteName
import io.github.ranzlappen.synthpiano.data.noteNameToMidi
import io.github.ranzlappen.synthpiano.data.parseScoreJson
import kotlin.math.max

/**
 * Bridges between the legacy step-based [Score] (still used by the current
 * Composer UI and by the bundled JSON demos) and the new event-based
 * [MidiScore].
 *
 * These adapters are temporary scaffolding for the transition window
 * between Batch 2 (this batch — wires `MidiScore` into the persistence
 * layer) and Batch 4 (replaces the step-grid UI with a piano-roll editor
 * that mutates `MidiScore` directly). At that point the legacy [Score]
 * type, [parseScoreJson], and this file all go away.
 *
 * Conversion is lossy in both directions:
 * - [Score] → [MidiScore]: every step becomes simultaneous Note events on
 *   channel 0 at default velocity 100. No timing nuance to lose.
 * - [MidiScore] → [Score]: notes are quantised onto a 1/16-beat grid and
 *   merged into chord steps when their starts fall within a small window.
 *   Multi-channel content collapses; per-note velocity is dropped. Use
 *   only for read/playback by the legacy step-grid composer.
 */

private const val LEGACY_VELOCITY: Int = 100
private const val LEGACY_CHANNEL: Int = 0

/** Parse legacy JSON score text directly into the new [MidiScore] model. */
fun importLegacyJsonAsMidiScore(text: String): MidiScore {
    val score = parseScoreJson(text)
    return score.toMidiScore()
}

/** Convert a step-based [Score] into an event-based [MidiScore]. */
fun Score.toMidiScore(ppq: Int = MidiTiming.DEFAULT_PPQ): MidiScore {
    val bpm = (tempoBpm ?: MidiTiming.DEFAULT_BPM).coerceIn(20, 300)
    val notesOut = mutableListOf<Note>()
    var currentTick = 0
    for (step in notes) {
        val durTicks = MidiTiming.beatsToTicks(step.durationBeats, ppq).coerceAtLeast(1)
        if (step.noteNames.isEmpty()) {
            // Rest: just advance the cursor.
            currentTick += durTicks
            continue
        }
        for (name in step.noteNames) {
            val midi = noteNameToMidi(name) ?: continue
            notesOut += Note(
                channel = LEGACY_CHANNEL,
                midi = midi,
                velocity = LEGACY_VELOCITY,
                startTicks = currentTick,
                durationTicks = durTicks,
            )
        }
        currentTick += durTicks
    }
    return MidiScore(
        ppq = ppq,
        title = title,
        tempoMap = mutableListOf(TempoEvent(0, MidiTiming.bpmToMicrosPerQuarter(bpm))),
        notes = notesOut,
    )
}

/**
 * Project an event-based [MidiScore] onto a step-grid [Score] for the legacy
 * UI. Notes whose start ticks fall within [chordWindowTicks] of each other
 * are merged into a single chord step. The step duration is the **shortest**
 * note in the cluster (so longer overlapping notes get truncated visually).
 * Gaps between clusters become rest steps. All notes are flattened
 * regardless of channel.
 */
fun MidiScore.toLegacyScore(
    chordWindowTicks: Int = ppq / 16,
    minStepTicks: Int = ppq / 16,
): Score {
    if (notes.isEmpty()) {
        return Score(notes = emptyList(), title = title, tempoBpm = initialBpm(), version = 1)
    }

    val sorted = notes.sortedWith(compareBy({ it.startTicks }, { it.midi }))

    // Group near-simultaneous note-ons into clusters.
    data class Cluster(
        val startTick: Int,
        val midis: MutableSet<Int>,
        var minDurationTicks: Int,
    )

    val clusters = mutableListOf<Cluster>()
    for (n in sorted) {
        val last = clusters.lastOrNull()
        if (last != null && n.startTicks - last.startTick <= chordWindowTicks) {
            last.midis += n.midi
            last.minDurationTicks = minOf(last.minDurationTicks, n.durationTicks)
        } else {
            clusters += Cluster(
                startTick = n.startTicks,
                midis = mutableSetOf(n.midi),
                minDurationTicks = n.durationTicks,
            )
        }
    }

    val steps = mutableListOf<ScoreStep>()
    var cursor = 0
    for ((idx, c) in clusters.withIndex()) {
        // Insert rest if there's a gap before this cluster.
        if (c.startTick > cursor) {
            val restTicks = c.startTick - cursor
            if (restTicks >= minStepTicks) {
                steps += ScoreStep(
                    noteNames = emptyList(),
                    durationBeats = MidiTiming.ticksToBeats(restTicks, ppq),
                )
            }
            cursor = c.startTick
        }

        // Step duration is bounded by the next cluster's start (so notes
        // can't visually overlap the next chord) and by the cluster's
        // minimum note duration.
        val nextStart = if (idx + 1 < clusters.size) clusters[idx + 1].startTick else Int.MAX_VALUE
        val gapToNext = nextStart - c.startTick
        val durTicks = max(minStepTicks, minOf(c.minDurationTicks, gapToNext))

        steps += ScoreStep(
            noteNames = c.midis.sorted().map(::midiToNoteName),
            durationBeats = MidiTiming.ticksToBeats(durTicks, ppq),
        )
        cursor = c.startTick + durTicks
    }

    return Score(
        notes = steps,
        title = title,
        tempoBpm = initialBpm(),
        version = 1,
    )
}
