package com.example.accelerometer

abstract class MeasurableSensor(sensorType: Int) {
    // protected var onSensorValuesChanged: ((List<Float>)-> Unit)? = null
    abstract val doesSensorExist: Boolean

    protected var onSensorSample: ((timestamp: Long, x: Float, y: Float, z: Float) -> Unit)? = null
    abstract fun startListening()
    abstract fun stopListening()

    //fun setOnSensorValuesChangedListener(listener:
    //                                       (List<Float>)-> Unit) {
    // onSensorValuesChanged = listener
    // }
    fun setOnSensorSampleListener(listener: ((timestamp: Long, x: Float, y: Float, z: Float) -> Unit)) {
        onSensorSample = listener
    }
}