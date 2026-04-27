package io.github.ranzlappen.synthpiano

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.ranzlappen.synthpiano.audio.SynthController
import io.github.ranzlappen.synthpiano.input.HwKeyboardMapper
import io.github.ranzlappen.synthpiano.midi.MidiManager
import io.github.ranzlappen.synthpiano.ui.SynthAppRoot
import io.github.ranzlappen.synthpiano.ui.theme.SynthPianoTheme
import io.github.ranzlappen.synthpiano.ui.theme.ThemeAccent
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var synth: SynthController
    private lateinit var midi: MidiManager
    private lateinit var hwKeys: HwKeyboardMapper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val app = SynthApp.get()
        synth = app.synth
        midi = MidiManager(applicationContext, synth)
        hwKeys = HwKeyboardMapper(synth, app.prefs)

        setContent {
            val accentName by app.prefs.themeAccent.collectAsState(initial = "AURORA")
            val accent = ThemeAccent.fromName(accentName)
            SynthPianoTheme(accent = accent) {
                SynthAppRoot(
                    synth = synth,
                    prefs = app.prefs,
                    midi = midi,
                    hwKeys = hwKeys,
                )
            }
        }

        // Engine starts when the activity is in the foreground; it's paused
        // when backgrounded so we don't burn CPU/battery on the audio thread.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                synth.start()
                midi.attach()
                hwKeys.refreshFromPrefs()
            }
        }

        intent?.let(::handleIntent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == "android.hardware.usb.action.USB_DEVICE_ATTACHED") {
            midi.refreshDevices()
        }
    }

    override fun onPause() {
        super.onPause()
        synth.allNotesOff()
    }

    override fun onDestroy() {
        midi.detach()
        synth.stop()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return hwKeys.dispatch(event) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return hwKeys.dispatch(event) || super.onKeyUp(keyCode, event)
    }
}
