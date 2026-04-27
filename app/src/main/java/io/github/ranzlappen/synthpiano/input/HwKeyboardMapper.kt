package io.github.ranzlappen.synthpiano.input

import android.view.KeyEvent
import io.github.ranzlappen.synthpiano.audio.NoteSource
import io.github.ranzlappen.synthpiano.audio.SynthController
import io.github.ranzlappen.synthpiano.data.PreferencesRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.runBlocking

/**
 * Translates Android KeyEvents from a hardware keyboard into note on/off
 * calls. Defaults match the Python source's German-layout mapping but the
 * user can rebind any key in Settings.
 *
 * Each key maps to a MIDI offset relative to a base note (default C4 = 60).
 * The mapper handles repeat events from auto-repeat by ignoring repeat
 * note-on events for keys already pressed.
 */
class HwKeyboardMapper(
    private val synth: SynthController,
    private val prefs: PreferencesRepository,
) {

    @Serializable
    data class Binding(val keyCode: Int, val midiOffset: Int)

    /** Mutable runtime map of keyCode -> midi offset relative to baseNote. */
    private var keyToOffset: Map<Int, Int> = defaultBindings()
    private val baseNote = 60   // C4
    private val held = mutableSetOf<Int>()

    // While set, ACTION_DOWN events go to this callback instead of
    // triggering notes — used by the keymap editor's capture mode.
    private var captureCallback: ((keyCode: Int) -> Unit)? = null

    fun refreshFromPrefs() {
        val json = prefs.blockingKeymapJson()
        if (json.isNullOrBlank()) {
            keyToOffset = defaultBindings()
            return
        }
        keyToOffset = runCatching {
            jsonCodec.decodeFromString<List<Binding>>(json)
                .associate { it.keyCode to it.midiOffset }
        }.getOrElse { defaultBindings() }
    }

    fun dispatch(event: KeyEvent): Boolean {
        // Only react to actual hardware keys, not soft-keyboard input.
        val src = event.source
        if (src and android.view.InputDevice.SOURCE_KEYBOARD == 0) return false

        // Capture mode short-circuits binding edits before we touch the
        // synth. Lift any held notes first so a captured key isn't left
        // playing if the user binds while a note is sustained.
        captureCallback?.let { cb ->
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                allNotesOffInternal()
                cb(event.keyCode)
            }
            return true
        }

        val offset = keyToOffset[event.keyCode] ?: return false
        val midi = baseNote + offset
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0 && held.add(event.keyCode)) {
                    synth.noteOn(midi, source = NoteSource.HW_KEYBOARD)
                }
                true
            }
            KeyEvent.ACTION_UP -> {
                if (held.remove(event.keyCode)) {
                    synth.noteOff(midi)
                }
                true
            }
            else -> false
        }
    }

    fun setCaptureCallback(cb: ((Int) -> Unit)?) {
        captureCallback = cb
        if (cb != null) allNotesOffInternal()
    }

    private fun allNotesOffInternal() {
        if (held.isNotEmpty()) {
            synth.allNotesOff()
            held.clear()
        }
    }

    suspend fun saveCurrentBindings() {
        val list = keyToOffset.map { (k, v) -> Binding(k, v) }
        prefs.setKeymapJson(jsonCodec.encodeToString(jsonSerializer, list))
    }

    fun setBinding(keyCode: Int, midiOffset: Int) {
        keyToOffset = keyToOffset.toMutableMap().also { it[keyCode] = midiOffset }
    }

    fun clearBinding(keyCode: Int) {
        keyToOffset = keyToOffset.toMutableMap().also { it.remove(keyCode) }
    }

    fun bindingsSnapshot(): Map<Int, Int> = keyToOffset

    fun resetToDefaults() { keyToOffset = defaultBindings() }

    companion object {
        private val jsonCodec = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        private val jsonSerializer = kotlinx.serialization.builtins.ListSerializer(Binding.serializer())

        /**
         * Defaults mirror the Python source's QWERTY mapping:
         *   White keys ASDFGHJKL  (C major scale starting at C4)
         *   Black keys WRTUIOP    (sharps/flats interleaved)
         * Unmapped keys (Q, E, Y, etc.) are left for chord pads.
         */
        fun defaultBindings(): Map<Int, Int> = mapOf(
            // White keys
            KeyEvent.KEYCODE_A to 0,    // C
            KeyEvent.KEYCODE_S to 2,    // D
            KeyEvent.KEYCODE_D to 4,    // E
            KeyEvent.KEYCODE_F to 5,    // F
            KeyEvent.KEYCODE_G to 7,    // G
            KeyEvent.KEYCODE_H to 9,    // A
            KeyEvent.KEYCODE_J to 11,   // B
            KeyEvent.KEYCODE_K to 12,   // C5
            KeyEvent.KEYCODE_L to 14,   // D5
            // Black keys
            KeyEvent.KEYCODE_W to 1,    // C#
            KeyEvent.KEYCODE_E to 3,    // D# (Python uses R; both reasonable, this matches QWERTY visual)
            KeyEvent.KEYCODE_T to 6,    // F#
            KeyEvent.KEYCODE_Y to 8,    // G#
            KeyEvent.KEYCODE_U to 10,   // A#
            KeyEvent.KEYCODE_O to 13,   // C#5
            KeyEvent.KEYCODE_P to 15,   // D#5
        )
    }
}
