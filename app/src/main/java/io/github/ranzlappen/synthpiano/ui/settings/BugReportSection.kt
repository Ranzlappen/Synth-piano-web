package io.github.ranzlappen.synthpiano.ui.settings

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.BuildConfig
import io.github.ranzlappen.synthpiano.R
import io.github.ranzlappen.synthpiano.audio.SynthController
import io.github.ranzlappen.synthpiano.midi.MidiManager

/**
 * Lets the user assemble a markdown bug report (synth status + system modes
 * + device info + their description) and email it to info@ranzlappen.com via
 * a mailto intent. Mirrors the HardwareDash bug report flow, minus the
 * permissions table (this app declares no runtime permissions).
 */
@Composable
fun BugReportSection(
    synth: SynthController,
    midi: MidiManager,
) {
    val context = LocalContext.current
    val started by synth.started.collectAsState()
    val devices by midi.connectedDeviceNames.collectAsState()
    val sampleRate = synth.engine().sampleRate()

    var description by remember { mutableStateOf("") }
    var showDialog by remember { mutableStateOf(false) }

    Text(
        stringResource(R.string.bug_report_disclaimer),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        placeholder = { Text(stringResource(R.string.bug_report_describe_hint)) },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
        maxLines = 8,
    )

    Button(
        onClick = { showDialog = true },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Default.BugReport, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.bug_report_create))
    }

    if (showDialog) {
        val synthStatus = remember(started, sampleRate, devices) {
            buildSynthStatus(started, sampleRate, POLYPHONY_VOICES, devices.size)
        }
        val systemModes = remember { buildSystemModes(context) }
        val deviceInfo = remember { buildDeviceInfo(context) }
        val report = remember(synthStatus, systemModes, deviceInfo, description) {
            buildMarkdownReport(synthStatus, systemModes, deviceInfo, description)
        }

        val subject = stringResource(R.string.bug_report_subject)
        val emailChooser = stringResource(R.string.bug_report_email)
        val copyLabel = stringResource(R.string.bug_report_copy)
        val copiedToast = stringResource(R.string.bug_report_copied)

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.bug_report_ready_title)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    Button(
                        onClick = {
                            val mailto = Uri.parse(
                                "mailto:" + RECIPIENT +
                                    "?subject=" + Uri.encode(subject) +
                                    "&body=" + Uri.encode(report),
                            )
                            val intent = Intent(Intent.ACTION_SENDTO, mailto)
                            context.startActivity(
                                Intent.createChooser(intent, emailChooser),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(emailChooser)
                    }

                    HorizontalDivider()

                    OutlinedTextField(
                        value = report,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp),
                        maxLines = 30,
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Bug report", report))
                        Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(copyLabel)
                    }
                    TextButton(onClick = { showDialog = false }) {
                        Text(stringResource(R.string.bug_report_close))
                    }
                }
            },
        )
    }
}

private const val RECIPIENT = "info@ranzlappen.com"
private const val POLYPHONY_VOICES = 16

private fun buildSynthStatus(
    engineStarted: Boolean,
    sampleRateHz: Int,
    polyphony: Int,
    midiDeviceCount: Int,
): List<Pair<String, String>> = listOf(
    "Engine started" to if (engineStarted) "Yes" else "No",
    "Sample rate" to if (sampleRateHz > 0) "$sampleRateHz Hz" else "n/a",
    "Polyphony" to "$polyphony voices",
    "MIDI devices connected" to midiDeviceCount.toString(),
    "Build flavor" to (BuildConfig.BUILD_TYPE),
)

private fun buildSystemModes(context: Context): List<Pair<String, String>> {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return listOf(
        "Ringer mode" to when (am.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> "Normal"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            AudioManager.RINGER_MODE_SILENT -> "Silent"
            else -> "Unknown"
        },
        "Do Not Disturb" to when (nm.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> "Off"
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "Priority Only"
            NotificationManager.INTERRUPTION_FILTER_NONE -> "Total Silence"
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "Alarms Only"
            else -> "Unknown"
        },
        "DND policy access" to if (nm.isNotificationPolicyAccessGranted) "Granted" else "Not Granted",
        "Battery saver" to if (pm.isPowerSaveMode) "Active" else "Off",
        "Music active" to if (am.isMusicActive) "Yes" else "No",
    )
}

private fun buildDeviceInfo(context: Context): List<Pair<String, String>> {
    val dm = context.resources.displayMetrics
    val appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    return listOf(
        "Device model" to Build.MODEL,
        "Manufacturer" to Build.MANUFACTURER,
        "Brand" to Build.BRAND,
        "Device" to Build.DEVICE,
        "Hardware" to Build.HARDWARE,
        "Android version" to Build.VERSION.RELEASE,
        "API level" to Build.VERSION.SDK_INT.toString(),
        "Build display" to Build.DISPLAY,
        "Screen resolution" to "${dm.widthPixels} x ${dm.heightPixels}",
        "Screen density" to "${dm.densityDpi} dpi (${dm.density}x)",
        "App version" to appVersion,
    )
}

private fun buildMarkdownReport(
    synthStatus: List<Pair<String, String>>,
    modes: List<Pair<String, String>>,
    deviceInfo: List<Pair<String, String>>,
    description: String,
): String = buildString {
    appendLine("## Synth status")
    appendLine("| Item | Value |")
    appendLine("|---|---|")
    synthStatus.forEach { (k, v) -> appendLine("| $k | $v |") }
    appendLine()
    appendLine("## System modes")
    appendLine("| Mode | Status |")
    appendLine("|---|---|")
    modes.forEach { (k, v) -> appendLine("| $k | $v |") }
    appendLine()
    appendLine("## Device info")
    appendLine("| Property | Value |")
    appendLine("|---|---|")
    deviceInfo.forEach { (k, v) -> appendLine("| $k | $v |") }
    appendLine()
    if (description.isNotBlank()) {
        appendLine("## Bug description")
        appendLine(description.trim())
    }
}
