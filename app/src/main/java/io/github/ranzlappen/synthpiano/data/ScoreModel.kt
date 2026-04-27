package io.github.ranzlappen.synthpiano.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Round-trip-compatible with the Python source's score.json format:
 *
 *   { "notes": [ { "note": "A4", "duration": 0.5 }, ... ] }
 *
 * Each entry's `note` may also be a list of names for chords. New v2+
 * scores may add a top-level `version` field; the parser tolerates both.
 */

@Serializable
data class Score(
    val notes: List<ScoreStep> = emptyList(),
    val title: String? = null,
    val tempoBpm: Int? = null,
    val version: Int = 1,
)

/** One sequence step. `noteNames` is empty for a rest. */
@Serializable
data class ScoreStep(
    val noteNames: List<String>,    // e.g. ["A4"] or ["C4", "E4", "G4"]
    val durationBeats: Float,        // duration in beats (1.0 = quarter at the score's tempo)
)

private val NOTE_PC = mapOf(
    "C" to 0, "C#" to 1, "DB" to 1,
    "D" to 2, "D#" to 3, "EB" to 3,
    "E" to 4, "FB" to 4,
    "F" to 5, "F#" to 6, "GB" to 6,
    "G" to 7, "G#" to 8, "AB" to 8,
    "A" to 9, "A#" to 10, "BB" to 10,
    "B" to 11, "CB" to 11,
)

/**
 * Convert a note name like "A4" or "C#5" to MIDI. Returns null if unparseable.
 * MIDI 60 = C4 (middle C). Sharps and flats both accepted.
 */
fun noteNameToMidi(name: String): Int? {
    val s = name.trim().uppercase()
    if (s.isEmpty()) return null
    var i = 1
    if (i < s.length && (s[i] == '#' || s[i] == 'B')) i++
    val pcKey = s.substring(0, i)
    val pc = NOTE_PC[pcKey] ?: return null
    val octStr = s.substring(i).ifEmpty { "4" }
    val oct = octStr.toIntOrNull() ?: return null
    return (oct + 1) * 12 + pc
}

fun midiToNoteName(midi: Int): String {
    val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val pc = ((midi % 12) + 12) % 12
    val oct = midi / 12 - 1
    return "${names[pc]}$oct"
}

private val scoreJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

/**
 * Parse score JSON in the Python source format or the v2 native format.
 * Throws IllegalArgumentException with a human-readable reason on failure.
 */
fun parseScoreJson(text: String): Score {
    val root = try {
        scoreJson.parseToJsonElement(text)
    } catch (t: Throwable) {
        throw IllegalArgumentException("Invalid JSON: ${t.message}")
    }
    val obj = root as? JsonObject
        ?: throw IllegalArgumentException("Score JSON must be an object")

    val notesElem = obj["notes"]
        ?: throw IllegalArgumentException("Missing 'notes' array")
    val notesArr = notesElem as? JsonArray
        ?: throw IllegalArgumentException("'notes' must be an array")

    val steps = notesArr.mapIndexed { idx, e ->
        parseStep(e, idx)
    }

    return Score(
        notes = steps,
        title = (obj["title"] as? JsonPrimitive)?.contentOrNull,
        tempoBpm = (obj["tempo"] as? JsonPrimitive)?.intOrNullSafe()
            ?: (obj["tempoBpm"] as? JsonPrimitive)?.intOrNullSafe(),
        version = (obj["version"] as? JsonPrimitive)?.intOrNullSafe() ?: 1,
    )
}

private fun parseStep(e: JsonElement, idx: Int): ScoreStep {
    val obj = e as? JsonObject
        ?: throw IllegalArgumentException("Step #$idx must be an object")
    val noteElem = obj["note"]
        ?: obj["notes"]
        ?: throw IllegalArgumentException("Step #$idx missing 'note'")
    val names = when (noteElem) {
        is JsonPrimitive -> listOf(noteElem.content)
        is JsonArray -> noteElem.map { (it as JsonPrimitive).content }
        else -> throw IllegalArgumentException("Step #$idx 'note' must be string or array")
    }.filter { it.isNotBlank() }

    val durElem = obj["duration"]
        ?: obj["durationBeats"]
        ?: throw IllegalArgumentException("Step #$idx missing 'duration'")
    val dur = (durElem as? JsonPrimitive)?.floatOrNullSafe()
        ?: throw IllegalArgumentException("Step #$idx 'duration' must be a number")

    return ScoreStep(noteNames = names, durationBeats = dur)
}

fun Score.toJsonString(prettyPrint: Boolean = true): String {
    val obj = buildJsonObject {
        put("version", JsonPrimitive(version))
        title?.let { put("title", JsonPrimitive(it)) }
        tempoBpm?.let { put("tempo", JsonPrimitive(it)) }
        put("notes", buildJsonArray {
            for (step in notes) {
                add(buildJsonObject {
                    if (step.noteNames.size == 1) {
                        put("note", JsonPrimitive(step.noteNames.first()))
                    } else {
                        put("note", buildJsonArray { step.noteNames.forEach { add(JsonPrimitive(it)) } })
                    }
                    put("duration", JsonPrimitive(step.durationBeats))
                })
            }
        })
    }
    val encoder = if (prettyPrint) Json { this.prettyPrint = true; prettyPrintIndent = "  " } else scoreJson
    return encoder.encodeToString(JsonObject.serializer(), obj)
}

private fun JsonPrimitive.intOrNullSafe(): Int? =
    contentOrNull?.trim()?.toIntOrNull()

private fun JsonPrimitive.floatOrNullSafe(): Float? =
    contentOrNull?.trim()?.toFloatOrNull()
