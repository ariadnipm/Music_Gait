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
    ): Double
}
