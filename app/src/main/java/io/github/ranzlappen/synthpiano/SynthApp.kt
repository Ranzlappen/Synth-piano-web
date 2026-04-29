package io.github.ranzlappen.synthpiano

import android.app.Application
import android.util.Log
import io.github.ranzlappen.synthpiano.audio.NativeSynth
import io.github.ranzlappen.synthpiano.audio.SynthController
import io.github.ranzlappen.synthpiano.audio.WavRecorder
import io.github.ranzlappen.synthpiano.data.LayoutRepository
import io.github.ranzlappen.synthpiano.data.PreferencesRepository
import io.github.ranzlappen.synthpiano.data.PresetRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class SynthApp : Application() {

    lateinit var synth: SynthController
        private set

    lateinit var prefs: PreferencesRepository
        private set

    lateinit var presets: PresetRepository
        private set

    lateinit var layouts: LayoutRepository
        private set

    private val migrationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this
        synth = SynthController(NativeSynth())
        prefs = PreferencesRepository(applicationContext)
        presets = PresetRepository(prefs)
        layouts = LayoutRepository(prefs)
        migrationScope.launch { migrateLegacyRecordings() }
    }

    /**
     * Move recordings written by older builds (`<filesDir>/recordings/`, an
     * app-private path the user can't browse) to the new app-specific
     * external storage location ([WavRecorder.recordingsDir]) so they show
     * up in the system Files app and over USB. Idempotent: when the source
     * dir is empty or absent, this is a no-op.
     */
    private fun migrateLegacyRecordings() {
        val legacy = File(filesDir, "recordings")
        if (!legacy.isDirectory) return
        val target = WavRecorder.recordingsDir(applicationContext)
        if (target.absolutePath == legacy.absolutePath) return  // external unmounted: nothing to do
        val files = legacy.listFiles() ?: return
        if (files.isEmpty()) {
            legacy.delete()
            return
        }
        target.mkdirs()
        var moved = 0
        for (src in files) {
            if (!src.isFile) continue
            val dst = File(target, src.name)
            if (dst.exists()) {
                // Don't clobber a same-named file in the new location.
                continue
            }
            val ok = runCatching { src.renameTo(dst) }.getOrDefault(false) ||
                runCatching {
                    src.copyTo(dst, overwrite = false)
                    src.delete()
                }.isSuccess
            if (ok) moved++ else Log.w(TAG, "Failed to migrate ${src.name}")
        }
        if (legacy.listFiles()?.isEmpty() == true) legacy.delete()
        if (moved > 0) Log.i(TAG, "Migrated $moved recording(s) to ${target.absolutePath}")
    }

    companion object {
        private const val TAG = "SynthApp"

        @Volatile
        private var instance: SynthApp? = null

        fun get(): SynthApp =
            instance ?: error("SynthApp not initialized")
    }
}
