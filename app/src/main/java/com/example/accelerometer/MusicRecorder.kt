package com.example.accelerometer

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.round

private fun round2(x: Double): Double =
    round(x * 100.0) / 100.0

class MusicRecorder(
    private val context: Context,
    private val scope: CoroutineScope,
    private val gson: Gson = Gson()
) {

    private val enabled = AtomicBoolean(false)

    private var sessionFolderName: String? = null
    private var writer: BufferedWriter? = null

    private var writeCh: Channel<MusicRecord>? = null
    private var writerJob: Job? = null

    fun isEnabled(): Boolean = enabled.get()
    fun sessionId(): String? = sessionFolderName

    data class MusicRecord(
        val win: Int,
        val cadence_hz_mean: Double,
        val tempo_bpm: Double,
        val macro_db_target: Double,
        val macro_amp_target: Double
    )

    /**
     * Start music recorder for an EXISTING session folder.
     * It writes to: sessions/<sessionId>/music.jsonl
     */
    fun start(sessionId: String) {
        stop()

        val dir = File(context.getExternalFilesDir("sessions"), sessionId)
        if (!dir.exists()) dir.mkdirs()

        sessionFolderName = sessionId

        val w = BufferedWriter(FileWriter(File(dir, "music.jsonl"), true))
        writer = w

        val ch = Channel<MusicRecord>(capacity = Channel.BUFFERED)
        writeCh = ch

        writerJob = scope.launch(Dispatchers.IO) {
            try {
                for (rec in ch) {
                    w.write(gson.toJson(rec))
                    w.newLine()
                    w.flush()
                }
            } catch (t: Throwable) {
                Log.e("MUSIC-REC", "writer loop failed", t)
            } finally {
                try { w.flush() } catch (_: Throwable) {}
                try { w.close() } catch (_: Throwable) {}
            }
        }

        enabled.set(true)
        Log.i("MUSIC-REC", "MusicRecorder START sessionId=$sessionId path=${dir.absolutePath}")
    }

    fun stop() {
        val wasEnabled = enabled.getAndSet(false)

        sessionFolderName = null

        val ch = writeCh
        writeCh = null
        try { ch?.close() } catch (_: Throwable) {}

        val job = writerJob
        writerJob = null

        scope.launch(Dispatchers.IO) {
            try { job?.join() } catch (_: Throwable) {}
            writer = null
            if (wasEnabled) Log.i("MUSIC-REC", "MusicRecorder STOP")
        }
    }

    /**
     * Record 1 music decision per window:
     * - win index (use RunningService.windows or recordSession winIndex if you prefer)
     * - cadence mean
     * - tempo in BPM
     * - macro target in dB and amp (target amp = 10^(db/20))
     */
    fun recordMusic(
        win: Int,
        cadenceMeanHz: Double,
        tempoBpm: Double,
        macroDbTarget: Double,
        macroAmpTarget: Double
    ) {
        if (!enabled.get()) return

        val ch = writeCh ?: return

        val rec = MusicRecord(
            win = win,
            cadence_hz_mean = round2(cadenceMeanHz),
            tempo_bpm = round2(tempoBpm),
            macro_db_target = round2(macroDbTarget),
            macro_amp_target = round2(macroAmpTarget)
        )

        val ok = ch.trySend(rec).isSuccess
        if (!ok) {
            Log.e("MUSIC-REC", "queue full -> stop music recorder")
            stop()
        }
    }
}
