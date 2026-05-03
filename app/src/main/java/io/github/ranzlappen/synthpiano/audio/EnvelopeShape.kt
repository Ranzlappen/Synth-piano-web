package io.github.ranzlappen.synthpiano.audio

import kotlinx.serialization.Serializable

/**
 * One breakpoint of an [EnvelopeShape]. [timeSec] is the length of the
 * segment leading INTO this vertex from the previous one (so the first
 * vertex's [timeSec] is meaningless and conventionally zero). [level] is
 * the absolute amplitude at this vertex in the unit interval. [curve] in
 * [-1, +1] shapes the segment leading INTO this vertex (positive = ease
 * out / convex, negative = ease in / concave, zero = linear).
 */
@Serializable
data class EnvelopeVertex(
    val timeSec: Float,
    val level: Float,
    val curve: Float = 0f,
)

/**
 * A multi-segment envelope generator (MSEG): an ordered list of
 * [vertices] joined by curved segments, with one [sustainIndex] vertex
 * held while a key is down. Note-off resumes traversal from the sustain
 * vertex; vertices after the sustain pin form the release tail.
 *
 * The classic ADSR is a 5-vertex shape (start=0, attack peak=1, decay
 * end=sustain, sustain pin=sustain, release end=0) with sustainIndex=3.
 * [fromAdsr] builds that canonical shape so existing code paths keep
 * working unchanged while the editor and engine grow MSEG support.
 */
@Serializable
data class EnvelopeShape(
    val vertices: List<EnvelopeVertex>,
    val sustainIndex: Int,
) {
    fun isCanonicalAdsr(): Boolean =
        vertices.size == 5 &&
            sustainIndex == 3 &&
            vertices[0].level == 0f &&
            vertices[1].level == 1f &&
            vertices[2].level == vertices[3].level &&
            vertices[3].timeSec == 0f &&
            vertices[4].level == 0f

    /**
     * Project this shape onto the legacy [Adsr] domain. Lossless when
     * [isCanonicalAdsr]; for non-canonical shapes returns a best-effort
     * approximation (peak amplitude as the implicit attack target,
     * sustain plateau pulled from the sustain vertex's level).
     */
    fun toAdsr(): Adsr {
        if (vertices.isEmpty()) return Adsr()
        val sustainVertex = vertices.getOrNull(sustainIndex) ?: vertices.last()
        val attackTime = vertices.subList(1, (sustainIndex - 1).coerceAtLeast(1).coerceAtMost(vertices.size))
            .sumOf { it.timeSec.toDouble() }
            .toFloat()
            .coerceAtLeast(0.001f)
        val decayTime = vertices.getOrNull(sustainIndex - 1)
            ?.timeSec
            ?.coerceAtLeast(0.001f)
            ?: 0.150f
        val releaseTime = vertices.drop(sustainIndex + 1)
            .sumOf { it.timeSec.toDouble() }
            .toFloat()
            .coerceAtLeast(0.001f)
        val curveAvg = vertices.drop(1).map { it.curve }.average().toFloat().takeIf { !it.isNaN() } ?: 0f
        return Adsr(
            attackSec = attackTime,
            decaySec = decayTime,
            sustain = sustainVertex.level.coerceIn(0f, 1f),
            releaseSec = releaseTime,
            curve = curveAvg.coerceIn(-1f, 1f),
        )
    }

    companion object {
        const val MAX_VERTICES = 16

        /** Default 5-vertex shape matching [Adsr]'s defaults. */
        val DEFAULT: EnvelopeShape = fromAdsr(Adsr())

        /** Build the canonical 5-vertex ADSR shape from a legacy [Adsr]. */
        fun fromAdsr(a: Adsr): EnvelopeShape = EnvelopeShape(
            vertices = listOf(
                EnvelopeVertex(0f, 0f, 0f),
                EnvelopeVertex(a.attackSec, 1f, a.curve),
                EnvelopeVertex(a.decaySec, a.sustain, a.curve),
                EnvelopeVertex(0f, a.sustain, 0f),
                EnvelopeVertex(a.releaseSec, 0f, a.curve),
            ),
            sustainIndex = 3,
        )
    }
}
