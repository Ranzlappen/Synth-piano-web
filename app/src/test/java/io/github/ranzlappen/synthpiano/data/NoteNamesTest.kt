package io.github.ranzlappen.synthpiano.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteNamesTest {

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
    fun `noteNameToMidi defaults octave to 4 when missing`() {
        assertEquals(60, noteNameToMidi("C"))
        assertEquals(69, noteNameToMidi("A"))
    }

    @Test
    fun `midiToNoteName round-trips`() {
        for (m in 24..96) {
            val name = midiToNoteName(m)
            assertEquals(m, noteNameToMidi(name))
        }
    }
}
