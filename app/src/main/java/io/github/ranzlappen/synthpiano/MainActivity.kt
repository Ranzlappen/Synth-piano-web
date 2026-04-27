package io.github.ranzlappen.synthpiano

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.ranzlappen.synthpiano.audio.SynthController
import io.github.ranzlappen.synthpiano.crash.CrashReporter
import io.github.ranzlappen.synthpiano.input.HwKeyboardMapper
import io.github.ranzlappen.synthpiano.midi.MidiManager
import io.github.ranzlappen.synthpiano.ui.SynthAppRoot
import io.github.ranzlappen.synthpiano.ui.theme.SynthPianoTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var synth: SynthController
    private lateinit var midi: MidiManager
    private lateinit var hwKeys: HwKeyboardMapper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // If the app crashed on a previous launch, fire the share sheet for
        // the report immediately -- BEFORE Compose runs -- so the user can
        // surface the trace even if the underlying composition crash is
        // still happening on this launch. We delete the file after firing
        // the intent to avoid prompting on every subsequent launch; if the
        // crash recurs, a fresh file will be written.
        if (CrashReporter.hasReport(this)) {
            CrashReporter.shareIntent(this)?.let { intent ->
                Toast.makeText(
                    this,
                    "Crash on previous launch — sharing report",
                    Toast.LENGTH_LONG,
                ).show()
                startActivity(Intent.createChooser(intent, "Share crash report"))
            }
            CrashReporter.clear(this)
        }

        val app = SynthApp.get()
        synth = app.synth
        midi = MidiManager(applicationContext, synth)
        hwKeys = HwKeyboardMapper(synth, app.prefs)

        setContent {
            SynthPianoTheme {
                SynthAppRoot(
                    synth = synth,
                    prefs = app.prefs,
                    midi = midi,
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

        // Honor the USB-MIDI launch intent if the activity was started by
        // a USB attach. MidiManager's onUsbDeviceAttached is idempotent.
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
        // Don't fully stop the engine on a brief pause; it incurs Oboe
        // open cost on resume. allNotesOff is enough.
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
