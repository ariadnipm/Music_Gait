package com.example.accelerometer

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import  androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@HiltViewModel
class MainViewModel @Inject constructor(

    private val accelerometer: MeasurableSensor
) : ViewModel() {


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
            tMs = timestamps
            ax = x
            ay = y
            az = z
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
