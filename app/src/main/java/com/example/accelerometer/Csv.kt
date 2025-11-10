package com.example.accelerometer

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        // 1) Φάκελος σε external app storage (ορατός με adb/File Manager)
        val dir = File(context.getExternalFilesDir(null), "windows")
        if (!dir.exists()) dir.mkdirs()

        // 2) Όνομα αρχείου με timestamp (ώστε να μη γίνει overwrite)
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "window_${windowIndex}_$ts.csv")

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
        Log.i("CSV", "Saved: ${file.absolutePath}")
    } catch (e: IOException) {
        Log.e("CSV", "Error saving CSV: ${e.message}")
    }
}
