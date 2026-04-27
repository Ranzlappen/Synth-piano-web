package io.github.ranzlappen.synthpiano

import android.app.Application
import io.github.ranzlappen.synthpiano.audio.NativeSynth
import io.github.ranzlappen.synthpiano.audio.SynthController
import io.github.ranzlappen.synthpiano.crash.CrashReporter
import io.github.ranzlappen.synthpiano.data.PreferencesRepository

class SynthApp : Application() {

    lateinit var synth: SynthController
        private set

    lateinit var prefs: PreferencesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // Install the crash logger BEFORE any other initialization so a
        // failure in synth init or DataStore creation still produces a
        // shareable report on the next launch.
        CrashReporter.install(this)
        instance = this
        synth = SynthController(NativeSynth())
        prefs = PreferencesRepository(applicationContext)
    }

    companion object {
        @Volatile
        private var instance: SynthApp? = null

        fun get(): SynthApp =
            instance ?: error("SynthApp not initialized")
    }
}
