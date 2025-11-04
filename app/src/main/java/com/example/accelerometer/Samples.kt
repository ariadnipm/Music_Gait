package com.example.accelerometer

data class Samples(val tMs: Long, val x: Float, val y: Float, val z: Float)

data class Bout(
    val t: DoubleArray,
    val x: DoubleArray,
    val y: DoubleArray,
    val z: DoubleArray
)
