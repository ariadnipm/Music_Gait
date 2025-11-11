// RunningService.kt
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@AndroidEntryPoint
class RunningService : Service() {

    enum class Actions { START, STOP }

    // ---- ό,τι είχες injected στο VM, τώρα injected στο Service ----
    @Inject lateinit var accelerometer: MeasurableSensor
    @Inject lateinit var serviceScope: CoroutineScope
    @Inject lateinit var window: SlidingWindow

    // --- κρατάμε ΑΚΡΙΒΩΣ τα ίδια πεδία με το ViewModel ---
    private val Trel   = DoubleArray(4096)

    private val TunixA = DoubleArray(4096)
    private val XA     = DoubleArray(4096)
    private val YA     = DoubleArray(4096)
    private val ZA     = DoubleArray(4096)

    private val TunixB = DoubleArray(4096)
    private val XB     = DoubleArray(4096)
    private val YB     = DoubleArray(4096)
    private val ZB     = DoubleArray(4096)

    private var useA = true
    private var lastEmitBootMs: Long? = null
    private var windowsDumped = 0

    private val swMutex = Mutex()
    private var bootToEpochMs: Long = 0L
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Actions.START.toString() -> start()
            Actions.STOP.toString()  -> stopClean()
            else -> start()
        }
        return START_STICKY
    }

    private fun start() {
        if (running) return
        running = true

        // Notification + foreground (όπως είχες)
        val notif = NotificationCompat.Builder(this, "running_channel")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Run is active")
            .setContentText("Collecting data…")
            .setOngoing(true)
            .build()
        startForeground(1, notif)

        // reset ανά session
        useA = true
        windowsDumped = 0
        lastEmitBootMs = null
        bootToEpochMs = System.currentTimeMillis() - SystemClock.elapsedRealtime()

        // listener ΙΔΙΑ λογική με VM
        accelerometer.setOnSensorSampleListener { timestampNs, x, y, z ->
            val tMonoMs = timestampNs / 1_000_000L

            serviceScope.launch {
                var nRel = 0
                var nUnix = 0
                var spanMs = 0L
                var fs = 0.0

                lateinit var outT: DoubleArray
                lateinit var outX: DoubleArray
                lateinit var outY: DoubleArray
                lateinit var outZ: DoubleArray

                var tStartRel = 0.0
                var tEndRel = 0.0
                var spanRel = 0.0

                swMutex.withLock {
                    val useABefore = useA
                    outT = if (useABefore) TunixA else TunixB
                    outX = if (useABefore) XA else XB
                    outY = if (useABefore) YA else YB
                    outZ = if (useABefore) ZA else ZB

                    val shouldEmit = window.push(tMonoMs, x, y, z)
                    if (!shouldEmit) return@launch

                    spanMs = window.targetSpanMs(tMonoMs)

                    // (A) Relative export (logs/plots)
                    nRel = window.copyWindowIntoRelativeSec(
                        tSec = Trel, x = outX, y = outY, z = outZ, targetSpanMs = spanMs
                    )
                    if (nRel > 0) {
                        tStartRel = Trel[0]
                        tEndRel   = Trel[nRel - 1]
                        spanRel   = tEndRel - tStartRel
                    }

                    // (B) UNIX seconds export (για walking algorithm)
                    nUnix = window.copyWindowIntoUnixSec(
                        tUnixSecOut = outT, x = outX, y = outY, z = outZ,
                        targetSpanMs = spanMs, bootToEpochMs = bootToEpochMs
                    )

                    // Εκτίμηση fs από το τρέχον buffer
                    fs = window.estimateFsHz()

                    // Απόσταση από προηγούμενο emit (MONOTONIC)
                    val deltaSec = if (lastEmitBootMs != null)
                        (tMonoMs - lastEmitBootMs!!) / 1000.0 else 0.0
                    lastEmitBootMs = tMonoMs

                    // εναλλαγή σετ (αποφυγή overwrite)
                    useA = !useABefore
                }
                // ---- εκτός lock: ασφαλής χρήση snapshot ----

                if (nRel > 0) {
                    Log.e("SW", "----- ΝΕΟ ΠΑΡΑΘΥΡΟ -----")
                    Log.e("SW", "Μέγεθος: $nRel | fs≈${"%.2f".format(fs)} Hz | span≈${"%.3f".format(spanRel)} s")
                    Log.w("SW", "REL first=${"%.3f".format(tStartRel)}s last=${"%.3f".format(tEndRel)}s X0=${"%.4f".format(outX[0])} XN=${"%.4f".format(outX[nRel-1])}")
                }

                if (nUnix > 0) {
                    val firstEpoch = outT[0]
                    val lastEpoch  = outT[nUnix - 1]
                    Log.d("SW", "UNIX first=${"%.3f".format(firstEpoch)} last=${"%.3f".format(lastEpoch)}")
                }

                if (nUnix > 0 && windowsDumped < 10) {
                    saveWindowToCsv(
                        context = applicationContext,
                        windowIndex = windowsDumped + 1,
                        n = nUnix,
                        tUnix = outT,
                        x = outX,
                        y = outY,
                        z = outZ
                    )
                    windowsDumped++
                }

                // -------- εδώ κάνεις call τον walking algorithm --------
                // Walking.preprocess_bout(
                //   t_bout = outT.copyOf(nUnix),
                //   x_bout = outX.copyOf(nUnix),
                //   y_bout = outY.copyOf(nUnix),
                //   z_bout = outZ.copyOf(nUnix),
                //   fs = 10 // ή fs.toInt()
                // )
            }
        }
        accelerometer.startListening()
    }

    private fun stopClean() {
        running = false
        accelerometer.stopListening()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }
}
