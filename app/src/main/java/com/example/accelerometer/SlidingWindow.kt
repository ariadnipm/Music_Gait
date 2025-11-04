package com.example.accelerometer

import java.util.ArrayDeque

class SlidingWindow(
    private val windowMs: Long = 6_000L,   // 6s
    private val hopMs: Long = 1_000L,      // 1s
    private val minEmitMs: Long = 3_000L   // warm-up 3s
) {
    private val buf = ArrayDeque<Samples>()
    private var lastEmitMs: Long? = null
    private var reachedFull = false

    fun pushAndMaybeEmit(
        tMs: Long, x: Float, y: Float, z: Float
    ): Bout? {

        buf.addLast(Samples(tMs, x, y, z))

        // κράτα μόνο ό,τι είναι μέσα στο παράθυρο
        val cutoff = tMs - windowMs
        while (buf.isNotEmpty() && buf.first().tMs < cutoff) {
            buf.removeFirst()
        }

        val span = if (buf.isEmpty()) 0L else (buf.last().tMs - buf.first().tMs)
        if (span >= windowMs) reachedFull = true
        val targetSpan = if (reachedFull) windowMs else minEmitMs

        val hopOk = (lastEmitMs == null) || (tMs - lastEmitMs!! >= hopMs)
        val canEmit = (span >= targetSpan) && hopOk
        if (!canEmit) return null

        lastEmitMs = tMs

        val n = buf.size
        val t = DoubleArray(n)
        val xs = DoubleArray(n)
        val ys = DoubleArray(n)
        val zs = DoubleArray(n)

        var i = 0
        for (s in buf) {
            t[i]  = s.tMs / 1000.0   // σε δευτερόλεπτα για ανάγνωση
            xs[i] = s.x.toDouble()
            ys[i] = s.y.toDouble()
            zs[i] = s.z.toDouble()
            i++
        }
        return Bout(t, xs, ys, zs)
    }
}