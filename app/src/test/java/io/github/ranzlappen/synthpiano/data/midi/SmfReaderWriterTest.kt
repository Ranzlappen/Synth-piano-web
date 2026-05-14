package io.github.ranzlappen.synthpiano.data.midi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SmfReaderWriterTest {

    @Test
    fun roundTripSingleNote() {
        val original = MidiScore(
            ppq = 480,
            title = "single",
            notes = mutableListOf(
                Note(channel = 0, midi = 60, velocity = 100, startTicks = 0, durationTicks = 480),
            ),
        )

        val bytes = SmfWriter.write(original)
        assertTrue("Output starts with MThd", bytes.size >= 4 &&
            bytes[0] == 'M'.code.toByte() && bytes[1] == 'T'.code.toByte() &&
            bytes[2] == 'h'.code.toByte() && bytes[3] == 'd'.code.toByte())

        val parsed = SmfReader.read(bytes)
        assertEquals(480, parsed.ppq)
        assertEquals("single", parsed.title)
        assertEquals(1, parsed.notes.size)
        val n = parsed.notes[0]
        assertEquals(60, n.midi)
        assertEquals(100, n.velocity)
        assertEquals(0, n.startTicks)
        assertEquals(480, n.durationTicks)
        assertEquals(0, n.channel)
    }

    @Test
    fun roundTripChordAndMelody() {
        val original = MidiScore(
            ppq = 480,
            title = "demo",
            notes = mutableListOf(
                // C major chord at tick 0, half note (240 ticks at quarter=480? no, half=960)
                Note(0, 60, 100, 0, 960),
                Note(0, 64, 100, 0, 960),
                Note(0, 67, 100, 0, 960),
                // Melody note after the chord
                Note(0, 72, 110, 960, 480),
            ),
        )

        val bytes = SmfWriter.write(original)
        val parsed = SmfReader.read(bytes)

        assertEquals(4, parsed.notes.size)
        // Sort for stable comparison
        val originalSorted = original.notes.sortedWith(compareBy({ it.startTicks }, { it.midi }))
        val parsedSorted = parsed.notes.sortedWith(compareBy({ it.startTicks }, { it.midi }))
        for ((a, b) in originalSorted.zip(parsedSorted)) {
            assertEquals(a.midi, b.midi)
            assertEquals(a.velocity, b.velocity)
            assertEquals(a.startTicks, b.startTicks)
            assertEquals(a.durationTicks, b.durationTicks)
            assertEquals(a.channel, b.channel)
        }
    }

    @Test
    fun roundTripPreservesTempo() {
        val original = MidiScore(
            ppq = 480,
            tempoMap = mutableListOf(TempoEvent(0, MidiTiming.bpmToMicrosPerQuarter(140))),
            notes = mutableListOf(Note(0, 60, 100, 0, 480)),
        )

        val parsed = SmfReader.read(SmfWriter.write(original))
        assertEquals(140, parsed.initialBpm())
    }

    @Test
    fun roundTripPreservesMultiChannel() {
        val original = MidiScore(
            ppq = 480,
            notes = mutableListOf(
                Note(channel = 0, midi = 60, velocity = 100, startTicks = 0, durationTicks = 480),
                Note(channel = 5, midi = 67, velocity = 90, startTicks = 240, durationTicks = 240),
            ),
        )

        val parsed = SmfReader.read(SmfWriter.write(original))
        assertEquals(2, parsed.notes.size)
        val byChannel = parsed.notes.associateBy { it.channel }
        assertNotNull(byChannel[0])
        assertNotNull(byChannel[5])
        assertEquals(60, byChannel[0]!!.midi)
        assertEquals(67, byChannel[5]!!.midi)
        assertEquals(90, byChannel[5]!!.velocity)
    }

    @Test
    fun roundTripPreservesOverlappingSameNote() {
        // Two NoteOn for MIDI 60 on channel 0 with no NoteOff between → both
        // valid notes in the SMF model. The reader should pair via FIFO so
        // both notes round-trip with their original durations.
        val original = MidiScore(
            ppq = 480,
            notes = mutableListOf(
                Note(0, 60, 100, 0, 960),
                Note(0, 60, 80, 240, 240),
            ),
        )

        val parsed = SmfReader.read(SmfWriter.write(original))
        assertEquals(2, parsed.notes.size)
        // Both notes should land back. Order may differ but durations & starts are preserved.
        val sorted = parsed.notes.sortedWith(compareBy({ it.startTicks }, { it.durationTicks }))
        assertEquals(0, sorted[0].startTicks)
        assertEquals(240, sorted[1].startTicks)
    }

    @Test
    fun rejectsNonSmfMagicBytes() {
        val garbage = "Hello, world!".toByteArray()
        val ex = assertThrows(IllegalArgumentException::class.java) {
            SmfReader.read(garbage)
        }
        assertTrue(ex.message!!.contains("MThd") || ex.message!!.contains("MIDI"))
    }

    @Test
    fun rejectsFormat2() {
        // Synthesise a minimal valid format-2 SMF: MThd + 6-byte header (format=2,
        // ntrks=1, division=480) + an empty track.
        val header = byteArrayOf(
            'M'.code.toByte(), 'T'.code.toByte(), 'h'.code.toByte(), 'd'.code.toByte(),
            0, 0, 0, 6,                  // header chunk length = 6
            0, 2,                        // format = 2
            0, 1,                        // ntrks = 1
            0x01.toByte(), 0xE0.toByte(), // division = 480
        )
        val track = byteArrayOf(
            'M'.code.toByte(), 'T'.code.toByte(), 'r'.code.toByte(), 'k'.code.toByte(),
            0, 0, 0, 4,
            0x00,                        // delta 0
            0xFF.toByte(), 0x2F, 0x00,   // End Of Track
        )
        val bytes = header + track

        val ex = assertThrows(IllegalArgumentException::class.java) {
            SmfReader.read(bytes)
        }
        assertTrue(ex.message!!.contains("format 2"))
    }

    @Test
    fun emptyScoreRoundTrips() {
        val original = MidiScore(ppq = 480)
        val parsed = SmfReader.read(SmfWriter.write(original))
        assertEquals(480, parsed.ppq)
        assertEquals(0, parsed.notes.size)
        assertEquals(120, parsed.initialBpm())
    }

    @Test
    fun titleSurvivesRoundTrip() {
        val original = MidiScore(ppq = 480, title = "Hello World")
        val parsed = SmfReader.read(SmfWriter.write(original))
        assertEquals("Hello World", parsed.title)
    }

    @Test
    fun untitledScoreHasNullTitle() {
        val parsed = SmfReader.read(SmfWriter.write(MidiScore(ppq = 480)))
        assertNull(parsed.title)
    }

    @Test
    fun roundTripPreservesSustainPedal() {
        val original = MidiScore(
            ppq = 480,
            notes = mutableListOf(Note(0, 60, 100, 0, 1920)),
            controlEvents = mutableListOf(
                ControlEvent(tick = 240, channel = 0, kind = ControlKind.SUSTAIN, value = 127),
                ControlEvent(tick = 1680, channel = 0, kind = ControlKind.SUSTAIN, value = 0),
            ),
        )
        val parsed = SmfReader.read(SmfWriter.write(original))
        val sustain = parsed.controlEvents.filter { it.kind == ControlKind.SUSTAIN }
        assertEquals(2, sustain.size)
        assertEquals(240, sustain[0].tick)
        assertEquals(127, sustain[0].value)
        assertEquals(1680, sustain[1].tick)
        assertEquals(0, sustain[1].value)
        // Sustain CCs should not be duplicated in nonNoteEvents — the writer
        // would otherwise emit each once from controlEvents and once from
        // nonNoteEvents, producing a four-event round-trip.
        val ccInRaw = parsed.nonNoteEvents.count {
            (it.statusByte and 0xF0) == 0xB0 && it.data1 == 64
        }
        assertEquals(0, ccInRaw)
    }

    @Test
    fun roundTripPreservesExpressionAndAftertouch() {
        val original = MidiScore(
            ppq = 480,
            notes = mutableListOf(Note(0, 60, 100, 0, 960)),
            controlEvents = mutableListOf(
                ControlEvent(0, 0, ControlKind.EXPRESSION, 100),
                ControlEvent(240, 0, ControlKind.EXPRESSION, 50),
                ControlEvent(480, 0, ControlKind.CHANNEL_PRESSURE, 80),
            ),
        )
        val parsed = SmfReader.read(SmfWriter.write(original))
        val expr = parsed.controlEvents.filter { it.kind == ControlKind.EXPRESSION }
            .sortedBy { it.tick }
        assertEquals(2, expr.size)
        assertEquals(100, expr[0].value)
        assertEquals(50, expr[1].value)
        val pressure = parsed.controlEvents.filter { it.kind == ControlKind.CHANNEL_PRESSURE }
        assertEquals(1, pressure.size)
        assertEquals(80, pressure[0].value)
        assertEquals(480, pressure[0].tick)
    }

    @Test
    fun sustainBelowThresholdNormalisesToReleased() {
        // Hardware that emits half-pedal values should still produce a clean
        // 0/127 typed event on import.
        val original = MidiScore(
            ppq = 480,
            controlEvents = mutableListOf(
                ControlEvent(0, 0, ControlKind.SUSTAIN, 63),  // below 64 — counts as released
                ControlEvent(240, 0, ControlKind.SUSTAIN, 64), // at threshold — held
            ),
        )
        val parsed = SmfReader.read(SmfWriter.write(original))
        val sustain = parsed.controlEvents.filter { it.kind == ControlKind.SUSTAIN }
            .sortedBy { it.tick }
        assertEquals(2, sustain.size)
        assertEquals(0, sustain[0].value)
        assertEquals(127, sustain[1].value)
    }
}
