package io.github.ranzlappen.synthpiano.audio

import io.github.ranzlappen.synthpiano.data.midi.MidiTiming
import io.github.ranzlappen.synthpiano.data.midi.TempoEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class MidiScorePlayerTimingTest {

    @Test
    fun zeroTickIsAlwaysZero() {
        val tm = listOf(TempoEvent(0, MidiTiming.bpmToMicrosPerQuarter(120)))
        assertEquals(0L, tickToMicrosInternal(0, tm, 480))
    }

    @Test
    fun constantTempoLinearScaling() {
        // 120 BPM, PPQ 480 -> 1 quarter = 500_000 µs, 1 tick = 500_000/480 µs
        val tm = listOf(TempoEvent(0, 500_000))
        // 1 quarter
        assertEquals(500_000L, tickToMicrosInternal(480, tm, 480))
        // 2 quarters
        assertEquals(1_000_000L, tickToMicrosInternal(960, tm, 480))
        // half-quarter
        assertEquals(250_000L, tickToMicrosInternal(240, tm, 480))
    }

    @Test
    fun multiSegmentTempoMap() {
        // 0..480 ticks at 120 BPM (500_000 µs/quarter)
        // 480..960 ticks at 60 BPM (1_000_000 µs/quarter)
        val tm = listOf(
            TempoEvent(0, 500_000),
            TempoEvent(480, 1_000_000),
        )
        // At tick 480: end of first segment
        assertEquals(500_000L, tickToMicrosInternal(480, tm, 480))
        // At tick 720: 480 ticks at first tempo + 240 ticks at second
        // = 500_000 + 240 * 1_000_000 / 480 = 500_000 + 500_000 = 1_000_000
        assertEquals(1_000_000L, tickToMicrosInternal(720, tm, 480))
        // At tick 960: 480 ticks at first + 480 ticks at second = 500_000 + 1_000_000
        assertEquals(1_500_000L, tickToMicrosInternal(960, tm, 480))
    }

    @Test
    fun negativeTickClampedToZero() {
        val tm = listOf(TempoEvent(0, 500_000))
        assertEquals(0L, tickToMicrosInternal(-100, tm, 480))
    }

    @Test
    fun tempoChangeAfterTick() {
        // If only one tempo entry at tick 0, all subsequent ticks use it.
        val tm = listOf(TempoEvent(0, MidiTiming.bpmToMicrosPerQuarter(60)))
        // 1 quarter at 60 BPM = 1_000_000 µs
        assertEquals(1_000_000L, tickToMicrosInternal(480, tm, 480))
    }
}
