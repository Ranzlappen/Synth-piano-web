package io.github.ranzlappen.synthpiano.data.midi

/**
 * Timing constants and conversions shared across the SMF reader/writer/recorder.
 *
 * PPQ (pulses per quarter note) = ticks per quarter at the file's `deltaTimeSpec`.
 * SMF encodes tempo as microseconds per quarter note. 60_000_000 / µs-per-quarter = BPM.
 */
object MidiTiming {

    /** Pulses per quarter note for files we author. 480 divides cleanly into common subdivisions. */
    const val DEFAULT_PPQ: Int = 480

    /** Default tempo when a file has no Set Tempo meta event. */
    const val DEFAULT_BPM: Int = 120

    const val DEFAULT_MICROS_PER_QUARTER: Int = 500_000  // 60_000_000 / 120

    fun bpmToMicrosPerQuarter(bpm: Int): Int = (60_000_000 / bpm).coerceAtLeast(1)

    fun microsPerQuarterToBpm(usPerQuarter: Int): Double =
        if (usPerQuarter <= 0) DEFAULT_BPM.toDouble() else 60_000_000.0 / usPerQuarter

    /** Convert beats to ticks at a given PPQ. */
    fun beatsToTicks(beats: Float, ppq: Int = DEFAULT_PPQ): Int =
        (beats * ppq).toInt().coerceAtLeast(0)

    fun ticksToBeats(ticks: Int, ppq: Int = DEFAULT_PPQ): Float =
        if (ppq <= 0) 0f else ticks.toFloat() / ppq

    /** Convert real-time microseconds to ticks given a fixed tempo. */
    fun microsToTicks(microseconds: Long, bpm: Int = DEFAULT_BPM, ppq: Int = DEFAULT_PPQ): Int {
        if (bpm <= 0 || ppq <= 0) return 0
        val usPerQuarter = bpmToMicrosPerQuarter(bpm).toLong()
        return ((microseconds * ppq) / usPerQuarter).toInt().coerceAtLeast(0)
    }

    fun ticksToMicros(ticks: Int, bpm: Int = DEFAULT_BPM, ppq: Int = DEFAULT_PPQ): Long {
        if (bpm <= 0 || ppq <= 0) return 0L
        val usPerQuarter = bpmToMicrosPerQuarter(bpm).toLong()
        return (ticks.toLong() * usPerQuarter) / ppq
    }
}
