package io.github.ranzlappen.synthpiano.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ChordModifierSetTest {

    @Test
    fun emptySet_isSingleNote() {
        assertEquals(listOf(0), buildChordIntervals(emptySet()))
    }

    @Test
    fun maj() {
        assertEquals(listOf(0, 4, 7), buildChordIntervals(setOf(ChordQuality.MAJ)))
    }

    @Test
    fun min() {
        assertEquals(listOf(0, 3, 7), buildChordIntervals(setOf(ChordQuality.MIN)))
    }

    @Test
    fun dim() {
        assertEquals(listOf(0, 3, 6), buildChordIntervals(setOf(ChordQuality.DIM)))
    }

    @Test
    fun sus() {
        assertEquals(listOf(0, 5, 7), buildChordIntervals(setOf(ChordQuality.SUS)))
    }

    @Test
    fun sevenAlone_isDom7() {
        assertEquals(listOf(0, 4, 7, 10), buildChordIntervals(setOf(ChordQuality.SEVEN)))
    }

    @Test
    fun majSeven_isMaj7() {
        assertEquals(
            listOf(0, 4, 7, 11),
            buildChordIntervals(setOf(ChordQuality.MAJ, ChordQuality.SEVEN)),
        )
    }

    @Test
    fun minSeven_isM7() {
        assertEquals(
            listOf(0, 3, 7, 10),
            buildChordIntervals(setOf(ChordQuality.MIN, ChordQuality.SEVEN)),
        )
    }

    @Test
    fun dimSeven_isM7b5() {
        assertEquals(
            listOf(0, 3, 6, 10),
            buildChordIntervals(setOf(ChordQuality.DIM, ChordQuality.SEVEN)),
        )
    }

    @Test
    fun susSeven_is7sus() {
        assertEquals(
            listOf(0, 5, 7, 10),
            buildChordIntervals(setOf(ChordQuality.SUS, ChordQuality.SEVEN)),
        )
    }

    @Test
    fun majAndMin_minWinsByPriority() {
        assertEquals(
            listOf(0, 3, 7),
            buildChordIntervals(setOf(ChordQuality.MAJ, ChordQuality.MIN)),
        )
    }

    @Test
    fun susBeatsAll() {
        assertEquals(
            listOf(0, 5, 7, 10),
            buildChordIntervals(setOf(ChordQuality.SUS, ChordQuality.MIN, ChordQuality.SEVEN)),
        )
    }

    @Test
    fun prefStringRoundTrip() {
        val set = setOf(ChordQuality.MAJ, ChordQuality.SEVEN)
        val s = set.toPrefString()
        assertEquals(set, parseChordModifierSet(s))
    }

    @Test
    fun parsesEmpty() {
        assertEquals(emptySet<ChordQuality>(), parseChordModifierSet(null))
        assertEquals(emptySet<ChordQuality>(), parseChordModifierSet(""))
        assertEquals(emptySet<ChordQuality>(), parseChordModifierSet("  "))
    }

    @Test
    fun parsesIgnoresGarbage() {
        assertEquals(
            setOf(ChordQuality.MAJ),
            parseChordModifierSet("MAJ,GARBAGE"),
        )
    }

    @Test
    fun inversionNone_passesThrough() {
        assertEquals(listOf(0, 4, 7), applyInversion(listOf(0, 4, 7), ChordInversion.NONE))
    }

    @Test
    fun firstInversion_majorTriad() {
        assertEquals(listOf(4, 7, 12), applyInversion(listOf(0, 4, 7), ChordInversion.FIRST))
    }

    @Test
    fun secondInversion_minorTriad() {
        assertEquals(listOf(7, 12, 15), applyInversion(listOf(0, 3, 7), ChordInversion.SECOND))
    }

    @Test
    fun thirdInversion_dom7() {
        assertEquals(
            listOf(10, 12, 16, 19),
            applyInversion(listOf(0, 4, 7, 10), ChordInversion.THIRD),
        )
    }

    @Test
    fun thirdInversion_onTriad_isNoOp() {
        // Triads only have 3 notes; 3rd inversion would rotate past the list,
        // so it should be a no-op rather than return a misleading voicing.
        assertEquals(listOf(0, 4, 7), applyInversion(listOf(0, 4, 7), ChordInversion.THIRD))
    }

    @Test
    fun anyInversion_onSingleNote_isNoOp() {
        assertEquals(listOf(0), applyInversion(listOf(0), ChordInversion.FIRST))
        assertEquals(listOf(0), applyInversion(listOf(0), ChordInversion.SECOND))
        assertEquals(listOf(0), applyInversion(listOf(0), ChordInversion.THIRD))
    }
}
