package io.github.ranzlappen.synthpiano.data

import io.github.ranzlappen.synthpiano.audio.Adsr
import io.github.ranzlappen.synthpiano.audio.EnvelopeShape
import io.github.ranzlappen.synthpiano.audio.EnvelopeVertex
import io.github.ranzlappen.synthpiano.audio.FilterSettings
import io.github.ranzlappen.synthpiano.audio.VoiceShaping
import io.github.ranzlappen.synthpiano.audio.Waveform
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class SoundPreset(
    val name: String,
    val waveform: Waveform = Waveform.SINE,
    val attack: Float = 0.005f,
    val decay: Float = 0.150f,
    val sustain: Float = 0.700f,
    val release: Float = 0.250f,
    val curve: Float = 0f,
    val cutoffHz: Float = 18000f,
    val resonance: Float = 0f,
    val velocitySensitivity: Float = 1f,
    val glideSec: Float = 0f,
    val masterAmp: Float = 0.7f,
    val polyCompensation: Float = 1f,
    val drive: Float = 0f,
    val builtin: Boolean = false,
    /**
     * Multi-segment envelope shape captured by the interactive editor.
     * When null the preset is purely ADSR and falls back to the
     * (attack/decay/sustain/release/curve) fields above. Built-in
     * presets stay on the legacy ADSR fields so the source remains
     * compact; user presets created by the editor populate this.
     */
    val envelopeShape: List<EnvelopeVertex>? = null,
    val envelopeSustainIndex: Int = 3,
) {
    fun adsr(): Adsr = Adsr(
        attackSec = attack,
        decaySec = decay,
        sustain = sustain,
        releaseSec = release,
        curve = curve,
    )

    /**
     * Resolved envelope. Prefers [envelopeShape] when present; otherwise
     * builds the canonical 5-vertex ADSR shape from the legacy fields so
     * old presets and code paths see one consistent type.
     */
    fun envelope(): EnvelopeShape =
        envelopeShape?.let { EnvelopeShape(it, envelopeSustainIndex.coerceIn(0, it.lastIndex)) }
            ?: EnvelopeShape.fromAdsr(adsr())

    fun filter(): FilterSettings = FilterSettings(cutoffHz = cutoffHz, resonance = resonance)

    fun voiceShaping(): VoiceShaping = VoiceShaping(
        velocitySensitivity = velocitySensitivity,
        glideSec = glideSec,
    )

    companion object {
        val json: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val listSerializer = ListSerializer(serializer())
    }
}

/** Curated factory presets that ship with the app. */
object BuiltInPresets {
    val all: List<SoundPreset> = listOf(
        SoundPreset(
            name = "Pluck",
            waveform = Waveform.SAW,
            attack = 0.003f, decay = 0.180f, sustain = 0.0f, release = 0.220f,
            curve = 0.6f,
            cutoffHz = 2400f, resonance = 0.35f,
            velocitySensitivity = 0.9f, glideSec = 0f,
            masterAmp = 0.65f, builtin = true,
        ),
        SoundPreset(
            name = "Pad",
            waveform = Waveform.SINE,
            attack = 1.2f, decay = 0.6f, sustain = 0.85f, release = 1.4f,
            curve = -0.4f,
            cutoffHz = 1600f, resonance = 0.2f,
            velocitySensitivity = 0.5f, glideSec = 0f,
            masterAmp = 0.55f, builtin = true,
        ),
        SoundPreset(
            name = "Lead",
            waveform = Waveform.SQUARE,
            attack = 0.01f, decay = 0.2f, sustain = 0.7f, release = 0.18f,
            curve = 0.3f,
            cutoffHz = 5200f, resonance = 0.55f,
            velocitySensitivity = 0.8f, glideSec = 0.04f,
            masterAmp = 0.55f, builtin = true,
        ),
        SoundPreset(
            name = "Bell",
            waveform = Waveform.TRIANGLE,
            attack = 0.002f, decay = 0.9f, sustain = 0.0f, release = 1.2f,
            curve = 0.8f,
            cutoffHz = 9000f, resonance = 0.1f,
            velocitySensitivity = 0.95f, glideSec = 0f,
            masterAmp = 0.6f, builtin = true,
        ),
        SoundPreset(
            name = "Bass",
            waveform = Waveform.SQUARE,
            attack = 0.004f, decay = 0.25f, sustain = 0.6f, release = 0.15f,
            curve = 0.4f,
            cutoffHz = 900f, resonance = 0.45f,
            velocitySensitivity = 0.95f, glideSec = 0.02f,
            masterAmp = 0.7f, builtin = true,
        ),
        SoundPreset(
            name = "Organ",
            waveform = Waveform.SINE,
            attack = 0.005f, decay = 0.05f, sustain = 0.95f, release = 0.08f,
            curve = 0f,
            cutoffHz = 6500f, resonance = 0.0f,
            velocitySensitivity = 0.2f, glideSec = 0f,
            masterAmp = 0.6f, builtin = true,
        ),
        SoundPreset(
            name = "Stab",
            waveform = Waveform.SAW,
            attack = 0.005f, decay = 0.08f, sustain = 0.0f, release = 0.10f,
            curve = 0.7f,
            cutoffHz = 3500f, resonance = 0.6f,
            velocitySensitivity = 0.85f, glideSec = 0f,
            masterAmp = 0.65f, builtin = true,
        ),
        SoundPreset(
            name = "Soft Keys",
            waveform = Waveform.PIANO,
            attack = 0.005f, decay = 0.30f, sustain = 0.55f, release = 0.50f,
            curve = -0.2f,
            cutoffHz = 5500f, resonance = 0.05f,
            velocitySensitivity = 0.8f, glideSec = 0f,
            masterAmp = 0.65f, builtin = true,
        ),
        SoundPreset(
            name = "EP",
            waveform = Waveform.PIANO,
            attack = 0.002f, decay = 0.6f, sustain = 0.45f, release = 0.7f,
            curve = -0.3f,
            cutoffHz = 4500f, resonance = 0.05f,
            velocitySensitivity = 0.85f, glideSec = 0f,
            masterAmp = 0.65f, builtin = true,
        ),
        SoundPreset(
            name = "Strings",
            waveform = Waveform.SAW,
            attack = 0.35f, decay = 0.4f, sustain = 0.85f, release = 0.9f,
            curve = -0.2f,
            cutoffHz = 3500f, resonance = 0.15f,
            velocitySensitivity = 0.6f, glideSec = 0f,
            masterAmp = 0.55f, builtin = true,
        ),
        SoundPreset(
            name = "Brass",
            waveform = Waveform.SAW,
            attack = 0.03f, decay = 0.2f, sustain = 0.75f, release = 0.25f,
            curve = 0.1f,
            cutoffHz = 2800f, resonance = 0.3f,
            velocitySensitivity = 0.8f, glideSec = 0f,
            masterAmp = 0.6f, builtin = true,
        ),
        SoundPreset(
            name = "Marimba",
            waveform = Waveform.TRIANGLE,
            attack = 0.001f, decay = 0.35f, sustain = 0f, release = 0.4f,
            curve = 0.7f,
            cutoffHz = 7000f, resonance = 0.05f,
            velocitySensitivity = 0.95f, glideSec = 0f,
            masterAmp = 0.65f, builtin = true,
        ),
        SoundPreset(
            name = "Warm Pad",
            waveform = Waveform.TRIANGLE,
            attack = 1.5f, decay = 0.8f, sustain = 0.9f, release = 1.8f,
            curve = -0.5f,
            cutoffHz = 1200f, resonance = 0.25f,
            velocitySensitivity = 0.4f, glideSec = 0f,
            masterAmp = 0.5f, builtin = true,
        ),
        SoundPreset(
            name = "Choir",
            waveform = Waveform.SINE,
            attack = 0.6f, decay = 0.4f, sustain = 0.85f, release = 1.0f,
            curve = -0.3f,
            cutoffHz = 2200f, resonance = 0.1f,
            velocitySensitivity = 0.5f, glideSec = 0f,
            masterAmp = 0.55f, builtin = true,
        ),
        SoundPreset(
            name = "Sub Bass",
            waveform = Waveform.SINE,
            attack = 0.005f, decay = 0.25f, sustain = 0.85f, release = 0.2f,
            curve = 0.2f,
            cutoffHz = 700f, resonance = 0f,
            velocitySensitivity = 0.9f, glideSec = 0.03f,
            masterAmp = 0.7f, builtin = true,
        ),
        SoundPreset(
            name = "Hollow Lead",
            waveform = Waveform.SQUARE,
            attack = 0.008f, decay = 0.25f, sustain = 0.7f, release = 0.2f,
            curve = 0.2f,
            cutoffHz = 4200f, resonance = 0.4f,
            velocitySensitivity = 0.85f, glideSec = 0.05f,
            masterAmp = 0.55f, builtin = true,
        ),
        // --- New presets showcasing the Drive modifier ----------------
        SoundPreset(
            name = "Driven Lead",
            waveform = Waveform.SAW,
            attack = 0.005f, decay = 0.2f, sustain = 0.7f, release = 0.18f,
            curve = 0.3f,
            cutoffHz = 4500f, resonance = 0.5f,
            velocitySensitivity = 0.8f, glideSec = 0.03f,
            masterAmp = 0.5f, drive = 0.55f, builtin = true,
        ),
        SoundPreset(
            name = "Tube Bass",
            waveform = Waveform.SQUARE,
            attack = 0.004f, decay = 0.25f, sustain = 0.7f, release = 0.16f,
            curve = 0.4f,
            cutoffHz = 1100f, resonance = 0.4f,
            velocitySensitivity = 0.9f, glideSec = 0.02f,
            masterAmp = 0.6f, drive = 0.4f, builtin = true,
        ),
        SoundPreset(
            name = "Crunch Stab",
            waveform = Waveform.SAW,
            attack = 0.003f, decay = 0.10f, sustain = 0.0f, release = 0.12f,
            curve = 0.7f,
            cutoffHz = 3200f, resonance = 0.55f,
            velocitySensitivity = 0.85f, glideSec = 0f,
            masterAmp = 0.55f, drive = 0.65f, builtin = true,
        ),
        SoundPreset(
            name = "Warm Rhodes",
            waveform = Waveform.PIANO,
            attack = 0.003f, decay = 0.7f, sustain = 0.5f, release = 0.7f,
            curve = -0.2f,
            cutoffHz = 4800f, resonance = 0.05f,
            velocitySensitivity = 0.85f, glideSec = 0f,
            masterAmp = 0.6f, drive = 0.18f, builtin = true,
        ),
        SoundPreset(
            name = "Fuzz Pad",
            waveform = Waveform.TRIANGLE,
            attack = 0.9f, decay = 0.6f, sustain = 0.85f, release = 1.4f,
            curve = -0.3f,
            cutoffHz = 1800f, resonance = 0.25f,
            velocitySensitivity = 0.4f, glideSec = 0f,
            masterAmp = 0.45f, drive = 0.35f, builtin = true,
        ),
        SoundPreset(
            name = "Glide Bass",
            waveform = Waveform.SAW,
            attack = 0.002f, decay = 0.30f, sustain = 0.65f, release = 0.18f,
            curve = 0.3f,
            cutoffHz = 1400f, resonance = 0.5f,
            velocitySensitivity = 0.95f, glideSec = 0.18f,
            masterAmp = 0.6f, drive = 0.25f, builtin = true,
        ),
        SoundPreset(
            name = "Space Pad",
            waveform = Waveform.SINE,
            attack = 1.8f, decay = 0.6f, sustain = 0.95f, release = 2.2f,
            curve = -0.5f,
            cutoffHz = 2400f, resonance = 0.3f,
            velocitySensitivity = 0.35f, glideSec = 0f,
            masterAmp = 0.5f, drive = 0f, builtin = true,
        ),
        SoundPreset(
            name = "Saw Pluck",
            waveform = Waveform.SAW,
            attack = 0.001f, decay = 0.16f, sustain = 0.0f, release = 0.18f,
            curve = 0.7f,
            cutoffHz = 2000f, resonance = 0.6f,
            velocitySensitivity = 0.95f, glideSec = 0f,
            masterAmp = 0.6f, drive = 0.1f, builtin = true,
        ),
    )
}
