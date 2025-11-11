package com.example.accelerometer

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Χρησιμοποιούμε global sessionId για κάθε εκτέλεση
private val sessionId: String =
    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

fun saveWindowToCsv(
    context: Context,
    windowIndex: Int,
    n: Int,
    tUnix: DoubleArray,
    x: DoubleArray,
    y: DoubleArray,
    z: DoubleArray
) {
    try {
        // 1) Δημιουργία υποφακέλου για το τρέχον session
        val baseDir = File(context.getExternalFilesDir(null), "sw_dumps")
        val sessionDir = File(baseDir, sessionId)
        if (!sessionDir.exists()) sessionDir.mkdirs()

        // 2) Δημιουργία αρχείου μέσα στο session folder
        val file = File(sessionDir, "window_${windowIndex}.csv")

        // 3) Γράψιμο CSV
        file.bufferedWriter().use { w ->
            w.write("window,idx,t_unix_sec,ax,ay,az\n")
            val limit = n.coerceAtMost(
                minOf(tUnix.size, x.size, y.size, z.size)
            )
            for (i in 0 until limit) {
                w.write(
                    String.format(
                        Locale.US,
                        "%d,%d,%.6f,%.6f,%.6f,%.6f\n",
                        windowIndex, i, tUnix[i], x[i], y[i], z[i]
                    )
                )
            }
        }

        Log.i("CSV", "✅ Saved: ${file.absolutePath}")

    } catch (e: IOException) {
        Log.e("CSV", "❌ Error saving CSV: ${e.message}")
    }
}
