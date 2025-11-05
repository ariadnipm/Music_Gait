package com.example.accelerometer

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Long.toSecondsSinceEpoch(): Double {
    return this / 1000.0
}

fun Long.toDateTimeString(): String {
    val date = Date(this)
    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    return format.format(date)
}
