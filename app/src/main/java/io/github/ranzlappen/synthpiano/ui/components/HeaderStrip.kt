package io.github.ranzlappen.synthpiano.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.audio.SynthController

/**
 * Persistent global header for the workstation. Rendered above every tab
 * so the player can always see live waveform, level, MIDI status, and
 * recording chip and tweak master volume regardless of which surface is
 * foregrounded.
 *
 * The leftmost slot is a live oscilloscope (replacing the old "Synth
 * Piano" title) that gives players a constant readout of the master mix.
 */
@Composable
fun HeaderStrip(
    synth: SynthController,
    masterAmp: Float,
    onMasterAmp: (Float) -> Unit,
    midiDevices: List<String>,
    onMidiClick: () -> Unit,
    isRecording: Boolean,
    elapsedMs: Long,
    hasLastRecording: Boolean,
    onRecordToggle: () -> Unit,
    onShareLast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.4f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Oscilloscope(
            synth = synth,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .width(120.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.35f)),
            pixelColumns = 128,
        )

        MidiStatusChip(deviceNames = midiDevices, onClick = onMidiClick)

        Icon(
            imageVector = Icons.Filled.GraphicEq,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp),
        )
        LevelMeter(synth = synth)

        Spacer(Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.width(180.dp),
        ) {
            Text(
                "VOL",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            Slider(
                value = masterAmp,
                onValueChange = onMasterAmp,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                ),
                modifier = Modifier.weight(1f),
            )
        }

        RecordingChip(
            isRecording = isRecording,
            elapsedMs = elapsedMs,
            onToggle = onRecordToggle,
            onShareLast = if (hasLastRecording && !isRecording) onShareLast else null,
        )
    }
}
