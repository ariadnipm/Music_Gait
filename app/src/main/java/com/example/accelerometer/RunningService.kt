package com.example.accelerometer

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import java.util.concurrent.atomic.AtomicBoolean

@AndroidEntryPoint
class RunningService : Service() {

    enum class Actions { START, STOP }

    @Inject lateinit var accelerometer: MeasurableSensor
    @Inject lateinit var serviceScope: CoroutineScope
    @Inject lateinit var window: SlidingWindow

    data class Sample(val tMonoMs: Long, val x: Float, val y: Float, val z: Float)

    // ===== Channel pipeline =====
    private val sampleCh = Channel<Sample>(capacity = Channel.BUFFERED)
    private var consumerJob: Job? = null

    // Buffers
    private val treal = DoubleArray(8192)

    private val tunixA = DoubleArray(8192)
    private val XA     = DoubleArray(8192)
    private val YA     = DoubleArray(8192)
    private val ZA     = DoubleArray(8192)

    private val tunixB = DoubleArray(8192)
    private val XB     = DoubleArray(8192)
    private val YB     = DoubleArray(8192)
    private val ZB     = DoubleArray(8192)

    //State
    private val mutex = Mutex()
    private var useA = true
    private var bootToEpoch: Long = 0L
    private var running = false
    private val started = AtomicBoolean(false)

    // Debug/metrics
    private var windows = 0
    private var droppedSamples = 0
    private var lastWindowEpoch: Double? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serviceScope.launch {
            when (intent?.action) {
                Actions.START.toString() -> {

                    if (started.compareAndSet(false, true)) {
                        startInternal()
                    } else {
                        Log.w("RUN-SVC", "Duplicate START ignored")
                    }
                }

                Actions.STOP.toString() -> {

                    if (started.compareAndSet(true, false)) {
                        stopClean()
                    } else {
                        Log.w("RUN-SVC", "STOP ignored (not running)")
                        stopSelf() // optional
                    }
                }

                else -> {
                    // optional: treat as START
                    if (started.compareAndSet(false, true)) startInternal()
                }
            }
        }
        return START_STICKY
    }


    private fun startInternal() {


        val notif = NotificationCompat.Builder(this, "running_channel")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Run is active")
            .setContentText("Collecting data…")
            .setOngoing(true)
            .build()
        Log.e("Clock", "About to start service...")
        startForeground(1, notif)
        Log.e("Clock", "Started...")
        //Oboe implementaion
        val ar = Bridge.startAudio()
        Log.d("RUN-SVC", "startAudio() -> $ar")
        Bridge.setCadenceHz(0f)

        running = true
        // Reset state on each start
        useA = true
        windows = 0
        droppedSamples = 0
        lastWindowEpoch = null
        bootToEpoch = System.currentTimeMillis() - SystemClock.elapsedRealtime()
        window.reset()

        // Producer: keep light (NO launch here)
        accelerometer.setOnSensorSampleListener { timestampNs, x, y, z ->
            if (!x.isFinite() || !y.isFinite() || !z.isFinite()) {
                droppedSamples++
                return@setOnSensorSampleListener
            }
            val tMonoMs = timestampNs / 1_000_000L
            val ok = sampleCh.trySend(Sample(tMonoMs, x, y, z)).isSuccess
            if (!ok) droppedSamples++
        }

        // Consumer: ONE coroutine total
        consumerJob?.cancel()
        consumerJob = serviceScope.launch {
            for (s in sampleCh) {
               try{ processSample(s)
            }
               catch (t: Throwable) {
                   Log.e("RUN-SVC", "processSample crashed; keep running", t)}
            }
        }

        accelerometer.startListening()

    }

    private suspend fun processSample(s: Sample) {
        var nRel = 0
        var nUnix = 0
        var spanMs = 0L
        var fsEst = 0.0

        lateinit var outT: DoubleArray
        lateinit var outX: DoubleArray
        lateinit var outY: DoubleArray
        lateinit var outZ: DoubleArray

        var tStartRel = 0.0
        var tEndRel = 0.0
        var spanRel = 0.0

        // Only for window-to-window delta in monotonic timebase (ms)
        var emitMonoMs: Long? = null

        mutex.withLock {
            val useABefore = useA
            outT = if (useABefore) tunixA else tunixB
            outX = if (useABefore) XA else XB
            outY = if (useABefore) YA else YB
            outZ = if (useABefore) ZA else ZB

            val shouldEmit = window.push(s.tMonoMs, s.x, s.y, s.z)
            if (!shouldEmit) return@withLock

            spanMs = window.targetSpanMs(s.tMonoMs)

            nRel = window.copyWindowIntoRelativeSec(
                tSec = treal, x = outX, y = outY, z = outZ, targetSpanMs = spanMs
            )
            if (nRel > 0) {
                tStartRel = treal[0]
                tEndRel   = treal[nRel - 1]
                spanRel   = tEndRel - tStartRel
            }

            nUnix = window.copyWindowIntoUnixSec(
                tUnixSecOut = outT, x = outX, y = outY, z = outZ,
                targetSpanMs = spanMs, bootToEpoch = bootToEpoch
            )

            fsEst = window.estimateFsHz()
            emitMonoMs = s.tMonoMs

            // Flip buffer AFTER copying to it
            useA = !useABefore
        }

        // Everything below is OUTSIDE the mutex

        // ---- Hard guards (protect future C++ JNI call) ----
        if (nUnix <= 1) return
        if (nUnix > outT.size || nUnix > outX.size || nUnix > outY.size || nUnix > outZ.size) {
            Log.e("WIN-ERR", "OVERFLOW nUnix=$nUnix cap=${outT.size}")
            return
        }
        if (outT[nUnix - 1] <= outT[0]) {
            Log.e("WIN-ERR", "Non-increasing UNIX time: first=${outT[0]} last=${outT[nUnix - 1]}")
            return
        }
        if (fsEst < 5.0 || fsEst > 200.0) {
            Log.w("WIN-ERR", "Weird est fs=${"%.2f".format(fsEst)} Hz (skip)")
            return
        }

        // ---- Core sanity metrics from the WINDOW itself (fsWin) ----
        val spanSec = outT[nUnix - 1] - outT[0]
        val fsWin = if (spanSec > 0.0) (nUnix - 1) / spanSec else 0.0

        // ---- Emit interval check (target ~15s) using UNIX epoch seconds ----
        val nowEpoch = outT[nUnix - 1]
        val emitDtSec = lastWindowEpoch?.let { nowEpoch - it } ?: 0.0
        lastWindowEpoch = nowEpoch

        // ---- Logging (you already have logs; these are the extra “proof” logs) ----
        windows++

        // (A) Window-level summary (span/fs/n)
        Log.e(
            "SW",
            "win#$windows nUnix=$nUnix spanSec=${"%.3f".format(spanSec)} fsWin=${"%.2f".format(fsWin)} estFs=${"%.2f".format(fsEst)} emitDt=${"%.2f".format(emitDtSec)}s dropped=$droppedSamples"
        )

        // (B) Keep your existing logs (optional)
        if (nRel > 0) {
            Log.d("SW", "REL n=$nRel span≈${"%.3f".format(spanRel)}s X0=${"%.4f".format(outX[0])} XN=${"%.4f".format(outX[nRel - 1])}")
        }

        // (C) UNIX first/last
        Log.d("SW", "UNIX first=${"%.3f".format(outT[0])} last=${"%.3f".format(outT[nUnix - 1])}")

        Log.e("Clock", "Find Walking")
        val cadenceHz = Bridge.findWalking(outT, outX, outY, outZ, nUnix)  // πρέπει να επιστρέφει Double
        val isWalking = cadenceHz >= 1.2
        Bridge.setCadenceHz(cadenceHz.toFloat())
        CadenceState.updateCadence(
            cadenceHz = cadenceHz,
            isWalking = isWalking
        )





    }

    private fun stopClean() {
        running = false
        val sr = Bridge.stopAudio()
        Log.d("RUN-SVC", "stopAudio() -> $sr")

        try { accelerometer.stopListening() } catch (_: Throwable) {}

        consumerJob?.cancel()
        consumerJob = null

        // Drain queued samples so next START begins “clean”
        drainChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun drainChannel() {
        while (true) {
            val r = sampleCh.tryReceive().getOrNull() ?: break
        }
    }

    override fun onDestroy() {
        try { accelerometer.stopListening() } catch (_: Throwable) {}
        try { consumerJob?.cancel() } catch (_: Throwable) {}
        try { sampleCh.close() } catch (_: Throwable) {}
        try { serviceScope.cancel() } catch (_: Throwable) {}
        super.onDestroy()
    }
}
