package io.github.ranzlappen.synthpiano.ui.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.audio.Adsr
import io.github.ranzlappen.synthpiano.audio.Waveform

@Composable
fun SynthControlsBar(
    waveform: Waveform,
    onWaveform: (Waveform) -> Unit,
    adsr: Adsr,
    onAdsr: (Adsr) -> Unit,
    masterAmp: Float,
    onMasterAmp: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(horizontal = 12.dp, vertical = 2.dp)) {
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Wave:", style = MaterialTheme.typography.labelMedium)
            Waveform.values().forEach { w ->
                FilterChip(
                    selected = w == waveform,
                    onClick = { onWaveform(w) },
                    label = { Text(w.displayName(), style = MaterialTheme.typography.labelMedium) },
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AdsrSlider("A", adsr.attackSec, 0.001f..2.0f) { onAdsr(adsr.copy(attackSec = it)) }
            AdsrSlider("D", adsr.decaySec, 0.001f..2.0f) { onAdsr(adsr.copy(decaySec = it)) }
            AdsrSlider("S", adsr.sustain, 0f..1f, isLevel = true) { onAdsr(adsr.copy(sustain = it)) }
            AdsrSlider("R", adsr.releaseSec, 0.001f..3.0f) { onAdsr(adsr.copy(releaseSec = it)) }
            AdsrSlider("Vol", masterAmp, 0f..1f, isLevel = true, onMasterAmp)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.AdsrSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    isLevel: Boolean = false,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.weight(1f)) {
        val display = if (isLevel) "%.2f".format(value) else "%.0f ms".format(value * 1000f)
        Text("$label  $display", style = MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
