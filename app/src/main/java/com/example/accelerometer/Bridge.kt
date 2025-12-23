package com.example.accelerometer

object Bridge {
    init {
        System.loadLibrary("accelerometer")
    }

    external fun fcwtWindowTest(
        tUnixSec: DoubleArray,
        x: DoubleArray,
        y: DoubleArray,
        z: DoubleArray,
        n: Int,
        fs: Int,
        loops: Int
    ): Int
}
