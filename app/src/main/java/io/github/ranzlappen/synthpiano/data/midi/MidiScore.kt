package io.github.ranzlappen.synthpiano.data.midi

/**
 * Event-based domain model for the editor + persistence layer. The canonical
 * exchange format on disk is Standard MIDI File (SMF), so this model is
 * shaped to round-trip through ktmidi's [dev.atsushieno.ktmidi.Midi1Music]
 * without losing data the editor doesn't currently understand.
 *
 *   notes              edited by the piano-roll
 *   tempoMap           edited via the tempo control; first entry is the "song tempo"
 *   nonNoteEvents      preserved opaquely (CC, sustain, program change, time
 *                      signature, key signature, text, marker, ...) so import
 *                      → save remains fidelity-preserving for everything
 *                      except the notes the user actually edits.
 *
 * All times are in **ticks** at the score's [ppq] (pulses per quarter note).
 * Conversion to/from beats / microseconds lives in [MidiTiming].
 */
data class MidiScore(
    val ppq: Int = MidiTiming.DEFAULT_PPQ,
    val title: String? = null,
    val tempoMap: MutableList<TempoEvent> = mutableListOf(
        TempoEvent(0, MidiTiming.DEFAULT_MICROS_PER_QUARTER)
    ),
    val notes: MutableList<Note> = mutableListOf(),
    val nonNoteEvents: MutableList<RawEvent> = mutableListOf(),
) {
    /** Last absolute tick across all notes and raw events. Used for End Of Track. */
    fun totalTicks(): Int {
        var max = 0
        for (n in notes) {
            val end = n.startTicks + n.durationTicks
            if (end > max) max = end
        }
        for (e in nonNoteEvents) if (e.tick > max) max = e.tick
        for (t in tempoMap) if (t.tick > max) max = t.tick
        return max
    }

    /** Initial tempo (the tempo at tick 0). Falls back to default if unset. */
    fun initialBpm(): Int {
        val first = tempoMap.firstOrNull { it.tick == 0 }
            ?: tempoMap.minByOrNull { it.tick }
            ?: return MidiTiming.DEFAULT_BPM
        return MidiTiming.microsPerQuarterToBpm(first.microsPerQuarter).toInt().coerceIn(20, 300)
    }

    /** Replace the initial tempo. Adds a tick-0 entry if missing. */
    fun setInitialBpm(bpm: Int) {
        val us = MidiTiming.bpmToMicrosPerQuarter(bpm)
        val idx = tempoMap.indexOfFirst { it.tick == 0 }
        if (idx >= 0) tempoMap[idx] = TempoEvent(0, us)
        else tempoMap.add(0, TempoEvent(0, us))
    }
}

/** A single playable note. `velocity` is 1..127 (0 means note-off in SMF; we never store 0 here). */
data class Note(
    val channel: Int,            // 0..15
    val midi: Int,               // 0..127
    val velocity: Int,           // 1..127
    val startTicks: Int,         // absolute ticks from start of song
    val durationTicks: Int,      // > 0
)

/** A tempo change point. */
data class TempoEvent(
    val tick: Int,
    val microsPerQuarter: Int,
)

/**
 * Any event from a loaded SMF that isn't a Note On/Off. We hold the raw
 * status byte + payload so the writer can emit it back unchanged. Includes:
 * Control Change (CC), Program Change, Pitch Bend, Polyphonic Aftertouch,
 * Channel Aftertouch, SysEx, and meta events other than Set Tempo.
 *
 * `statusByte` is the full byte (status nibble + channel for channel
 * messages, or 0xFF for meta, 0xF0/0xF7 for SysEx).
 * `metaType` is the meta sub-type for status 0xFF (e.g. 0x58 time
 * signature). For non-meta events it's -1.
 * `data1` / `data2` carry the inline data bytes for short channel
 * messages. `payload` carries variable-length data for meta and SysEx.
 */
data class RawEvent(
    val tick: Int,
    val statusByte: Int,         // 0..255
    val metaType: Int = -1,      // 0..127 for meta, else -1
    val data1: Int = 0,          // 0..127
    val data2: Int = 0,          // 0..127
    val payload: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RawEvent) return false
        return tick == other.tick &&
            statusByte == other.statusByte &&
            metaType == other.metaType &&
            data1 == other.data1 &&
            data2 == other.data2 &&
            (payload?.contentEquals(other.payload) ?: (other.payload == null))
    }

    override fun hashCode(): Int {
        var result = tick
        result = 31 * result + statusByte
        result = 31 * result + metaType
        result = 31 * result + data1
        result = 31 * result + data2
        result = 31 * result + (payload?.contentHashCode() ?: 0)
        return result
    }
}
