package io.github.ranzlappen.synthpiano.data.midi

import dev.atsushieno.ktmidi.Midi1CompoundMessage
import dev.atsushieno.ktmidi.Midi1Music
import dev.atsushieno.ktmidi.mergeTracks
import dev.atsushieno.ktmidi.read
import java.io.InputStream

/**
 * Reads a Standard MIDI File (SMF, .mid) into our [MidiScore] domain model.
 *
 * Supported: format 0 (single track) and format 1 (multi-track, flattened
 * via ktmidi's `mergeTracks()`). Format 2 is rejected with a clear error.
 *
 * Multi-channel content is preserved on each [Note] so the editor can
 * colour-code by channel. Non-note events (CC, PC, pitch bend, time
 * signature, etc.) flow into [MidiScore.nonNoteEvents] for opaque
 * round-tripping. Set Tempo meta events populate [MidiScore.tempoMap].
 */
object SmfReader {

    /** Read SMF bytes into a [MidiScore]. Throws [IllegalArgumentException] on invalid input. */
    fun read(bytes: ByteArray): MidiScore {
        validateMagic(bytes)

        val music = Midi1Music()
        try {
            music.read(bytes.toList())
        } catch (t: Throwable) {
            throw IllegalArgumentException("Failed to parse SMF: ${t.message}", t)
        }

        val format = music.format.toInt() and 0xFF
        if (format == 2) {
            throw IllegalArgumentException(
                "SMF format 2 (multi-song) is not supported. Please convert to format 0 or 1."
            )
        }

        val ppq = music.deltaTimeSpec
        if (ppq <= 0) {
            // Negative deltaTimeSpec encodes SMPTE timing. We don't support that yet.
            throw IllegalArgumentException(
                "SMPTE timing (deltaTimeSpec=$ppq) is not supported. PPQ-based files only."
            )
        }

        // mergeTracks() flattens format-1 tracks into a single track preserving
        // absolute timing. For format-0 files it's effectively a no-op.
        val merged = music.mergeTracks()
        val track = merged.tracks.firstOrNull()
            ?: return MidiScore(ppq = ppq)

        return buildScore(ppq, track.events)
    }

    fun read(stream: InputStream): MidiScore = read(stream.readBytes())

    // ────────────────────────────────────────────────────────────────────

    private fun validateMagic(bytes: ByteArray) {
        if (bytes.size < 4 ||
            bytes[0] != 'M'.code.toByte() ||
            bytes[1] != 'T'.code.toByte() ||
            bytes[2] != 'h'.code.toByte() ||
            bytes[3] != 'd'.code.toByte()
        ) {
            throw IllegalArgumentException("Not a Standard MIDI File (missing MThd header)")
        }
    }

    private fun buildScore(
        ppq: Int,
        events: List<dev.atsushieno.ktmidi.Midi1Event>,
    ): MidiScore {
        val tempoMap = mutableListOf<TempoEvent>()
        val notes = mutableListOf<Note>()
        val nonNote = mutableListOf<RawEvent>()
        var title: String? = null

        // Pending note-ons keyed by (channel, midi). FIFO so overlapping notes
        // on the same key pair correctly.
        val pending = HashMap<Int, ArrayDeque<PendingNoteOn>>()

        var absTick = 0
        for (ev in events) {
            absTick += ev.deltaTime
            val msg = ev.message
            val statusByte = msg.statusByte.toInt() and 0xFF
            val statusNibble = statusByte and 0xF0
            val channel = msg.channel.toInt() and 0x0F
            val data1 = msg.msb.toInt() and 0xFF  // first data byte
            val data2 = msg.lsb.toInt() and 0xFF  // second data byte

            when {
                statusNibble == 0x90 && data2 > 0 -> {
                    // Note On with non-zero velocity
                    val key = pendingKey(channel, data1)
                    val q = pending.getOrPut(key) { ArrayDeque() }
                    q.addLast(PendingNoteOn(absTick, data2))
                }
                statusNibble == 0x80 || (statusNibble == 0x90 && data2 == 0) -> {
                    // Note Off (or Note On vel=0, which is SMF-equivalent)
                    val key = pendingKey(channel, data1)
                    val q = pending[key]
                    val pendingOn = q?.removeFirstOrNull()
                    if (pendingOn != null) {
                        val dur = (absTick - pendingOn.tick).coerceAtLeast(1)
                        notes += Note(
                            channel = channel,
                            midi = data1,
                            velocity = pendingOn.velocity.coerceIn(1, 127),
                            startTicks = pendingOn.tick,
                            durationTicks = dur,
                        )
                    }
                    // unmatched note-off: silently dropped
                }
                statusByte == 0xFF -> {
                    // Meta event
                    val metaType = msg.metaType.toInt() and 0xFF
                    val payload = (msg as? Midi1CompoundMessage)?.let { extractPayload(it) }
                    when (metaType) {
                        0x2F -> Unit // End of Track — writer regenerates
                        0x51 -> {
                            // Set Tempo: 3-byte big-endian microseconds per quarter
                            if (payload != null && payload.size >= 3) {
                                val us = ((payload[0].toInt() and 0xFF) shl 16) or
                                    ((payload[1].toInt() and 0xFF) shl 8) or
                                    (payload[2].toInt() and 0xFF)
                                tempoMap += TempoEvent(absTick, us)
                            }
                        }
                        0x03 -> {
                            // Track Name → use as title (first occurrence wins)
                            if (title == null && payload != null) {
                                title = String(payload, Charsets.UTF_8).trim().ifEmpty { null }
                            }
                            nonNote += RawEvent(absTick, statusByte, metaType, data1, data2, payload)
                        }
                        else -> {
                            nonNote += RawEvent(absTick, statusByte, metaType, data1, data2, payload)
                        }
                    }
                }
                statusByte == 0xF0 || statusByte == 0xF7 -> {
                    // SysEx
                    val payload = (msg as? Midi1CompoundMessage)?.let { extractPayload(it) }
                    nonNote += RawEvent(absTick, statusByte, -1, data1, data2, payload)
                }
                else -> {
                    // Other channel messages (CC, PC, pitch bend, aftertouch, etc.)
                    // Re-serialise the original status byte (with channel) so the writer can replay it verbatim.
                    nonNote += RawEvent(absTick, statusByte, -1, data1, data2, null)
                }
            }
        }

        // Sort by start tick for stable iteration in editor / writer.
        notes.sortWith(compareBy({ it.startTicks }, { it.midi }))

        // Ensure tempoMap has a tick-0 entry for the editor's "song tempo" control.
        if (tempoMap.none { it.tick == 0 }) {
            val firstUs = tempoMap.minByOrNull { it.tick }?.microsPerQuarter
                ?: MidiTiming.DEFAULT_MICROS_PER_QUARTER
            tempoMap.add(0, TempoEvent(0, firstUs))
        }
        tempoMap.sortBy { it.tick }
        nonNote.sortBy { it.tick }

        return MidiScore(
            ppq = ppq,
            title = title,
            tempoMap = tempoMap,
            notes = notes,
            nonNoteEvents = nonNote,
        )
    }

    private fun pendingKey(channel: Int, midi: Int): Int = (channel shl 8) or (midi and 0xFF)

    private fun extractPayload(msg: Midi1CompoundMessage): ByteArray? {
        val src = msg.extraData ?: return null
        val off = msg.extraDataOffset
        val len = msg.extraDataLength
        if (len <= 0 || off < 0 || off + len > src.size) return null
        return src.copyOfRange(off, off + len)
    }

    private data class PendingNoteOn(val tick: Int, val velocity: Int)
}
