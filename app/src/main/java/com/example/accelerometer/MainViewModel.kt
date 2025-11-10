package com.example.accelerometer

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class MainViewModel @Inject constructor(
    private val accelerometer: MeasurableSensor,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val window = SlidingWindow(
        windowMs = 6_000L,
        hopMs = 1_000L,
        minEmitMs = 3_000L,
        maxHz = 100.0
    )
    private val bootToEpochMs = System.currentTimeMillis() - SystemClock.elapsedRealtime()

    // --- Relative time (κρατάω όπως το είχες) ---
    private val Trel = DoubleArray(4096)

    // --- Ping–pong snapshots για να μη γίνεται overwrite ---
    private val TunixA = DoubleArray(4096)
    private val XA = DoubleArray(4096)
    private val YA = DoubleArray(4096)
    private val ZA = DoubleArray(4096)

    private val TunixB = DoubleArray(4096)
    private val XB = DoubleArray(4096)
    private val YB = DoubleArray(4096)
    private val ZB = DoubleArray(4096)

    private var useA = true

    private var lastEmitBootMs: Long? = null

    var tMs by mutableStateOf(0L)
        private set
    var ax by mutableStateOf(0f)
        private set
    var ay by mutableStateOf(0f)
        private set
    var az by mutableStateOf(0f)
        private set

    private fun onNewSample(tEpochMs: Long, x: Float, y: Float, z: Float) {
        tMs = tEpochMs
        ax = x
        ay = y
        az = z
    }

    private var windowsDumped = 0

    // 🔒 Σειριοποιεί push→export ώστε το snapshot να είναι συνεπές
    private val swMutex = Mutex()

    fun startListeningSensor() {
        accelerometer.setOnSensorSampleListener { timestampNs, x, y, z ->
            // 1) MONOTONIC ms
            val tMonoMs = timestampNs / 1_000_000L

            // UI στο main thread
            viewModelScope.launch {
                onNewSample(tMonoMs + bootToEpochMs, x, y, z)
            }

            // Επεξεργασία / emit
            viewModelScope.launch(Dispatchers.Default) {
                var nRel = 0
                var nUnix = 0
                var spanMs = 0L
                var fs = 0.0

                // references στο σετ που ΘΑ γεμίσει τώρα
                lateinit var outT: DoubleArray
                lateinit var outX: DoubleArray
                lateinit var outY: DoubleArray
                lateinit var outZ: DoubleArray

                // για logs relative εκτός lock
                var tStartRel = 0.0
                var tEndRel = 0.0
                var spanRel = 0.0

                // 🔒 Κρίσιμο τμήμα: push → targetSpanMs → export (Relative+Unix) → fs → toggle
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
                        tEndRel = Trel[nRel - 1]
                        spanRel = tEndRel - tStartRel
                    }

                    // (B) UNIX seconds export (για walking algorithm)
                    nUnix = window.copyWindowIntoUnixSec(
                        tUnixSecOut = outT, x = outX, y = outY, z = outZ,
                        targetSpanMs = spanMs, bootToEpochMs = bootToEpochMs
                    )

                    fs = window.estimateFsHz()
                    lastEmitBootMs = tMonoMs

                    // εναλλαγή σετ για το επόμενο emit (αποφυγή overwrite)
                    useA = !useABefore
                }
                // 🔓 Εκτός lock: ασφαλής χρήση του snapshot outT/outX/outY/outZ

                // -------- Logs για έλεγχο --------
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

               /* if (nUnix > 0 && windowsDumped < 10) {
                    saveWindowToCsv(
                        context = appContext,
                        windowIndex = windowsDumped + 1,
                        n = nUnix,
                        tUnix = outT,  // <- snapshot από ping-pong
                        x = outX,
                        y = outY,
                        z = outZ
                    )
                    windowsDumped++
                }
  */

                // -------- ΕΔΩ καλείς τον walking algorithm --------
                // Walking.preprocess_bout(
                //     t_bout = outT.copyOf(nUnix),   // ή δώσ' τα απευθείας αν ο native τα διαβάζει sync
                //     x_bout = outX.copyOf(nUnix),
                //     y_bout = outY.copyOf(nUnix),
                //     z_bout = outZ.copyOf(nUnix),
                //     fs = 10 // ή fs.toInt() από estimate
                // )
            }
        }
        accelerometer.startListening()
    }

    fun stopListeningSensor() {
        accelerometer.stopListening()
    }

    override fun onCleared() {
        super.onCleared()
        accelerometer.stopListening()
    }
}
