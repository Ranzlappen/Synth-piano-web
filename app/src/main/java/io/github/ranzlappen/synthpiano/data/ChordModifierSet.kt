package io.github.ranzlappen.synthpiano.data

/**
 * The five chord-modifier qualities surfaced by the perform-tab modifier
 * strip. Two strips of buttons (sticky locks + momentary shifts) toggle
 * elements of a [ChordModifierState]; whichever piano key is then pressed
 * becomes the chord's root.
 *
 * MAJ/MIN/DIM/SUS pick a triad shape; SEVEN is orthogonal and stacks onto
 * whichever triad won. With nothing active, a key plays a single note.
 */
enum class ChordQuality {
    MAJ, MIN, SEVEN, DIM, SUS;

    fun label(): String = when (this) {
        MAJ -> "Maj"
        MIN -> "min"
        SEVEN -> "7"
        DIM -> "dim"
        SUS -> "sus"
    }
}

/**
 * Aggregate of the two modifier sources. [sticky] is persisted across
 * sessions; [held] reflects the live state of the momentary shift row.
 */
data class ChordModifierState(
    val sticky: Set<ChordQuality> = emptySet(),
    val held: Set<ChordQuality> = emptySet(),
) {
    val active: Set<ChordQuality> get() = sticky union held
}

/**
 * Resolve the active modifier set into chord intervals (semitones from the
 * root, root included as 0).
 *
 * Triad priority when multiple triad qualities are active: SUS > DIM > MIN
 * > MAJ. SEVEN is orthogonal; it adds a major-7th when paired with MAJ
 * exclusively, otherwise a minor-7th (b7).
 *
 * Cheat sheet:
 *   {}              -> [0]              single note
 *   {MAJ}           -> [0,4,7]          major triad
 *   {MIN}           -> [0,3,7]          minor triad
 *   {DIM}           -> [0,3,6]          diminished triad
 *   {SUS}           -> [0,5,7]          sus4
 *   {SEVEN}         -> [0,4,7,10]       dom7 (no triad selected)
 *   {MAJ,SEVEN}     -> [0,4,7,11]       Maj7
 *   {MIN,SEVEN}     -> [0,3,7,10]       m7
 *   {DIM,SEVEN}     -> [0,3,6,10]       m7b5 (half-diminished)
 *   {SUS,SEVEN}     -> [0,5,7,10]       7sus4
 *   {MAJ,MIN}       -> [0,3,7]          MIN wins by priority
 *   {SUS,MIN,SEVEN} -> [0,5,7,10]       SUS wins, becomes 7sus4
 */
fun buildChordIntervals(active: Set<ChordQuality>): List<Int> {
    val triadPriority = listOf(ChordQuality.SUS, ChordQuality.DIM, ChordQuality.MIN, ChordQuality.MAJ)
    val triad = triadPriority.firstOrNull { it in active }
    val hasSeven = ChordQuality.SEVEN in active

    if (triad == null && !hasSeven) return listOf(0)

    val base = when (triad) {
        ChordQuality.SUS -> listOf(0, 5, 7)
        ChordQuality.DIM -> listOf(0, 3, 6)
        ChordQuality.MIN -> listOf(0, 3, 7)
        ChordQuality.MAJ, null -> listOf(0, 4, 7)
        ChordQuality.SEVEN -> listOf(0, 4, 7) // unreachable — SEVEN isn't in triadPriority
    }
    if (!hasSeven) return base

    val seventh = if (triad == ChordQuality.MAJ) 11 else 10
    return base + seventh
}

/** Comma-separated enum names for DataStore persistence. */
fun Set<ChordQuality>.toPrefString(): String = joinToString(",") { it.name }

fun parseChordModifierSet(s: String?): Set<ChordQuality> {
    if (s.isNullOrBlank()) return emptySet()
    return s.split(",").mapNotNullTo(mutableSetOf()) { token ->
        runCatching { ChordQuality.valueOf(token.trim()) }.getOrNull()
    }
}
