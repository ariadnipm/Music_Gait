package com.example.accelerometer

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
    private val T = DoubleArray(2048)
    private val X = DoubleArray(2048)
    private val Y = DoubleArray(2048)
    private val Z = DoubleArray(2048)

    private var lastEmitMs: Long? = null

    // --- UI values (τρέχουσες τιμές αισθητήρα) ---
    var tMs by mutableStateOf(0L)
        private set
    var ax by mutableStateOf(0f)
        private set
    var ay by mutableStateOf(0f)
        private set
    var az by mutableStateOf(0f)
        private set

    fun startListeningSensor() {
        accelerometer.setOnSensorSampleListener { timestampNs, x, y, z ->
            val tEpochMs = timestampNs   // σε ms (monotonic)
            tMs = tEpochMs
            ax = x; ay = y; az = z

            // Επεξεργασία στο background thread (όχι στο UI)
            CoroutineScope(Dispatchers.Default).launch {
                val shouldEmit = window.push(tEpochMs, x, y, z)
                if (shouldEmit) {
                    val n = window.copyWindowInto(T, X, Y, Z)
                    val fs = window.estimateFsHz()
                    val tStart = T[0]
                    val tEnd = T[n - 1]
                    val tSpan = tEnd - tStart

                    val currentEmit = System.currentTimeMillis()
                    val deltaSec = if (lastEmitMs != null)
                        (currentEmit - lastEmitMs!!) / 1000.0 else 0.0
                    lastEmitMs = currentEmit

                    Log.e("SW", "----- ΝΕΟ ΠΑΡΑΘΥΡΟ -----")
                    Log.e("SW", "Απόσταση από προηγούμενο: ${"%.3f".format(deltaSec)} s")
                    Log.e("SW", "Μέγεθος δείγματος: $n | Εκτιμώμενο fs: ${"%.2f".format(fs)} Hz")
                    Log.w("SW", "ΠΡΩΤΟ:  t=${"%.3f".format(T[0])} s | X[0]=${"%.4f".format(X[0])}")
                    Log.w("SW", "ΤΕΛΕΥΤΑΙΟ: t=${"%.3f".format(T[n - 1])} s | X[n-1]=${"%.4f".format(X[n - 1])}")
                    // Εκτύπωσε span για έλεγχο ομοιομορφίας
                    if (n > 0) {
                        val tSpan = T[n - 1] - T[0]
                        Log.d("SW", "Χρονικό εύρος παραθύρου: ${"%.3f".format(tSpan)} s")
                    }
                    // Πρώτη και τελευταία γραμμή του παραθύρου


                    // TODO: Εδώ μπορείς να καλέσεις τον walking algorithm
                    // WalkingAlgorithm.process(X.copyOf(n), Y.copyOf(n), Z.copyOf(n), fs)
                }
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
