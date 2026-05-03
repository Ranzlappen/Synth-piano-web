package io.github.ranzlappen.synthpiano.data

import io.github.ranzlappen.synthpiano.audio.SynthController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * User-defined presets are persisted as a single JSON-serialized array under
 * one DataStore key (see [PreferencesRepository.userPresetsJson]). Built-ins
 * are compiled-in via [BuiltInPresets] and never written to DataStore.
 */
class PresetRepository(private val prefs: PreferencesRepository) {

    val userPresets: Flow<List<SoundPreset>> = prefs.userPresetsJson.map { raw ->
        if (raw.isNullOrBlank()) emptyList()
        else runCatching {
            SoundPreset.json.decodeFromString(SoundPreset.listSerializer, raw)
                .map { it.copy(builtin = false) }
        }.getOrElse { emptyList() }
    }

    suspend fun saveUser(preset: SoundPreset) {
        val name = preset.name.trim()
        if (name.isEmpty()) return
        val current = userPresets.first().toMutableList()
        val idx = current.indexOfFirst { it.name == name }
        val replacement = preset.copy(name = name, builtin = false)
        if (idx >= 0) current[idx] = replacement else current.add(replacement)
        writeAll(current)
    }

    suspend fun renameUser(oldName: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty() || trimmed == oldName) return
        val current = userPresets.first().toMutableList()
        val idx = current.indexOfFirst { it.name == oldName }
        if (idx < 0) return
        // Disallow collision with another user preset.
        if (current.any { it.name == trimmed && it.name != oldName }) return
        current[idx] = current[idx].copy(name = trimmed)
        writeAll(current)
        if (prefs.lastPresetName.first() == oldName) prefs.setLastPresetName(trimmed)
    }

    suspend fun deleteUser(name: String) {
        val current = userPresets.first().filter { it.name != name }
        writeAll(current)
        if (prefs.lastPresetName.first() == name) prefs.setLastPresetName(null)
    }

    /**
     * Captures the current synth state into a [SoundPreset]. If the live
     * envelope shape is non-canonical (added vertices, custom sustain
     * pin, etc.) it's persisted via the [SoundPreset.envelopeShape]
     * field so the editor's full multi-segment work isn't lost on save.
     * Canonical 5-vertex ADSR shapes leave the field null so user JSON
     * stays compact for ordinary presets.
     */
    fun snapshot(synth: SynthController, name: String): SoundPreset {
        val a = synth.adsr.value
        val f = synth.filter.value
        val v = synth.voiceShaping.value
        val shape = synth.envelopeShape.value
        return SoundPreset(
            name = name,
            waveform = synth.waveform.value,
            attack = a.attackSec, decay = a.decaySec, sustain = a.sustain,
            release = a.releaseSec, curve = a.curve,
            cutoffHz = f.cutoffHz, resonance = f.resonance,
            velocitySensitivity = v.velocitySensitivity, glideSec = v.glideSec,
            masterAmp = synth.masterAmp.value,
            polyCompensation = synth.polyComp.value,
            drive = synth.drive.value,
            envelopeShape = if (shape.isCanonicalAdsr()) null else shape.vertices,
            envelopeSustainIndex = shape.sustainIndex,
            builtin = false,
        )
    }

    /** Push a preset into the synth and remember it as the last selection. */
    suspend fun apply(synth: SynthController, preset: SoundPreset) {
        synth.setWaveform(preset.waveform)
        // Prefer the multi-segment shape when present; falls back to the
        // legacy ADSR projection otherwise.
        if (preset.envelopeShape != null) {
            synth.setEnvelopeShape(preset.envelope())
        } else {
            synth.setAdsr(preset.attack, preset.decay, preset.sustain, preset.release, preset.curve)
        }
        synth.setFilter(preset.cutoffHz, preset.resonance)
        synth.setVelocitySensitivity(preset.velocitySensitivity)
        synth.setGlideSec(preset.glideSec)
        synth.setMasterAmp(preset.masterAmp)
        synth.setPolyCompensation(preset.polyCompensation)
        synth.setDrive(preset.drive)
        prefs.setLastPresetName(preset.name)
        prefs.setWaveform(preset.waveform)
        prefs.setAdsr(preset.adsr())
        prefs.setFilter(preset.filter())
        prefs.setVoiceShaping(preset.voiceShaping())
        prefs.setMasterAmp(preset.masterAmp)
    }

    /** On launch: re-apply the previously selected preset, if any. */
    suspend fun applyLastIfAny(synth: SynthController) {
        val name = prefs.lastPresetName.first() ?: return
        val all = BuiltInPresets.all + userPresets.first()
        val match = all.firstOrNull { it.name == name } ?: return
        apply(synth, match)
    }

    private suspend fun writeAll(list: List<SoundPreset>) {
        prefs.setUserPresetsJson(
            SoundPreset.json.encodeToString(SoundPreset.listSerializer, list)
        )
    }
}
