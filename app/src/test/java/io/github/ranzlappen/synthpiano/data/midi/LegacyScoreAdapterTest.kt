package io.github.ranzlappen.synthpiano.data.midi

import io.github.ranzlappen.synthpiano.data.Score
import io.github.ranzlappen.synthpiano.data.ScoreStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyScoreAdapterTest {

    @Test
    fun jsonImportProducesMidiScore() {
        val json = """
            {
              "title": "Test",
              "tempo": 100,
              "notes": [
                {"note": "C4", "duration": 1.0},
                {"note": ["E4", "G4"], "duration": 0.5},
                {"note": "rest", "duration": 0.5}
              ]
            }
        """.trimIndent()

        val ms = importLegacyJsonAsMidiScore(json)

        assertEquals("Test", ms.title)
        assertEquals(100, ms.initialBpm())
        assertEquals(3, ms.notes.size) // C4, E4, G4 (rest produces no notes)

        val byMidi = ms.notes.associateBy { it.midi }
        assertTrue(byMidi.containsKey(60)) // C4
        assertTrue(byMidi.containsKey(64)) // E4
        assertTrue(byMidi.containsKey(67)) // G4

        // C4 starts at 0, lasts 1 beat = 480 ticks
        assertEquals(0, byMidi[60]!!.startTicks)
        assertEquals(480, byMidi[60]!!.durationTicks)

        // E4 and G4 start at 480 (after the C4), last 0.5 beats = 240
        assertEquals(480, byMidi[64]!!.startTicks)
        assertEquals(240, byMidi[64]!!.durationTicks)
        assertEquals(480, byMidi[67]!!.startTicks)
    }

    @Test
    fun scoreToMidiScorePreservesMonophonicMelody() {
        val score = Score(
            notes = listOf(
                ScoreStep(noteNames = listOf("C4"), durationBeats = 1.0f),
                ScoreStep(noteNames = listOf("D4"), durationBeats = 0.5f),
                ScoreStep(noteNames = listOf("E4"), durationBeats = 0.5f),
            ),
            title = null,
            tempoBpm = 120,
        )

        val ms = score.toMidiScore()
        assertEquals(3, ms.notes.size)
        val sorted = ms.notes.sortedBy { it.startTicks }
        assertEquals(60, sorted[0].midi); assertEquals(0, sorted[0].startTicks)
        assertEquals(62, sorted[1].midi); assertEquals(480, sorted[1].startTicks)
        assertEquals(64, sorted[2].midi); assertEquals(720, sorted[2].startTicks)
    }

    @Test
    fun midiScoreBackToLegacyScoreClustersChords() {
        val ms = MidiScore(
            ppq = 480,
            tempoMap = mutableListOf(TempoEvent(0, MidiTiming.bpmToMicrosPerQuarter(120))),
            notes = mutableListOf(
                Note(0, 60, 100, 0, 480),
                Note(0, 64, 100, 5, 480), // within chord window of C
                Note(0, 67, 100, 10, 480),
                Note(0, 72, 100, 480, 240),
            ),
        )

        val legacy = ms.toLegacyScore()
        // Expect one chord step (C/E/G) then one melody step (C5)
        assertEquals(2, legacy.notes.size)
        assertEquals(setOf("C4", "E4", "G4"), legacy.notes[0].noteNames.toSet())
        assertEquals(listOf("C5"), legacy.notes[1].noteNames)
    }

    @Test
    fun midiScoreToLegacyInsertsRest() {
        val ms = MidiScore(
            ppq = 480,
            notes = mutableListOf(
                Note(0, 60, 100, 0, 240),
                Note(0, 62, 100, 720, 240), // 480-tick gap = 1 beat rest
            ),
        )
        val legacy = ms.toLegacyScore()
        assertEquals(3, legacy.notes.size)
        assertEquals(listOf("C4"), legacy.notes[0].noteNames)
        assertEquals(emptyList<String>(), legacy.notes[1].noteNames) // rest
        assertEquals(listOf("D4"), legacy.notes[2].noteNames)
    }

    @Test
    fun emptyMidiScoreToLegacyEmpty() {
        val legacy = MidiScore(ppq = 480).toLegacyScore()
        assertEquals(0, legacy.notes.size)
        assertEquals(120, legacy.tempoBpm)
    }
}
