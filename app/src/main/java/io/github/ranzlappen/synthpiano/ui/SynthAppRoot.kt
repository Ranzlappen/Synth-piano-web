package io.github.ranzlappen.synthpiano.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.ranzlappen.synthpiano.audio.SynthController
import io.github.ranzlappen.synthpiano.data.PreferencesRepository
import io.github.ranzlappen.synthpiano.midi.MidiManager
import io.github.ranzlappen.synthpiano.ui.play.PlayScreen
import io.github.ranzlappen.synthpiano.ui.score.ScoreScreen
import io.github.ranzlappen.synthpiano.ui.settings.SettingsScreen

enum class Tab { Play, Score, Settings }

@Composable
fun SynthAppRoot(
    synth: SynthController,
    prefs: PreferencesRepository,
    midi: MidiManager,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Play) }
    val onNavigate: (Tab) -> Unit = { tab = it }
    val navigateBack: () -> Unit = { tab = Tab.Play }

    Box(modifier = Modifier.fillMaxSize()) {
        when (tab) {
            Tab.Play -> PlayScreen(
                synth = synth,
                prefs = prefs,
                onNavigate = onNavigate,
            )
            Tab.Score -> ScoreScreen(
                synth = synth,
                prefs = prefs,
                onBack = navigateBack,
            )
            Tab.Settings -> SettingsScreen(
                synth = synth,
                prefs = prefs,
                midi = midi,
                onBack = navigateBack,
            )
        }
    }
}
