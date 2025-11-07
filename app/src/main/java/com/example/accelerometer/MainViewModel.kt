package com.example.accelerometer

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sqrt

@HiltViewModel
class MainViewModel @Inject constructor(
    private val accelerometer: MeasurableSensor
) : ViewModel() {

    // --- Sliding window (τελική έκδοση) ---
    private val window = SlidingWindow(
        windowMs = 6_000L,   // 6 s πλήρες παράθυρο
        hopMs = 1_000L,      // 1 s hop
        minEmitMs = 3_000L,  // 3 s warm-up
        maxHz = 100.0
    )

    // Προκαθορισμένοι buffers (επανάχρηση – χωρίς allocations)
    // A) Για relative logging (0..span sec)
    private val Trel = DoubleArray(4096)
    // B) Για Unix seconds (t_bout) που ζητά ο walking algorithm
    private val Tunix = DoubleArray(4096)
    // C) Για monotonic ms αν ποτέ χρειαστείς ABS export
    private val TmsMono = LongArray(4096)

    private val X = DoubleArray(4096)
    private val Y = DoubleArray(4096)
    private val Z = DoubleArray(4096)

    // για μέτρηση απόστασης παραθύρων σε MONOTONIC
    private var lastEmitBootMs: Long? = null

    // --- UI values (τρέχουσες τιμές αισθητήρα, σε EPOCH ms για εμφάνιση) ---
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

    fun startListeningSensor() {
        accelerometer.setOnSensorSampleListener { timestampNs, x, y, z ->
            // 1) MONOTONIC ms για logic
            val tMonoMs = timestampNs/1_000_000L
            Log.e("Time", "$tMonoMs")
            // 2) Bridge MONOTONIC -> EPOCH (για UI/Export)
            val bootToEpochMs = System.currentTimeMillis() - SystemClock.elapsedRealtime()


            // UI δείχνει epoch (προαιρετικό)
            //onNewSample(tEpochMs, x, y, z)

            // Επεξεργασία στο background thread (όχι στο UI)
            viewModelScope.launch(Dispatchers.Default) {
                // SlidingWindow δουλεύει ΜΟΝΟ με MONOTONIC
                val shouldEmit = window.push(tMonoMs, x, y, z)
                if (!shouldEmit) return@launch

                // Span export: 3s στο warm-up, 6s μετά
                val spanMs = window.targetSpanMs(tMonoMs)

                // (A) Relative export (0..span sec) για έλεγχο/plots/logs
                val nRel = window.copyWindowIntoRelativeSec(
                    tSec = Trel, x = X, y = Y, z = Z, targetSpanMs = spanMs
                )

                // (B) UNIX seconds export για τον walking algorithm (αυτό θα χρησιμοποιήσεις)
                val nUnix = window.copyWindowIntoUnixSec(
                    tUnixSecOut = Tunix, x = X, y = Y, z = Z,
                    targetSpanMs = spanMs, bootToEpochMs = bootToEpochMs
                )

                // Εκτίμηση fs από το τρέχον buffer
                val fs = window.estimateFsHz()

                // Απόσταση από προηγούμενο emit (MONOTONIC)
                val deltaSec = if (lastEmitBootMs != null)
                    (tMonoMs - lastEmitBootMs!!) /1.000 else 0.0
                lastEmitBootMs = tMonoMs

                // -------- Logs για έλεγχο --------
                if (nRel > 0) {
                    val tStartRel = Trel[0]
                    val tEndRel   = Trel[nRel - 1]
                    val spanRel   = tEndRel - tStartRel

                    Log.e("SW", "----- ΝΕΟ ΠΑΡΑΘΥΡΟ -----")
                    Log.e("SW", "Απόσταση από προηγούμενο: ${"%.3f".format(deltaSec)} s")
                    Log.e("SW", "Μέγεθος: $nRel | fs≈${"%.2f".format(fs)} Hz | span≈${"%.3f".format(spanRel)} s")
                    Log.w("SW", "REL first=${"%.3f".format(tStartRel)}s last=${"%.3f".format(tEndRel)}s X0=${"%.4f".format(X[0])} XN=${"%.4f".format(X[nRel-1])}")
                }

                if (nUnix > 0) {
                    val firstEpoch = Tunix[0]
                    val lastEpoch  = Tunix[nUnix - 1]
                    Log.d("SW", "UNIX first=${"%.3f".format(firstEpoch)} last=${"%.3f".format(lastEpoch)}")


                }

                // -------- ΕΔΩ καλείς τον walking algorithm --------
                // Tunix: DoubleArray σε UNIX seconds (ακριβώς όπως ζητάει το preprocess_bout)
                // X/Y/Z: DoubleArray με τιμές ανά δείγμα
                // fs: εκτίμηση συχνότητας
                //
                // example:
                // Walking.preprocess_bout(
                //     t_bout = Tunix.copyOf(nUnix),
                //     x_bout = X.copyOf(nUnix),
                //     y_bout = Y.copyOf(nUnix),
                //     z_bout = Z.copyOf(nUnix),
                //     fs = 10 // ή fs.toInt() αν το θες από estimate
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
