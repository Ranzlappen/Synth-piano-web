package io.github.ranzlappen.synthpiano.data

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
}
