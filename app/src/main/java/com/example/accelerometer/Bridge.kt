package com.example.accelerometer

object Bridge {
    init {
        System.loadLibrary("accelerometer")
    }

    external fun findWalking(
        tUnixSec: DoubleArray,
        x: DoubleArray,
        y: DoubleArray,
        z: DoubleArray,
        n: Int
    ): DoubleArray

    external fun startAudio(): Int
    external fun stopAudio(): Int
    external fun setCadenceHz(cadenceHz: Float)
}