package com.example.accelerometer

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlin.invoke

abstract class AndroidSensor(private val context: Context,
                             private val sensorFeature: String,
                             private val sensorType: Int ): MeasurableSensor(sensorType), SensorEventListener {
    override val doesSensorExist: Boolean
        get() = context.packageManager.hasSystemFeature(sensorFeature)

    private lateinit var sensorManager: SensorManager
    private var sensor: Sensor? = null

    @RequiresApi(Build.VERSION_CODES.M)
    override fun startListening() {
        if (!doesSensorExist) {
            return

        }
        if (!::sensorManager.isInitialized && sensor == null) {
            sensorManager = context.getSystemService(
                SensorManager::class.java
            ) as SensorManager
            sensor = sensorManager.getDefaultSensor(sensorType)

        }
        sensor?.let {
            sensorManager.registerListener(this, it, 20_000,0)

        }
    }

    override fun stopListening() {
        if (!doesSensorExist || !::sensorManager.isInitialized) {
            return
        }

        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
       // android.util.Log.d("SENSOR", "ax=${event?.values[0]}")

        if (!doesSensorExist) {
            return
        }
        if (event?.sensor?.type == sensorType ) {
            onSensorValuesChanged?.invoke(event.values.toList())

            val v = event.values
            if (v.size >= 3) {
                val tEpochMs = event.timestamp
                onSensorSample?.invoke(tEpochMs, v[0], v[1], v[2])
            }
        }
    }

    override fun onAccuracyChanged(p0: Sensor?, p1: Int) = Unit


}