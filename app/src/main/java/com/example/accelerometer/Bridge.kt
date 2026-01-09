package com.example.accelerometer

object Bridge {
    init {
        System.loadLibrary("accelerometer")
    }

    external fun findWalkingDebug(
        tUnixSec: DoubleArray,
        x: DoubleArray,
        y: DoubleArray,
        z: DoubleArray,
        n: Int
    ): Int
}
