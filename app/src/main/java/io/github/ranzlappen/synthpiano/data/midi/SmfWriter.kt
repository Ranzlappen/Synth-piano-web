package io.github.ranzlappen.synthpiano.data.midi

import dev.atsushieno.ktmidi.Midi1CompoundMessage
import dev.atsushieno.ktmidi.Midi1Event
import dev.atsushieno.ktmidi.Midi1Music
import dev.atsushieno.ktmidi.Midi1SimpleMessage
import dev.atsushieno.ktmidi.Midi1Track
import dev.atsushieno.ktmidi.write

/**
 * Writes a [MidiScore] to Standard MIDI File (SMF, .mid) bytes.
 *
 * Always emits format-0 (single-track). Notes are expanded to NoteOn/NoteOff
 * pairs; tempo map events become Set Tempo metas; non-note events are
 * replayed at their original ticks; an End Of Track meta closes the file.
 */
object SmfWriter {

    /** Serialise [score] to SMF bytes. */
    fun write(score: MidiScore): ByteArray {
        val music = Midi1Music().apply {
            format = 0.toByte()
            deltaTimeSpec = score.ppq
        }

        val track = Midi1Track()
        val timed = collectTimedEvents(score)
        timed.sortWith(EVENT_ORDER)

        var prevTick = 0
        for (te in timed) {
            val delta = (te.tick - prevTick).coerceAtLeast(0)
            track.events.add(Midi1Event(delta, te.message))
            prevTick = te.tick
        }

        // Append End Of Track at totalTicks (or 0 if empty score) with delta 0 from last event.
        val eotTick = maxOf(prevTick, score.totalTicks())
        val eotDelta = (eotTick - prevTick).coerceAtLeast(0)
        track.events.add(Midi1Event(eotDelta, META_END_OF_TRACK))

        music.addTrack(track)

        val out = mutableListOf<Byte>()
        music.write(out)
        return out.toByteArray()
    }

    // ────────────────────────────────────────────────────────────────────

    private fun collectTimedEvents(score: MidiScore): MutableList<TimedEvent> {
        val out = mutableListOf<TimedEvent>()

        // Title as Track Name meta at tick 0 (only if not already in nonNoteEvents).
        val titleAlreadyEncoded = score.nonNoteEvents.any { it.statusByte == 0xFF && it.metaType == 0x03 }
        score.title?.takeIf { it.isNotBlank() && !titleAlreadyEncoded }?.let { t ->
            out += TimedEvent(
                tick = 0,
                priority = PRIO_META,
                message = Midi1CompoundMessage(0xFF, 0x03, 0, t.toByteArray(Charsets.UTF_8)),
            )
        }

        // Tempo map.
        for (te in score.tempoMap) {
            out += TimedEvent(
                tick = te.tick.coerceAtLeast(0),
                priority = PRIO_META,
                message = Midi1CompoundMessage(
                    0xFF, 0x51, 0,
                    encodeMicrosPerQuarter(te.microsPerQuarter),
                ),
            )
        }

        // Notes: emit NoteOn at start, NoteOff at end.
        for (n in score.notes) {
            val status = 0x90 or (n.channel and 0x0F)
            val offStatus = 0x80 or (n.channel and 0x0F)
            val v = n.velocity.coerceIn(1, 127)
            out += TimedEvent(
                tick = n.startTicks.coerceAtLeast(0),
                priority = PRIO_NOTE_ON,
                message = Midi1SimpleMessage(status, n.midi and 0x7F, v),
            )
            out += TimedEvent(
                tick = (n.startTicks + n.durationTicks).coerceAtLeast(n.startTicks + 1),
                priority = PRIO_NOTE_OFF,
                message = Midi1SimpleMessage(offStatus, n.midi and 0x7F, 0),
            )
        }

        // Non-note events (CC / PC / pitch bend / time sig / etc.).
        for (e in score.nonNoteEvents) {
            // Skip End Of Track copies — we always synthesise our own at the end.
            if (e.statusByte == 0xFF && e.metaType == 0x2F) continue
            // Skip Set Tempo from raw events — tempoMap is the source of truth.
            if (e.statusByte == 0xFF && e.metaType == 0x51) continue

            val msg = when {
                e.statusByte == 0xFF -> Midi1CompoundMessage(
                    0xFF, e.metaType and 0x7F, 0, e.payload,
                )
                e.statusByte == 0xF0 || e.statusByte == 0xF7 -> Midi1CompoundMessage(
                    e.statusByte, 0, 0, e.payload,
                )
                else -> Midi1SimpleMessage(e.statusByte, e.data1 and 0x7F, e.data2 and 0x7F)
            }
            val priority = when {
                e.statusByte == 0xFF -> PRIO_META
                else -> PRIO_OTHER
            }
            out += TimedEvent(e.tick.coerceAtLeast(0), priority, msg)
        }

        return out
    }

    private fun encodeMicrosPerQuarter(us: Int): ByteArray {
        val v = us.coerceIn(1, 0xFFFFFF)
        return byteArrayOf(
            ((v shr 16) and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte(),
            (v and 0xFF).toByte(),
        )
    }

    private data class TimedEvent(
        val tick: Int,
        val priority: Int,
        val message: dev.atsushieno.ktmidi.Midi1Message,
    )

    private const val PRIO_META = 0
    private const val PRIO_NOTE_OFF = 1
    private const val PRIO_OTHER = 2
    private const val PRIO_NOTE_ON = 3

    private val EVENT_ORDER = compareBy<TimedEvent>({ it.tick }, { it.priority })

    private val META_END_OF_TRACK: Midi1CompoundMessage =
        Midi1CompoundMessage(0xFF, 0x2F, 0, ByteArray(0))
}

/** Convenience: Score → SMF bytes. */
fun MidiScore.toSmfBytes(): ByteArray = SmfWriter.write(this)
