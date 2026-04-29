package io.github.ranzlappen.synthpiano

import android.app.Application
import io.github.ranzlappen.synthpiano.audio.NativeSynth
import io.github.ranzlappen.synthpiano.audio.SynthController
import io.github.ranzlappen.synthpiano.data.LayoutRepository
import io.github.ranzlappen.synthpiano.data.PreferencesRepository
import io.github.ranzlappen.synthpiano.data.PresetRepository

class SynthApp : Application() {

    lateinit var synth: SynthController
        private set

    lateinit var prefs: PreferencesRepository
        private set

    lateinit var presets: PresetRepository
        private set

    lateinit var layouts: LayoutRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        synth = SynthController(NativeSynth())
        prefs = PreferencesRepository(applicationContext)
        presets = PresetRepository(prefs)
        layouts = LayoutRepository(prefs)
    }

    companion object {
        @Volatile
        private var instance: SynthApp? = null

        fun get(): SynthApp =
            instance ?: error("SynthApp not initialized")
    }
}
