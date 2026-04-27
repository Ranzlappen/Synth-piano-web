package io.github.ranzlappen.synthpiano.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import io.github.ranzlappen.synthpiano.audio.SynthController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Connects to any USB MIDI device exposed via android.media.midi and
 * forwards Note On / Note Off / All Notes Off to [SynthController].
 *
 * [attach] is idempotent — call it from your activity's STARTED state.
 * [detach] releases all open MIDI ports and devices.
 */
class MidiManager(
    private val context: Context,
    private val synth: SynthController,
) {
    private val sysManager: MidiManager? =
        context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
    private val handler = Handler(Looper.getMainLooper())

    private val openDevices = mutableListOf<MidiDevice>()
    private val openPorts = mutableListOf<MidiOutputPort>()

    private val _connectedDeviceNames = MutableStateFlow<List<String>>(emptyList())
    val connectedDeviceNames: StateFlow<List<String>> = _connectedDeviceNames.asStateFlow()

    private val deviceCallback = object : MidiManager.DeviceCallback() {
        override fun onDeviceAdded(device: MidiDeviceInfo) { handler.post { connect(device) } }
        override fun onDeviceRemoved(device: MidiDeviceInfo) { handler.post { refreshDevices() } }
    }

    fun attach() {
        val mgr = sysManager ?: return
        // Get a snapshot of currently attached devices.
        for (info in mgr.devices) connect(info)
        @Suppress("DEPRECATION")
        mgr.registerDeviceCallback(deviceCallback, handler)
    }

    fun detach() {
        sysManager?.unregisterDeviceCallback(deviceCallback)
        for (p in openPorts) try { p.close() } catch (_: Throwable) {}
        openPorts.clear()
        for (d in openDevices) try { d.close() } catch (_: Throwable) {}
        openDevices.clear()
        _connectedDeviceNames.value = emptyList()
    }

    fun refreshDevices() {
        val mgr = sysManager ?: return
        for (p in openPorts) try { p.close() } catch (_: Throwable) {}
        for (d in openDevices) try { d.close() } catch (_: Throwable) {}
        openPorts.clear()
        openDevices.clear()
        _connectedDeviceNames.value = emptyList()
        for (info in mgr.devices) connect(info)
    }

    private fun connect(info: MidiDeviceInfo) {
        val mgr = sysManager ?: return
        if (info.outputPortCount == 0) return  // we only care about devices that send to us
        mgr.openDevice(info, { device ->
            if (device == null) return@openDevice
            handler.post {
                openDevices.add(device)
                val output = device.openOutputPort(0) ?: return@post
                output.connect(SynthMidiReceiver(synth))
                openPorts.add(output)
                val name = info.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "MIDI device"
                _connectedDeviceNames.value = _connectedDeviceNames.value + name
            }
        }, handler)
    }
}

/** Parses raw MIDI bytes and maps Note On/Off + CC123 to the synth. */
private class SynthMidiReceiver(private val synth: SynthController) : MidiReceiver() {
    override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
        var i = 0
        while (i < count) {
            val b0 = msg[offset + i].toInt() and 0xFF
            val status = b0 and 0xF0
            when (status) {
                0x90 -> { // Note On
                    if (i + 2 >= count) return
                    val note = msg[offset + i + 1].toInt() and 0x7F
                    val vel = msg[offset + i + 2].toInt() and 0x7F
                    if (vel > 0) synth.noteOn(note, vel / 127f) else synth.noteOff(note)
                    i += 3
                }
                0x80 -> { // Note Off
                    if (i + 2 >= count) return
                    val note = msg[offset + i + 1].toInt() and 0x7F
                    synth.noteOff(note)
                    i += 3
                }
                0xB0 -> { // Control Change — CC123 = All Notes Off
                    if (i + 2 >= count) return
                    val cc = msg[offset + i + 1].toInt() and 0x7F
                    if (cc == 123) synth.allNotesOff()
                    i += 3
                }
                else -> {
                    // Unhandled: skip a single byte and resync.
                    i += 1
                }
            }
        }
    }
}
