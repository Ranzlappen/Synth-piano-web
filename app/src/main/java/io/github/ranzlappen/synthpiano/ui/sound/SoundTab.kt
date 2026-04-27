package io.github.ranzlappen.synthpiano.ui.sound

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.R
import io.github.ranzlappen.synthpiano.audio.SynthController
import io.github.ranzlappen.synthpiano.audio.Waveform
import io.github.ranzlappen.synthpiano.data.PreferencesRepository
import io.github.ranzlappen.synthpiano.ui.components.AdsrPreview
import io.github.ranzlappen.synthpiano.ui.components.GlassCard
import io.github.ranzlappen.synthpiano.ui.components.Oscilloscope

/**
 * The SOUND tab: full-page sound design surface. Three glass cards laid
 * out horizontally on landscape (wide screens) and stacked on narrow:
 *   - Oscillator: 4 large waveform tiles.
 *   - Envelope: ADSR sliders + live shape preview.
 *   - Oscilloscope: live mono master-mix waveform.
 *
 * Persists waveform + ADSR through PreferencesRepository so the Perform
 * tab inherits whatever the player dialed in here.
 */
@Composable
fun SoundTab(
    synth: SynthController,
    prefs: PreferencesRepository,
    modifier: Modifier = Modifier,
) {
    val waveform by synth.waveform.collectAsState()
    val adsr by synth.adsr.collectAsState()

    LaunchedEffect(waveform) { prefs.setWaveform(waveform) }
    LaunchedEffect(adsr) { prefs.setAdsr(adsr) }

    val widthDp = LocalConfiguration.current.screenWidthDp
    // 700dp was too narrow — 800-900dp landscape phones were squeezing the
    // three cards to the point where Triangle/Sustain clipped. Push the
    // threshold up so phones in landscape stack vertically with outer scroll.
    val wide = widthDp >= 900

    if (wide) {
        Row(
            modifier = modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OscillatorCard(
                waveform = waveform,
                onSelect = synth::setWaveform,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            EnvelopeCard(
                adsr = adsr,
                onAdsr = { synth.setAdsr(it.attackSec, it.decaySec, it.sustain, it.releaseSec) },
                modifier = Modifier.weight(1.5f).fillMaxHeight(),
            )
            OscilloscopeCard(
                synth = synth,
                modifier = Modifier.weight(1.2f).fillMaxHeight(),
            )
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OscillatorCard(
                waveform = waveform,
                onSelect = synth::setWaveform,
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            )
            EnvelopeCard(
                adsr = adsr,
                onAdsr = { synth.setAdsr(it.attackSec, it.decaySec, it.sustain, it.releaseSec) },
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            )
            OscilloscopeCard(
                synth = synth,
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )
        }
    }
}

@Composable
private fun OscillatorCard(
    waveform: Waveform,
    onSelect: (Waveform) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(R.string.sound_oscillator),
                style = MaterialTheme.typography.titleMedium,
            )
            Waveform.values().forEach { w ->
                WaveformTile(
                    waveform = w,
                    selected = w == waveform,
                    onClick = { onSelect(w) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun WaveformTile(
    waveform: Waveform,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    val onContainer = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                      else MaterialTheme.colorScheme.onSurface
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        WaveformIcon(
            waveform = waveform,
            color = if (selected) accent else onContainer.copy(alpha = 0.6f),
            modifier = Modifier.height(20.dp).width(40.dp),
        )
        Text(
            waveform.displayName(),
            style = MaterialTheme.typography.titleMedium,
            color = onContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun WaveformIcon(
    waveform: Waveform,
    color: Color,
    modifier: Modifier,
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val mid = h / 2f
        when (waveform) {
            Waveform.SINE -> {
                val path = androidx.compose.ui.graphics.Path()
                path.moveTo(0f, mid)
                val steps = 32
                for (i in 1..steps) {
                    val t = i.toFloat() / steps
                    val x = t * w
                    val y = mid - kotlin.math.sin(t * 2 * Math.PI).toFloat() * (h * 0.4f)
                    path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = color,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f),
                )
            }
            Waveform.SQUARE -> {
                val path = androidx.compose.ui.graphics.Path()
                path.moveTo(0f, h * 0.1f)
                path.lineTo(w * 0.5f, h * 0.1f)
                path.lineTo(w * 0.5f, h * 0.9f)
                path.lineTo(w, h * 0.9f)
                drawPath(
                    path = path,
                    color = color,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f),
                )
            }
            Waveform.SAW -> {
                val path = androidx.compose.ui.graphics.Path()
                path.moveTo(0f, h * 0.9f)
                path.lineTo(w * 0.95f, h * 0.1f)
                path.lineTo(w * 0.95f, h * 0.9f)
                drawPath(
                    path = path,
                    color = color,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f),
                )
            }
            Waveform.TRIANGLE -> {
                val path = androidx.compose.ui.graphics.Path()
                path.moveTo(0f, h * 0.9f)
                path.lineTo(w * 0.5f, h * 0.1f)
                path.lineTo(w, h * 0.9f)
                drawPath(
                    path = path,
                    color = color,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f),
                )
            }
            Waveform.PIANO -> {
                // A decaying-cosine glyph evokes a plucked string envelope.
                val path = androidx.compose.ui.graphics.Path()
                path.moveTo(0f, mid)
                val steps = 48
                for (i in 1..steps) {
                    val t = i.toFloat() / steps
                    val x = t * w
                    val decay = kotlin.math.exp(-2.5 * t).toFloat()
                    val y = mid - kotlin.math.sin(t * 4 * Math.PI).toFloat() * (h * 0.4f) * decay
                    path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = color,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f),
                )
            }
        }
    }
}

@Composable
private fun EnvelopeCard(
    adsr: io.github.ranzlappen.synthpiano.audio.Adsr,
    onAdsr: (io.github.ranzlappen.synthpiano.audio.Adsr) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.sound_envelope), style = MaterialTheme.typography.titleMedium)
            AdsrPreview(
                adsr = adsr,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            )
            EnvelopeSlider(
                label = stringResource(R.string.adsr_attack),
                value = adsr.attackSec,
                range = 0.001f..2.0f,
                isSeconds = true,
                onChange = { onAdsr(adsr.copy(attackSec = it)) },
            )
            EnvelopeSlider(
                label = stringResource(R.string.adsr_decay),
                value = adsr.decaySec,
                range = 0.001f..2.0f,
                isSeconds = true,
                onChange = { onAdsr(adsr.copy(decaySec = it)) },
            )
            EnvelopeSlider(
                label = stringResource(R.string.adsr_sustain),
                value = adsr.sustain,
                range = 0f..1f,
                isSeconds = false,
                onChange = { onAdsr(adsr.copy(sustain = it)) },
            )
            EnvelopeSlider(
                label = stringResource(R.string.adsr_release),
                value = adsr.releaseSec,
                range = 0.001f..3.0f,
                isSeconds = true,
                onChange = { onAdsr(adsr.copy(releaseSec = it)) },
            )
        }
    }
}

@Composable
private fun EnvelopeSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    isSeconds: Boolean,
    onChange: (Float) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (isSeconds) "%d ms".format((value * 1000f).toInt())
                   else "%.2f".format(value),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(56.dp),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun OscilloscopeCard(
    synth: SynthController,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.sound_oscilloscope), style = MaterialTheme.typography.titleMedium)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.55f)),
            ) {
                Oscilloscope(
                    synth = synth,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}
