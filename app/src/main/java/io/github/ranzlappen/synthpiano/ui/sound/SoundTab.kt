package io.github.ranzlappen.synthpiano.ui.sound

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ranzlappen.synthpiano.R
import io.github.ranzlappen.synthpiano.audio.Adsr
import io.github.ranzlappen.synthpiano.audio.FilterSettings
import io.github.ranzlappen.synthpiano.audio.SynthController
import io.github.ranzlappen.synthpiano.audio.VoiceShaping
import io.github.ranzlappen.synthpiano.audio.Waveform
import io.github.ranzlappen.synthpiano.data.BuiltInPresets
import io.github.ranzlappen.synthpiano.data.PreferencesRepository
import io.github.ranzlappen.synthpiano.data.PresetRepository
import io.github.ranzlappen.synthpiano.data.SoundPreset
import io.github.ranzlappen.synthpiano.ui.components.AdsrPreview
import io.github.ranzlappen.synthpiano.ui.components.ExpandableSection
import io.github.ranzlappen.synthpiano.ui.components.GlassCard
import io.github.ranzlappen.synthpiano.ui.components.InfoCopy
import io.github.ranzlappen.synthpiano.ui.components.InfoIconButton
import io.github.ranzlappen.synthpiano.ui.components.InfoSlider
import io.github.ranzlappen.synthpiano.ui.components.Oscilloscope
import kotlinx.coroutines.launch
import kotlin.math.log10
import kotlin.math.pow

/** MIDI note for the C audition button (middle C). */
private const val AUDITION_MIDI = 60

/**
 * The SOUND tab: full-page sound design surface. Top row is the preset chip
 * strip; below it the existing oscillator / envelope / oscilloscope cards
 * (now with a one-shot "C" audition button beside the scope and an ADSR
 * curve slider). A second row exposes the filter and voice-shaping (velocity
 * sensitivity + glide) controls.
 */
@Composable
fun SoundTab(
    synth: SynthController,
    prefs: PreferencesRepository,
    presets: PresetRepository,
    modifier: Modifier = Modifier,
) {
    val waveform by synth.waveform.collectAsState()
    val adsr by synth.adsr.collectAsState()
    val filter by synth.filter.collectAsState()
    val voice by synth.voiceShaping.collectAsState()
    val polyComp by synth.polyComp.collectAsState()
    val headroom by synth.headroom.collectAsState()
    val maxPolyphony by synth.maxPolyphony.collectAsState()
    val drive by synth.drive.collectAsState()
    val userPresets by presets.userPresets.collectAsState(initial = emptyList())
    val selectedPresetName by prefs.lastPresetName.collectAsState(initial = null)
    val advExpanded by prefs.advancedExpanded.collectAsState(initial = emptySet())

    LaunchedEffect(waveform) { prefs.setWaveform(waveform) }
    LaunchedEffect(adsr) { prefs.setAdsr(adsr) }
    LaunchedEffect(filter) { prefs.setFilter(filter) }
    LaunchedEffect(voice) { prefs.setVoiceShaping(voice) }

    val scope = rememberCoroutineScope()
    val onToggleAdvanced: (String) -> Unit = { id ->
        scope.launch { prefs.setAdvancedExpanded(id, id !in advExpanded) }
    }

    // Three responsive layout tiers compact horizontally; vertical scroll
    // is always enabled so every slider stays reachable even when the cards
    // overflow on shorter screens.
    //   tablet (≥ 900 dp): 3-column grid, two rows; usually no scroll.
    //   mid    (600..899): 2-column grid + full-width scope underneath.
    //   narrow (< 600):    single column.
    val widthDp = LocalConfiguration.current.screenWidthDp
    val tier = when {
        widthDp >= 900 -> SoundLayoutTier.Tablet
        widthDp >= 600 -> SoundLayoutTier.Mid
        else -> SoundLayoutTier.Narrow
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PresetCard(
            synth = synth,
            presets = presets,
            userPresets = userPresets,
            selectedName = selectedPresetName,
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        )

        // Card rendering helpers — captured here so each tier just composes
        // them into the right grid without repeating the call sites.
        val osc: @Composable (Modifier) -> Unit = { m ->
            OscillatorCard(
                waveform = waveform,
                onSelect = synth::setWaveform,
                modifier = m,
            )
        }
        val env: @Composable (Modifier) -> Unit = { m ->
            EnvelopeCard(
                adsr = adsr,
                onAdsr = { synth.setAdsr(it.attackSec, it.decaySec, it.sustain, it.releaseSec, it.curve) },
                advancedExpanded = "envelope" in advExpanded,
                onAdvancedToggle = { onToggleAdvanced("envelope") },
                modifier = m,
            )
        }
        val flt: @Composable (Modifier) -> Unit = { m ->
            FilterCard(
                filter = filter,
                onFilter = { synth.setFilter(it.cutoffHz, it.resonance) },
                advancedExpanded = "filter" in advExpanded,
                onAdvancedToggle = { onToggleAdvanced("filter") },
                modifier = m,
            )
        }
        val voc: @Composable (Modifier) -> Unit = { m ->
            VoiceShapingCard(
                voice = voice,
                polyComp = polyComp,
                headroom = headroom,
                maxPolyphony = maxPolyphony,
                drive = drive,
                onVoice = { v ->
                    synth.setVelocitySensitivity(v.velocitySensitivity)
                    synth.setGlideSec(v.glideSec)
                },
                onPolyComp = synth::setPolyCompensation,
                onHeadroom = synth::setHeadroom,
                onMaxPolyphony = synth::setMaxPolyphony,
                onDrive = synth::setDrive,
                advancedExpanded = "voice" in advExpanded,
                onAdvancedToggle = { onToggleAdvanced("voice") },
                modifier = m,
            )
        }
        val scopeCard: @Composable (Modifier) -> Unit = { m ->
            OscilloscopeCard(synth = synth, modifier = m)
        }

        when (tier) {
            SoundLayoutTier.Tablet -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    osc(Modifier.weight(1f))
                    env(Modifier.weight(1.5f))
                    scopeCard(Modifier.weight(1.2f).height(280.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    flt(Modifier.weight(1f))
                    voc(Modifier.weight(1f))
                }
            }
            SoundLayoutTier.Mid -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    osc(Modifier.weight(1f))
                    env(Modifier.weight(1.5f))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    flt(Modifier.weight(1f))
                    voc(Modifier.weight(1.5f))
                }
                scopeCard(Modifier.fillMaxWidth().height(140.dp))
            }
            SoundLayoutTier.Narrow -> {
                osc(Modifier.fillMaxWidth().wrapContentHeight())
                env(Modifier.fillMaxWidth().wrapContentHeight())
                scopeCard(Modifier.fillMaxWidth().height(220.dp))
                flt(Modifier.fillMaxWidth().wrapContentHeight())
                voc(Modifier.fillMaxWidth().wrapContentHeight())
            }
        }
    }
}

private enum class SoundLayoutTier { Tablet, Mid, Narrow }

@Composable
private fun PresetCard(
    synth: SynthController,
    presets: PresetRepository,
    userPresets: List<SoundPreset>,
    selectedName: String?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var saveDialogOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<SoundPreset?>(null) }
    var menuFor by remember { mutableStateOf<SoundPreset?>(null) }

    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Presets",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { saveDialogOpen = true }) {
                    Text(stringResource(R.string.sound_save_current_as))
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BuiltInPresets.all.forEach { p ->
                    PresetChip(
                        preset = p,
                        selected = p.name == selectedName,
                        onClick = { scope.launch { presets.apply(synth, p) } },
                    )
                }
                userPresets.forEach { p ->
                    Box {
                        PresetChip(
                            preset = p,
                            selected = p.name == selectedName,
                            onClick = { scope.launch { presets.apply(synth, p) } },
                            onLongClick = { menuFor = p },
                        )
                        DropdownMenu(
                            expanded = menuFor?.name == p.name,
                            onDismissRequest = { menuFor = null },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_rename)) },
                                onClick = {
                                    renameTarget = p
                                    menuFor = null
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
                                onClick = {
                                    scope.launch { presets.deleteUser(p.name) }
                                    menuFor = null
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (saveDialogOpen) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saveDialogOpen = false },
            title = { Text(stringResource(R.string.sound_save_preset_title)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.sound_preset_name_label)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = name.trim()
                        if (trimmed.isNotEmpty()) {
                            scope.launch {
                                presets.saveUser(presets.snapshot(synth, trimmed))
                            }
                            saveDialogOpen = false
                        }
                    },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { saveDialogOpen = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    renameTarget?.let { target ->
        var name by remember(target.name) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.sound_rename_preset_title)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.sound_preset_name_label)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { presets.renameUser(target.name, name) }
                        renameTarget = null
                    },
                ) { Text(stringResource(R.string.action_rename)) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetChip(
    preset: SoundPreset,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    val onContainer = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                      else MaterialTheme.colorScheme.onSurface
    val clickMod = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .then(clickMod)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            preset.name,
            style = MaterialTheme.typography.labelLarge,
            color = onContainer,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (preset.builtin) {
            Spacer(Modifier.width(6.dp))
            Text(
                "★",
                style = MaterialTheme.typography.labelMedium,
                color = onContainer.copy(alpha = 0.6f),
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
    adsr: Adsr,
    onAdsr: (Adsr) -> Unit,
    advancedExpanded: Boolean,
    onAdvancedToggle: () -> Unit,
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
                info = InfoCopy(R.string.info_attack_title, R.string.info_attack_body),
            )
            EnvelopeSlider(
                label = stringResource(R.string.adsr_decay),
                value = adsr.decaySec,
                range = 0.001f..2.0f,
                isSeconds = true,
                onChange = { onAdsr(adsr.copy(decaySec = it)) },
                info = InfoCopy(R.string.info_decay_title, R.string.info_decay_body),
            )
            EnvelopeSlider(
                label = stringResource(R.string.adsr_sustain),
                value = adsr.sustain,
                range = 0f..1f,
                isSeconds = false,
                onChange = { onAdsr(adsr.copy(sustain = it)) },
                info = InfoCopy(R.string.info_sustain_title, R.string.info_sustain_body),
            )
            EnvelopeSlider(
                label = stringResource(R.string.adsr_release),
                value = adsr.releaseSec,
                range = 0.001f..3.0f,
                isSeconds = true,
                onChange = { onAdsr(adsr.copy(releaseSec = it)) },
                info = InfoCopy(R.string.info_release_title, R.string.info_release_body),
            )
            ExpandableSection(
                expanded = advancedExpanded,
                onToggle = onAdvancedToggle,
                label = stringResource(R.string.sound_advanced),
            ) {
                EnvelopeSlider(
                    label = stringResource(R.string.sound_curve),
                    value = adsr.curve,
                    range = -1f..1f,
                    isSeconds = false,
                    onChange = { onAdsr(adsr.copy(curve = it)) },
                    info = InfoCopy(R.string.info_curve_title, R.string.info_curve_body),
                )
            }
        }
    }
}

/**
 * Thin SOUND-tab adapter over [InfoSlider]. Keeps the existing call sites
 * tidy (sliders that don't supply [info] just don't show the icon) while
 * letting every slider gain a localized info popup by passing an
 * [InfoCopy] holder.
 */
@Composable
private fun EnvelopeSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    isSeconds: Boolean,
    onChange: (Float) -> Unit,
    info: InfoCopy? = null,
    valueFormatter: (Float) -> String = { v ->
        if (isSeconds) "%d ms".format((v * 1000f).toInt())
        else "%.2f".format(v)
    },
) {
    InfoSlider(
        label = label,
        value = value,
        range = range,
        onChange = onChange,
        valueFormatter = valueFormatter,
        info = info,
    )
}

@Composable
private fun OscilloscopeCard(
    synth: SynthController,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.sound_oscilloscope),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                AuditionKeyButton(synth = synth)
            }
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

@Composable
private fun AuditionKeyButton(synth: SynthController) {
    var pressed by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            if (pressed) {
                synth.noteOff(AUDITION_MIDI)
                pressed = false
            }
        }
    }
    val container = if (pressed) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    val onContainer = if (pressed) MaterialTheme.colorScheme.onPrimary
                      else MaterialTheme.colorScheme.onSurface
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    synth.noteOn(AUDITION_MIDI)
                    waitForUpOrCancellation()
                    synth.noteOff(AUDITION_MIDI)
                    pressed = false
                }
            },
    ) {
        Text(
            "C",
            style = MaterialTheme.typography.titleLarge,
            color = onContainer,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun FilterCard(
    filter: FilterSettings,
    onFilter: (FilterSettings) -> Unit,
    advancedExpanded: Boolean,
    onAdvancedToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.sound_filter_title), style = MaterialTheme.typography.titleMedium)
            // Cutoff is mapped to a logarithmic scale so the slider feels even
            // across the audible range (50 Hz to 18 kHz).
            val logMin = log10(50f)
            val logMax = log10(18000f)
            val logVal = log10(filter.cutoffHz.coerceIn(50f, 18000f))
            EnvelopeSlider(
                label = stringResource(R.string.sound_cutoff),
                value = logVal,
                range = logMin..logMax,
                isSeconds = false,
                onChange = { l -> onFilter(filter.copy(cutoffHz = 10f.pow(l))) },
                info = InfoCopy(R.string.info_cutoff_title, R.string.info_cutoff_body),
                valueFormatter = { l ->
                    val hz = 10f.pow(l)
                    if (hz >= 1000f) "%.1f kHz".format(hz / 1000f) else "%d Hz".format(hz.toInt())
                },
            )
            ExpandableSection(
                expanded = advancedExpanded,
                onToggle = onAdvancedToggle,
                label = stringResource(R.string.sound_advanced),
            ) {
                EnvelopeSlider(
                    label = stringResource(R.string.sound_resonance),
                    value = filter.resonance,
                    range = 0f..1f,
                    isSeconds = false,
                    onChange = { onFilter(filter.copy(resonance = it)) },
                    info = InfoCopy(R.string.info_resonance_title, R.string.info_resonance_body),
                )
            }
        }
    }
}

@Composable
private fun VoiceShapingCard(
    voice: VoiceShaping,
    polyComp: Float,
    headroom: Float,
    maxPolyphony: Int,
    drive: Float,
    onVoice: (VoiceShaping) -> Unit,
    onPolyComp: (Float) -> Unit,
    onHeadroom: (Float) -> Unit,
    onMaxPolyphony: (Int) -> Unit,
    onDrive: (Float) -> Unit,
    advancedExpanded: Boolean,
    onAdvancedToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.sound_voice_title), style = MaterialTheme.typography.titleMedium)
            // Basic: high-impact voice modifiers a casual user reaches for first.
            EnvelopeSlider(
                label = stringResource(R.string.sound_glide),
                value = voice.glideSec,
                range = 0f..0.5f,
                isSeconds = true,
                onChange = { onVoice(voice.copy(glideSec = it)) },
                info = InfoCopy(R.string.info_glide_title, R.string.info_glide_body),
            )
            EnvelopeSlider(
                label = stringResource(R.string.sound_drive),
                value = drive,
                range = 0f..1f,
                isSeconds = false,
                onChange = onDrive,
                info = InfoCopy(R.string.info_drive_title, R.string.info_drive_body),
                valueFormatter = { v -> "%d%%".format((v * 100f).toInt()) },
            )
            ExpandableSection(
                expanded = advancedExpanded,
                onToggle = onAdvancedToggle,
                label = stringResource(R.string.sound_advanced),
            ) {
                EnvelopeSlider(
                    label = stringResource(R.string.sound_velocity),
                    value = voice.velocitySensitivity,
                    range = 0f..1f,
                    isSeconds = false,
                    onChange = { onVoice(voice.copy(velocitySensitivity = it)) },
                    info = InfoCopy(R.string.info_velocity_title, R.string.info_velocity_body),
                )
                EnvelopeSlider(
                    label = stringResource(R.string.sound_poly_comp),
                    value = polyComp,
                    range = 0f..1f,
                    isSeconds = false,
                    onChange = onPolyComp,
                    info = InfoCopy(R.string.info_poly_comp_title, R.string.info_poly_comp_body),
                    valueFormatter = { v -> "%d%%".format((v * 100f).toInt()) },
                )
                EnvelopeSlider(
                    label = stringResource(R.string.sound_headroom),
                    value = headroom,
                    range = 0.5f..1.5f,
                    isSeconds = false,
                    onChange = onHeadroom,
                    info = InfoCopy(R.string.info_headroom_title, R.string.info_headroom_body),
                    valueFormatter = { v -> "%.2f×".format(v) },
                )
                PolyphonyStepper(
                    value = maxPolyphony,
                    onChange = onMaxPolyphony,
                )
            }
        }
    }
}

/**
 * Stepper for the active-voice cap. Discrete options keep the user from
 * landing on awkward values; the cap maps directly to CPU headroom and
 * the maximum number of voices that can stack into the limiter.
 */
@Composable
private fun PolyphonyStepper(
    value: Int,
    onChange: (Int) -> Unit,
) {
    val options = listOf(4, 8, 12, 16)
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.sound_max_polyphony),
            modifier = Modifier.width(72.dp),
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { n ->
                val selected = value == n
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onChange(n) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = n.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
        InfoIconButton(
            info = InfoCopy(R.string.info_max_polyphony_title, R.string.info_max_polyphony_body),
        )
    }
}
