package io.github.ranzlappen.synthpiano.data

import io.github.ranzlappen.synthpiano.ui.play.chordNotes
import org.junit.Assert.assertEquals
import org.junit.Test

class ChordPadTest {

    @Test
    fun `intervalsFor returns correct chord shapes`() {
        assertEquals(listOf(0, 4, 7), intervalsFor(ChordQuality.MAJ))
        assertEquals(listOf(0, 3, 7), intervalsFor(ChordQuality.MIN))
        assertEquals(listOf(0, 4, 7, 10), intervalsFor(ChordQuality.SEVEN))
        assertEquals(listOf(0, 3, 6), intervalsFor(ChordQuality.DIM))
        assertEquals(listOf(0, 5, 7), intervalsFor(ChordQuality.SUS))
    }

    @Test
    fun `default pads JSON round-trips`() {
        val pads = defaultChordPads()
        val json = pads.toJson()
        val parsed = parseChordPadsJson(json)
        assertEquals(pads, parsed)
        assertEquals(11, pads.size)
    }

    @Test
    fun `pad label uses note name and quality short form`() {
        assertEquals("CMaj", ChordPad(60, ChordQuality.MAJ).label())
        assertEquals("AMin", ChordPad(69, ChordQuality.MIN).label())
        assertEquals("G7", ChordPad(67, ChordQuality.SEVEN).label())
        assertEquals("BDim", ChordPad(71, ChordQuality.DIM).label())
    }

    @Test
    fun `chordNotes anchors to leftmost-visible C`() {
        // C major triad anchored at C3 (MIDI 48): C3, E3, G3 = 48, 52, 55
        val cMaj = ChordPad(60, ChordQuality.MAJ)
        assertEquals(listOf(48, 52, 55), chordNotes(cMaj, baseC = 48))

        // Same pad anchored at C5 (MIDI 72): C5, E5, G5 = 72, 76, 79
        assertEquals(listOf(72, 76, 79), chordNotes(cMaj, baseC = 72))
    }

    @Test
    fun `chordNotes uses pitch class only from rootNote`() {
        // Two pads with different absolute MIDI but same pitch class (G):
        // both should produce the same chord at a given baseC.
        val gMajLow = ChordPad(43, ChordQuality.MAJ)   // G2
        val gMajHigh = ChordPad(67, ChordQuality.MAJ)  // G4
        assertEquals(chordNotes(gMajLow, baseC = 48), chordNotes(gMajHigh, baseC = 48))
    }

    @Test
    fun `octaveOffset shifts the chord one octave up`() {
        val cMajUp = ChordPad(60, ChordQuality.MAJ, octaveOffset = 1)
        // Anchored at C3 (48), +1 octave = C4 (60), E4 (64), G4 (67)
        assertEquals(listOf(60, 64, 67), chordNotes(cMajUp, baseC = 48))
    }
}
