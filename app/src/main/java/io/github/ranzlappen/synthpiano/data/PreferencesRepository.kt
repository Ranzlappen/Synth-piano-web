package io.github.ranzlappen.synthpiano.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.ranzlappen.synthpiano.audio.Adsr
import io.github.ranzlappen.synthpiano.audio.FilterSettings
import io.github.ranzlappen.synthpiano.audio.VoiceShaping
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
    val ADSR_CURVE = floatPreferencesKey("adsr_curve")
    val FILTER_CUTOFF = floatPreferencesKey("filter_cutoff_hz")
    val FILTER_RES = floatPreferencesKey("filter_resonance")
    val VEL_SENS = floatPreferencesKey("velocity_sensitivity")
    val GLIDE_SEC = floatPreferencesKey("glide_sec")
    val USER_PRESETS_JSON = stringPreferencesKey("user_presets_json")
    val LAST_PRESET_NAME = stringPreferencesKey("last_preset_name")
    val OCTAVE = intPreferencesKey("octave")
    val KEYBOARD_LEFT_C = intPreferencesKey("keyboard_left_c")
    val KEYMAP_JSON = stringPreferencesKey("keymap_json")
    val LAST_SCORE_URI = stringPreferencesKey("last_score_uri")
    val TEMPO_BPM = intPreferencesKey("tempo_bpm")
    val THEME_ACCENT = stringPreferencesKey("theme_accent")
    val CHORD_MOD_STICKY = stringPreferencesKey("chord_mod_sticky")
    val CHORD_INV_STICKY = stringPreferencesKey("chord_inv_sticky")
    val PIANO_ZOOM = floatPreferencesKey("piano_zoom")
    val PIANO_SCROLL_X = intPreferencesKey("piano_scroll_x")
    val COMPOSER_EDITOR_W = floatPreferencesKey("composer_editor_weight")
    val COMPOSER_EDITOR_H = floatPreferencesKey("composer_editor_height")
    val KEYBOARD_LAYOUT_JSON = stringPreferencesKey("keyboard_layout_json")
    val USER_LAYOUTS_JSON = stringPreferencesKey("user_layouts_json")
    val HAS_SEEN_LAYOUT_ONBOARDING = booleanPreferencesKey("has_seen_layout_onboarding")
    val LANGUAGE_TAG = stringPreferencesKey("language_tag")
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
            curve = (prefs[Keys.ADSR_CURVE] ?: 0f).coerceIn(-1f, 1f),
        )
    }

    val masterAmp: Flow<Float> =
        context.dataStore.data.map { it[Keys.MASTER_AMP] ?: 0.7f }

    val filter: Flow<FilterSettings> = context.dataStore.data.map { prefs ->
        FilterSettings(
            cutoffHz = (prefs[Keys.FILTER_CUTOFF] ?: 18000f).coerceIn(20f, 20000f),
            resonance = (prefs[Keys.FILTER_RES] ?: 0f).coerceIn(0f, 1f),
        )
    }

    val voiceShaping: Flow<VoiceShaping> = context.dataStore.data.map { prefs ->
        VoiceShaping(
            velocitySensitivity = (prefs[Keys.VEL_SENS] ?: 1f).coerceIn(0f, 1f),
            glideSec = (prefs[Keys.GLIDE_SEC] ?: 0f).coerceIn(0f, 0.5f),
        )
    }

    val userPresetsJson: Flow<String?> =
        context.dataStore.data.map { it[Keys.USER_PRESETS_JSON] }

    val lastPresetName: Flow<String?> =
        context.dataStore.data.map { it[Keys.LAST_PRESET_NAME] }

    val octave: Flow<Int> =
        context.dataStore.data.map { it[Keys.OCTAVE] ?: 0 }

    /** MIDI note number of the leftmost C the piano keyboard should show. */
    val keyboardLeftC: Flow<Int> =
        context.dataStore.data.map { it[Keys.KEYBOARD_LEFT_C] ?: 48 }

    val keymapJson: Flow<String?> =
        context.dataStore.data.map { it[Keys.KEYMAP_JSON] }

    val lastScoreUri: Flow<String?> =
        context.dataStore.data.map { it[Keys.LAST_SCORE_URI] }

    val tempoBpm: Flow<Int> =
        context.dataStore.data.map { it[Keys.TEMPO_BPM] ?: 120 }

    val themeAccent: Flow<String> =
        context.dataStore.data.map { it[Keys.THEME_ACCENT] ?: "AURORA" }

    /** Sticky chord modifiers (LOCK row of the perform-tab modifier strip). */
    val chordModSticky: Flow<Set<ChordQuality>> =
        context.dataStore.data.map { parseChordModifierSet(it[Keys.CHORD_MOD_STICKY]) }

    /** Sticky chord inversion (LOCK row's inversion column). */
    val chordInvSticky: Flow<ChordInversion> =
        context.dataStore.data.map { prefs ->
            runCatching {
                ChordInversion.valueOf(prefs[Keys.CHORD_INV_STICKY] ?: ChordInversion.NONE.name)
            }.getOrDefault(ChordInversion.NONE)
        }

    /** Piano keyboard zoom factor (white-key width multiplier). */
    val pianoZoom: Flow<Float> =
        context.dataStore.data.map { (it[Keys.PIANO_ZOOM] ?: 1.0f).coerceIn(0.1f, 2.0f) }

    /** Piano keyboard horizontal scroll offset in px; restored across sessions. */
    val pianoScrollX: Flow<Int> =
        context.dataStore.data.map { (it[Keys.PIANO_SCROLL_X] ?: 0).coerceAtLeast(0) }

    /** Composer editor pane weight in side-by-side layout (>=900dp). */
    val composerEditorWeight: Flow<Float> =
        context.dataStore.data.map { (it[Keys.COMPOSER_EDITOR_W] ?: 0.667f).coerceIn(0.2f, 0.85f) }

    /** Composer editor pane height (dp) in stacked layout (<900dp). */
    val composerEditorHeightDp: Flow<Float> =
        context.dataStore.data.map { (it[Keys.COMPOSER_EDITOR_H] ?: 600f).coerceIn(120f, 4000f) }

    /** Active on-screen keyboard layout (panels + positions). */
    val keyboardLayout: Flow<KeyboardLayout> =
        context.dataStore.data.map { prefs ->
            prefs[Keys.KEYBOARD_LAYOUT_JSON]
                ?.let(::parseKeyboardLayoutJson)
                ?: BuiltInLayouts.DEFAULT
        }

    /** JSON for the list of user-saved (named) layouts. */
    val userLayoutsJson: Flow<String?> =
        context.dataStore.data.map { it[Keys.USER_LAYOUTS_JSON] }

    /** Whether the first-launch layout onboarding dialog has been seen. */
    val hasSeenLayoutOnboarding: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.HAS_SEEN_LAYOUT_ONBOARDING] ?: false }

    /** User-selected language tag (e.g. "en", "de", "es", "fr") or null = follow system. */
    val languageTag: Flow<String?> =
        context.dataStore.data.map { it[Keys.LANGUAGE_TAG] }

    suspend fun setWaveform(w: Waveform) =
        edit { it[Keys.WAVEFORM] = w.name }

    suspend fun setAdsr(a: Adsr) = edit {
        it[Keys.ATTACK] = a.attackSec
        it[Keys.DECAY] = a.decaySec
        it[Keys.SUSTAIN] = a.sustain
        it[Keys.RELEASE] = a.releaseSec
        it[Keys.ADSR_CURVE] = a.curve
    }

    suspend fun setMasterAmp(v: Float) = edit { it[Keys.MASTER_AMP] = v }

    suspend fun setFilter(f: FilterSettings) = edit {
        it[Keys.FILTER_CUTOFF] = f.cutoffHz
        it[Keys.FILTER_RES] = f.resonance
    }

    suspend fun setVoiceShaping(s: VoiceShaping) = edit {
        it[Keys.VEL_SENS] = s.velocitySensitivity
        it[Keys.GLIDE_SEC] = s.glideSec
    }

    suspend fun setUserPresetsJson(json: String) = edit { it[Keys.USER_PRESETS_JSON] = json }

    suspend fun setLastPresetName(name: String?) = edit {
        if (name == null) it.remove(Keys.LAST_PRESET_NAME) else it[Keys.LAST_PRESET_NAME] = name
    }

    suspend fun setOctave(v: Int) = edit { it[Keys.OCTAVE] = v }

    suspend fun setKeyboardLeftC(midi: Int) = edit { it[Keys.KEYBOARD_LEFT_C] = midi }

    suspend fun setKeymapJson(json: String) = edit { it[Keys.KEYMAP_JSON] = json }

    suspend fun setLastScoreUri(uri: String?) = edit {
        if (uri == null) it.remove(Keys.LAST_SCORE_URI) else it[Keys.LAST_SCORE_URI] = uri
    }

    suspend fun setTempoBpm(bpm: Int) = edit { it[Keys.TEMPO_BPM] = bpm.coerceIn(20, 300) }

    suspend fun setThemeAccent(name: String) = edit { it[Keys.THEME_ACCENT] = name }

    suspend fun setChordModSticky(s: Set<ChordQuality>) =
        edit { it[Keys.CHORD_MOD_STICKY] = s.toPrefString() }

    suspend fun setChordInvSticky(inv: ChordInversion) =
        edit { it[Keys.CHORD_INV_STICKY] = inv.name }

    suspend fun setPianoZoom(z: Float) =
        edit { it[Keys.PIANO_ZOOM] = z.coerceIn(0.1f, 2.0f) }

    suspend fun setPianoScrollX(px: Int) =
        edit { it[Keys.PIANO_SCROLL_X] = px.coerceAtLeast(0) }

    suspend fun setComposerEditorWeight(w: Float) =
        edit { it[Keys.COMPOSER_EDITOR_W] = w.coerceIn(0.2f, 0.85f) }

    suspend fun setComposerEditorHeightDp(dp: Float) =
        edit { it[Keys.COMPOSER_EDITOR_H] = dp.coerceIn(120f, 4000f) }

    suspend fun setKeyboardLayout(layout: KeyboardLayout) = edit {
        it[Keys.KEYBOARD_LAYOUT_JSON] = layout.toJsonString()
    }

    suspend fun clearKeyboardLayout() = edit { it.remove(Keys.KEYBOARD_LAYOUT_JSON) }

    suspend fun setUserLayoutsJson(json: String) = edit { it[Keys.USER_LAYOUTS_JSON] = json }

    suspend fun setHasSeenLayoutOnboarding(v: Boolean) = edit {
        it[Keys.HAS_SEEN_LAYOUT_ONBOARDING] = v
    }

    suspend fun setLanguageTag(tag: String?) = edit {
        if (tag.isNullOrBlank()) it.remove(Keys.LANGUAGE_TAG) else it[Keys.LANGUAGE_TAG] = tag
    }

    /** Synchronous snapshot of the keymap JSON for non-suspending init paths. */
    fun blockingKeymapJson(): String? = runBlocking { keymapJson.first() }

    /** Synchronous snapshot of the language tag for use during Activity startup. */
    fun blockingLanguageTag(): String? = runBlocking { languageTag.first() }

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
        Keys.MASTER_AMP, Keys.ADSR_CURVE, Keys.FILTER_CUTOFF, Keys.FILTER_RES,
        Keys.VEL_SENS, Keys.GLIDE_SEC, Keys.USER_PRESETS_JSON, Keys.LAST_PRESET_NAME,
        Keys.OCTAVE, Keys.KEYBOARD_LEFT_C, Keys.KEYMAP_JSON,
        Keys.LAST_SCORE_URI, Keys.TEMPO_BPM, Keys.THEME_ACCENT,
        Keys.CHORD_MOD_STICKY, Keys.CHORD_INV_STICKY, Keys.PIANO_ZOOM,
        Keys.PIANO_SCROLL_X,
        Keys.COMPOSER_EDITOR_W, Keys.COMPOSER_EDITOR_H,
        Keys.KEYBOARD_LAYOUT_JSON, Keys.USER_LAYOUTS_JSON,
        Keys.HAS_SEEN_LAYOUT_ONBOARDING, Keys.LANGUAGE_TAG,
    )
}
