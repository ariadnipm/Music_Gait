package com.example.accelerometer

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.round


private fun round2(x: Double): Double =
    kotlin.math.round(x * 100.0) / 100.0

class RecordSession(
    private val context: Context,
    private val scope: CoroutineScope,
    private val gson: Gson = Gson()
) {

    private val enabled = AtomicBoolean(false)

    private var sessionFolderName: String? = null
    private var writer: BufferedWriter? = null

    private var remainingWindows: Int = 0
    private var winIndex: Int = 0


    private var writeCh: Channel<WindowRecord>? = null
    private var writerJob: Job? = null

    fun isEnabled(): Boolean = enabled.get()
    fun sessionId(): String? = sessionFolderName

    @Suppress("PropertyName")
    data class WindowRecord(
        val win: Int,
        val t: DoubleArray,
        val x: DoubleArray,
        val y: DoubleArray,
        val z: DoubleArray,
        val cadence_hz_mean: Double,
        val cadence_hz_per_sec: DoubleArray
    )

    /** Start a new session. Creates sessions/<label__timestamp>/ and opens session.jsonl */
    fun start(label: String?, recordFirstNWindows: Int) {
        stop()

        val ts = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss_SSS", Locale.US).format(Date())

        val safeLabel = label
            ?.trim()
            ?.lowercase(Locale.US)
            ?.replace(Regex("\\s+"), "-")
            ?.replace(Regex("[^a-z0-9_-]"), "")
            ?.take(40)

        val folderName = if (!safeLabel.isNullOrBlank()) "${safeLabel}__${ts}" else ts

        val dir = File(context.getExternalFilesDir("sessions"), folderName)
        dir.mkdirs()

        sessionFolderName = folderName
        remainingWindows = recordFirstNWindows
        winIndex = 0

        val w = BufferedWriter(FileWriter(File(dir, "session.jsonl"), true))
        writer = w


        val ch = Channel<WindowRecord>(capacity = Channel.BUFFERED)
        writeCh = ch

        writerJob = scope.launch(Dispatchers.IO) {
            try {
                for (rec in ch) {
                    w.write(gson.toJson(rec))
                    w.newLine()
                    w.flush()
                }
            } catch (t: Throwable) {
                Log.e("RECORD", "writer loop failed", t)
            } finally {
                try { w.flush() } catch (_: Throwable) {}
                try { w.close() } catch (_: Throwable) {}
            }
        }

        enabled.set(true)
        Log.i("RECORD", "RecordSession START id=$folderName path=${dir.absolutePath} windows=$recordFirstNWindows")
    }

    /** Stop session and close file safely */
    fun stop() {

        val wasEnabled = enabled.getAndSet(false)

        remainingWindows = 0
        winIndex = 0
        sessionFolderName = null


        val ch = writeCh
        writeCh = null
        try { ch?.close() } catch (_: Throwable) {}


        val job = writerJob
        writerJob = null

        scope.launch(Dispatchers.IO) {
            try { job?.join() } catch (_: Throwable) {}


            writer = null

            if (wasEnabled) Log.i("RECORD", "RecordSession STOP")
        }
    }

    /** Record one completed window and cadence outputs */
    fun recordWindow(
        t: DoubleArray,
        x: DoubleArray,
        y: DoubleArray,
        z: DoubleArray,
        cadenceMeanHz: Double,
        cadencePerSecHz: DoubleArray
    ) {
        if (!enabled.get()) return
        if (remainingWindows <= 0) { stop(); return }

        val ch = writeCh ?: return


        val cadenceMean2 = round2(cadenceMeanHz)
        val cadencePerSec2 = cadencePerSecHz.map { round2(it) }.toDoubleArray()

        val rec = WindowRecord(
            win = winIndex,
            t = t, x = x, y = y, z = z,
            cadence_hz_mean = cadenceMean2,
            cadence_hz_per_sec = cadencePerSec2
        )

        val ok = ch.trySend(rec).isSuccess
        if (!ok) {
            Log.e("RECORD", "queue full -> stop session")
            stop()
            return
        }

        winIndex++
        remainingWindows--
        if (remainingWindows <= 0) stop()
    }

}
