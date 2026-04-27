package io.github.ranzlappen.synthpiano.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.ranzlappen.synthpiano.R
import io.github.ranzlappen.synthpiano.audio.SynthController
import io.github.ranzlappen.synthpiano.data.PreferencesRepository
import io.github.ranzlappen.synthpiano.midi.MidiManager
import io.github.ranzlappen.synthpiano.ui.play.PlayScreen
import io.github.ranzlappen.synthpiano.ui.score.ScoreScreen
import io.github.ranzlappen.synthpiano.ui.settings.SettingsScreen

private enum class Tab { Play, Score, Settings }

@Composable
fun SynthAppRoot(
    synth: SynthController,
    prefs: PreferencesRepository,
    midi: MidiManager,
) {
    var tab by rememberSaveable { mutableStateOf(Tab.Play) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == Tab.Play,
                    onClick = { tab = Tab.Play },
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_play)) },
                )
                NavigationBarItem(
                    selected = tab == Tab.Score,
                    onClick = { tab = Tab.Score },
                    icon = { Icon(Icons.Filled.MusicNote, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_score)) },
                )
                NavigationBarItem(
                    selected = tab == Tab.Settings,
                    onClick = { tab = Tab.Settings },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.nav_settings)) },
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                Tab.Play -> PlayScreen(synth = synth, prefs = prefs)
                Tab.Score -> ScoreScreen(synth = synth, prefs = prefs)
                Tab.Settings -> SettingsScreen(synth = synth, prefs = prefs, midi = midi)
            }
        }
    }
}
