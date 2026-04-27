package io.github.ranzlappen.synthpiano.crash

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * On-device crash logger. Installed once from [SynthApp.onCreate].
 *
 * When an uncaught exception happens on any thread, writes a stack trace
 * to [REPORT_FILENAME] in the app's filesDir, then chains to the platform
 * default handler (which usually shows the "App keeps stopping" dialog).
 *
 * Used to bootstrap remote debugging when the user has no PC for ADB and
 * no rooted/Android-11 device for Shizuku.
 */
object CrashReporter {

    const val REPORT_FILENAME = "crash-report.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeReport(appContext, thread, throwable)
            } catch (_: Throwable) {
                // Don't let the logger crash the crash handler.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun getReport(context: Context): String? {
        val file = reportFile(context)
        return if (file.exists()) file.readText() else null
    }

    fun hasReport(context: Context): Boolean = reportFile(context).exists()

    fun clear(context: Context) {
        reportFile(context).delete()
    }

    /**
     * Builds an Intent that shares the report via the system share sheet.
     * Returns null when there is no report yet.
     */
    fun shareIntent(context: Context): Intent? {
        val file = reportFile(context)
        if (!file.exists()) return null
        val authority = "${context.packageName}.fileprovider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Synth Piano crash report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun reportFile(context: Context) = File(context.filesDir, REPORT_FILENAME)

    private fun writeReport(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        pw.println("Synth Piano crash report")
        pw.println("---")
        pw.println("When:   ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())}")
        pw.println("Thread: ${thread.name}")
        pw.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        pw.println("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        pw.println("Brand:  ${Build.BRAND} / ${Build.PRODUCT}")
        pw.println("---")
        throwable.printStackTrace(pw)
        pw.flush()
        reportFile(context).writeText(sw.toString())
    }
}
