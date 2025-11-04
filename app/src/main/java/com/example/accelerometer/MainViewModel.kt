package com.example.accelerometer

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import  androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import dagger.hilt.android.internal.Contexts.getApplication
import java.io.File

import java.text.SimpleDateFormat
import java.util.*

fun Long.toDateTimeString(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    return sdf.format(Date(this)) // τοπική ώρα
}

fun Long.toSecondsSinceEpoch(): Double = this / 1000.0






@HiltViewModel
class MainViewModel @Inject constructor(

    private val accelerometer: MeasurableSensor
) : ViewModel() {
    private val window = SlidingWindow(6_000, 1_000, 3_000)
    private var lastEmitTimeMs: Double? = null


    var tMs by mutableStateOf(0L)
        private set
    var ax by mutableStateOf(0f)
        private set
    var ay by mutableStateOf(0f)
        private set
    var az by mutableStateOf(0f)
        private set

    fun startListeningSensor() {
        accelerometer.setOnSensorSampleListener { timestamps, x, y, z ->
            tMs = timestamps / 1_000_000L
            ax = x
            ay = y
            az = z
            val bout = window.pushAndMaybeEmit(timestamps, x, y, z)
            if (bout != null) {
                val (t_bout, x_bout, y_bout, z_bout) = bout

                val nT = bout.t.size
                val nX = bout.x.size
                val nY = bout.y.size
                val nZ = bout.z.size
                val now = bout.t.last()   // τελευταίος χρόνος του παραθύρου
                val last = lastEmitTimeMs
                val deltaSec = if (last != null) now - last else 0.0
                lastEmitTimeMs = now



                Log.e("SW", "----- ΝΕΟ ΠΑΡΑΘΥΡΟ -----")
                Log.e("SW", "Διάστημα από προηγούμενο: ${"%.3f".format(deltaSec)} sec")
                Log.e("SW", "Μέγεθος: t=$nT  x=$nX  y=$nY  z=$nZ")
                Log.d("SLIDING_WINDOW", "--------- Νέο Παράθυρο ---------")
                Log.d("SLIDING_WINDOW", "t: ${t_bout.joinToString(", ")}")
                Log.d("SLIDING_WINDOW", "x: ${x_bout.joinToString(", ")}")
                Log.d("SLIDING_WINDOW", "y: ${y_bout.joinToString(", ")}")
                Log.d("SLIDING_WINDOW", "z: ${z_bout.joinToString(", ")}")
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


