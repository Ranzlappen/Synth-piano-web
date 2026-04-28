package io.github.ranzlappen.synthpiano.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreModelTest {

    @Test
    fun `noteNameToMidi handles natural pitches`() {
        assertEquals(60, noteNameToMidi("C4"))
        assertEquals(69, noteNameToMidi("A4"))
        assertEquals(72, noteNameToMidi("C5"))
    }

    @Test
    fun `noteNameToMidi handles sharps and flats`() {
        assertEquals(61, noteNameToMidi("C#4"))
        assertEquals(61, noteNameToMidi("DB4"))
        assertEquals(70, noteNameToMidi("BB4"))
    }

    @Test
    fun `noteNameToMidi rejects garbage`() {
        assertNull(noteNameToMidi(""))
        assertNull(noteNameToMidi("Z4"))
        assertNull(noteNameToMidi("C-x"))
    }

    @Test
    fun `midiToNoteName round-trips`() {
        for (m in listOf(48, 60, 61, 69, 81)) {
            val name = midiToNoteName(m)
            assertEquals(m, noteNameToMidi(name))
        }
    }

    @Test
    fun `parses Python-source score schema`() {
        val text = """
            {
              "notes": [
                {"note": "C4", "duration": 1.0},
                {"note": "E4", "duration": 0.5},
                {"note": ["C4", "E4", "G4"], "duration": 2.0}
              ]
            }
        """.trimIndent()
        val score = parseScoreJson(text)
        assertEquals(3, score.notes.size)
        assertEquals(listOf("C4"), score.notes[0].noteNames)
        assertEquals(0.5f, score.notes[1].durationBeats, 1e-6f)
        assertEquals(listOf("C4", "E4", "G4"), score.notes[2].noteNames)
        assertEquals(1, score.version)
        assertNull(score.title)
    }

    @Test
    fun `parses extended schema with title and tempo`() {
        val text = """
            {
              "version": 1,
              "title": "Test",
              "tempo": 90,
              "notes": [{"note": "C4", "duration": 1.0}]
            }
        """.trimIndent()
        val score = parseScoreJson(text)
        assertEquals("Test", score.title)
        assertEquals(90, score.tempoBpm)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects missing notes array`() {
        parseScoreJson("""{"title": "x"}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects malformed JSON`() {
        parseScoreJson("""{"notes": [""")
    }

    @Test
    fun `parses top-level array score`() {
        val text = """
            [
                {"notes": ["F4", "A4", "C5"], "duration": 1.0},
                {"note": "rest", "duration": 0.5},
                {"notes": ["E4", "G4", "B4"], "duration": 1.0}
            ]
        """.trimIndent()
        val score = parseScoreJson(text)
        assertEquals(3, score.notes.size)
        assertEquals(listOf("F4", "A4", "C5"), score.notes[0].noteNames)
        assertTrue(score.notes[1].noteNames.isEmpty())
        assertEquals(0.5f, score.notes[1].durationBeats, 1e-6f)
        assertEquals(listOf("E4", "G4", "B4"), score.notes[2].noteNames)
        assertNull(score.title)
        assertNull(score.tempoBpm)
    }

    @Test
    fun `parses rest literal as empty step`() {
        val text = """
            {"notes": [
                {"note": "rest", "duration": 0.5},
                {"note": "REST", "duration": 0.25}
            ]}
        """.trimIndent()
        val score = parseScoreJson(text)
        assertEquals(2, score.notes.size)
        assertTrue(score.notes[0].noteNames.isEmpty())
        assertTrue(score.notes[1].noteNames.isEmpty())
    }

    @Test
    fun `serializes empty noteNames as rest and round trips`() {
        val score = Score(
            notes = listOf(
                ScoreStep(listOf("C4"), 1.0f),
                ScoreStep(emptyList(), 0.5f),
            ),
            title = "WithRest",
        )
        val text = score.toJsonString(prettyPrint = false)
        assertTrue("rest literal expected in serialized form", text.contains("\"rest\""))
        val parsed = parseScoreJson(text)
        assertEquals(2, parsed.notes.size)
        assertEquals(listOf("C4"), parsed.notes[0].noteNames)
        assertTrue(parsed.notes[1].noteNames.isEmpty())
        assertEquals(0.5f, parsed.notes[1].durationBeats, 1e-6f)
    }

    @Test
    fun `serialized round trips`() {
        val score = Score(
            notes = listOf(
                ScoreStep(listOf("C4"), 1.0f),
                ScoreStep(listOf("C4", "E4", "G4"), 2.0f),
            ),
            title = "Round",
            tempoBpm = 100,
        )
        val text = score.toJsonString(prettyPrint = false)
        val parsed = parseScoreJson(text)
        assertEquals(score.notes.size, parsed.notes.size)
        assertEquals(score.title, parsed.title)
        assertEquals(score.tempoBpm, parsed.tempoBpm)
        assertEquals(score.notes[0].durationBeats, parsed.notes[0].durationBeats, 1e-6f)
        assertEquals(score.notes[1].noteNames, parsed.notes[1].noteNames)
    }
}
