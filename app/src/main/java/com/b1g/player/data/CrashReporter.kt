package com.b1g.player.data

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records uncaught exceptions to a file so the next launch can show them.
 *
 * A debug build installed by sideloading has no crash reporting and often no adb
 * attached, which leaves "the app closed instantly" as the only available symptom.
 * This turns that into a stack trace the user can read and send.
 */
class CrashReporter(private val context: Context) {

    private val file: File get() = File(context.filesDir, FILE_NAME)

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(thread, error) }
            // Still let the platform do its normal thing, so the process dies and
            // any real reporter downstream also sees the crash.
            previous?.uncaughtException(thread, error)
        }
    }

    fun lastCrash(): String? = runCatching {
        file.takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
    }.getOrNull()

    fun clear() {
        runCatching { file.delete() }
    }

    private fun write(thread: Thread, error: Throwable) {
        val stackTrace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        file.writeText(
            buildString {
                appendLine("B1G Player crash")
                appendLine(TIMESTAMP.format(Date()))
                appendLine("thread: ${thread.name}")
                appendLine("device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                appendLine("android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                appendLine()
                append(stackTrace)
            }
        )
    }

    private companion object {
        const val FILE_NAME = "last-crash.txt"
        val TIMESTAMP = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }
}
