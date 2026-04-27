package io.github.ranzlappen.synthpiano.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.ranzlappen.synthpiano.audio.Adsr
import io.github.ranzlappen.synthpiano.audio.Waveform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore(name = "synth_settings")

private object Keys {
    val WAVEFORM = stringPreferencesKey("waveform")
    val ATTACK = floatPreferencesKey("adsr_attack")
    val DECAY = floatPreferencesKey("adsr_decay")
    val SUSTAIN = floatPreferencesKey("adsr_sustain")
    val RELEASE = floatPreferencesKey("adsr_release")
    val MASTER_AMP = floatPreferencesKey("master_amp")
    val OCTAVE = intPreferencesKey("octave")
    val KEYMAP_JSON = stringPreferencesKey("keymap_json")
    val CHORD_PADS_JSON = stringPreferencesKey("chord_pads_json")
    val LAST_SCORE_URI = stringPreferencesKey("last_score_uri")
    val TEMPO_BPM = intPreferencesKey("tempo_bpm")
}

/**
 * DataStore-backed persistence for user-tunable settings.
 *
 * Reads expose Flows; writes are suspend functions. UI uses Compose's
 * collectAsState. Pre-flow snapshots are available via blockingSnapshot()
 * for code paths that can't suspend (HwKeyboardMapper init).
 */
class PreferencesRepository(private val context: Context) {

    val waveform: Flow<Waveform> =
        context.dataStore.data.map { prefs ->
            runCatching { Waveform.valueOf(prefs[Keys.WAVEFORM] ?: Waveform.SINE.name) }
                .getOrDefault(Waveform.SINE)
        }

    val adsr: Flow<Adsr> = context.dataStore.data.map { prefs ->
        Adsr(
            attackSec = prefs[Keys.ATTACK] ?: 0.005f,
            decaySec = prefs[Keys.DECAY] ?: 0.150f,
            sustain = prefs[Keys.SUSTAIN] ?: 0.700f,
            releaseSec = prefs[Keys.RELEASE] ?: 0.250f,
        )
    }

    val masterAmp: Flow<Float> =
        context.dataStore.data.map { it[Keys.MASTER_AMP] ?: 0.7f }

    val octave: Flow<Int> =
        context.dataStore.data.map { it[Keys.OCTAVE] ?: 0 }

    val keymapJson: Flow<String?> =
        context.dataStore.data.map { it[Keys.KEYMAP_JSON] }

    val chordPadsJson: Flow<String?> =
        context.dataStore.data.map { it[Keys.CHORD_PADS_JSON] }

    val lastScoreUri: Flow<String?> =
        context.dataStore.data.map { it[Keys.LAST_SCORE_URI] }

    val tempoBpm: Flow<Int> =
        context.dataStore.data.map { it[Keys.TEMPO_BPM] ?: 120 }

    suspend fun setWaveform(w: Waveform) =
        edit { it[Keys.WAVEFORM] = w.name }

    suspend fun setAdsr(a: Adsr) = edit {
        it[Keys.ATTACK] = a.attackSec
        it[Keys.DECAY] = a.decaySec
        it[Keys.SUSTAIN] = a.sustain
        it[Keys.RELEASE] = a.releaseSec
    }

    suspend fun setMasterAmp(v: Float) = edit { it[Keys.MASTER_AMP] = v }

    suspend fun setOctave(v: Int) = edit { it[Keys.OCTAVE] = v }

    suspend fun setKeymapJson(json: String) = edit { it[Keys.KEYMAP_JSON] = json }

    suspend fun setChordPadsJson(json: String) = edit { it[Keys.CHORD_PADS_JSON] = json }

    suspend fun setLastScoreUri(uri: String?) = edit {
        if (uri == null) it.remove(Keys.LAST_SCORE_URI) else it[Keys.LAST_SCORE_URI] = uri
    }

    suspend fun setTempoBpm(bpm: Int) = edit { it[Keys.TEMPO_BPM] = bpm.coerceIn(20, 300) }

    /** Synchronous snapshot of the keymap JSON for non-suspending init paths. */
    fun blockingKeymapJson(): String? = runBlocking { keymapJson.first() }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { prefs -> block(prefs) }
    }

    /** Test/debug helper: erase everything. */
    suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }

    @Suppress("unused")
    fun keysSnapshot(): Set<Preferences.Key<*>> = setOf(
        Keys.WAVEFORM, Keys.ATTACK, Keys.DECAY, Keys.SUSTAIN, Keys.RELEASE,
        Keys.MASTER_AMP, Keys.OCTAVE, Keys.KEYMAP_JSON, Keys.CHORD_PADS_JSON,
        Keys.LAST_SCORE_URI, Keys.TEMPO_BPM,
    )
}
