package com.example.accelerometer

import kotlin.math.ceil
import kotlin.math.max


class SlidingWindow(
    private val windowlength: Long = 30_000L,
    private val step: Long = 15_000L,
    private val minEmit: Long = 15_000L,
    maxHz: Double = 100.0,
    headroomFactor: Double = 1.3
) {
    /** ring buffer creation */
    private val cap: Int = run {
        val basis = max(windowlength, minEmit)
        val capEst = ceil((basis / 1000.0) * maxHz * headroomFactor).toInt()
        max(64, capEst)
    }
    private val tMsBuf = LongArray(cap)
    private val xBuf   = FloatArray(cap)
    private val yBuf   = FloatArray(cap)
    private val zBuf   = FloatArray(cap)

    private var head = 0
    private var size = 0


    private var firstSeenMs: Long? = null
    private var gridAnchorMs: Long? = null
    private var nextEdgeMs: Long? = null

    /** Decides whether it's the warm-up phase or not(3s or 6s). */
    fun targetSpanMs(now: Long): Long {
        val full = (firstSeenMs != null) && ((now - firstSeenMs!!) >= windowlength)
        return if (full) windowlength else minEmit
    }

    /** Push a new sample and return true when it's time to emit. */
    fun push(tMs: Long, x: Float, y: Float, z: Float): Boolean {
        if (firstSeenMs == null) firstSeenMs = tMs
        append(tMs, x, y, z)


        val cutoff = tMs - windowlength
        while (size > 0 && firstTimeMs() < cutoff) popOldest()


        val span = if (size > 1) lastTimeMs() - firstTimeMs() else 0L
        if (span < minEmit) return false


        if (nextEdgeMs == null) {
            val firstTarget = firstSeenMs!! + minEmit
            gridAnchorMs = ceilToGrid(firstTarget, step)
            nextEdgeMs = gridAnchorMs
        }

        if (tMs >= nextEdgeMs!!) {
            do { nextEdgeMs = nextEdgeMs!! + step } while (nextEdgeMs!! <= tMs)
            return true
        }
        return false
    }



    /** RELATIVE seconds (0..span) για logs/plots. */
    fun copyWindowIntoRelativeSec(
        tSec: DoubleArray, x: DoubleArray, y: DoubleArray, z: DoubleArray,
        targetSpanMs: Long
    ): Int {
        if (size == 0) return 0
        val tEnd = lastTimeMs()
        if (targetSpanMs <= 0L) return 0
        val cutoff = tEnd - targetSpanMs
        val startOffset = firstIndexAtOrAfter(cutoff)
        val n = size - startOffset
        require(tSec.size >= n && x.size >= n && y.size >= n && z.size >= n)

        val startIdx = (head + startOffset) % cap
        val t0 = tMsBuf[startIdx]
        var k = 0
        var i = startOffset
        while (i < size) {
            val idx = (head + i) % cap
            tSec[k] = (tMsBuf[idx] - t0)/1000.0
            x[k] = xBuf[idx].toDouble()
            y[k] = yBuf[idx].toDouble()
            z[k] = zBuf[idx].toDouble()
            k++; i++
        }
        return n
    }


    fun copyWindowIntoUnixSec(
        tUnixSecOut: DoubleArray, x: DoubleArray, y: DoubleArray, z: DoubleArray,
        targetSpanMs: Long, bootToEpoch: Long
    ): Int {
        if (size == 0) return 0
        val tEnd = lastTimeMs()
        if (targetSpanMs <= 0L) return 0
        val cutoff = tEnd - targetSpanMs
        val startOffset = firstIndexAtOrAfter(cutoff)
        val n = size - startOffset
        require(tUnixSecOut.size >= n && x.size >= n && y.size >= n && z.size >= n)

        var k = 0
        var i = startOffset
        while (i < size) {
            val idx = (head + i) % cap
            val epochMs = bootToEpoch + tMsBuf[idx]
            tUnixSecOut[k] = epochMs / 1000.0
            x[k] = xBuf[idx].toDouble()
            y[k] = yBuf[idx].toDouble()
            z[k] = zBuf[idx].toDouble()
            k++; i++
        }
        return n
    }


    fun estimateFsHz(): Double {
        val n = size
        if (n < 2) return 0.0
        val spanSec = (lastTimeMs() - firstTimeMs()).coerceAtLeast(1L) / 1000.0
        return (n - 1) / spanSec
    }



    private fun firstIndexAtOrAfter(cutoffT: Long): Int {
        var i = 0
        while (i < size) {
            val idx = (head + i) % cap
            if (tMsBuf[idx] >= cutoffT) break
            i++
        }
        return i
    }

    private fun append(t: Long, x: Float, y: Float, z: Float) {
        if (size < cap) {
            val idx = (head + size) % cap
            tMsBuf[idx] = t; xBuf[idx] = x; yBuf[idx] = y; zBuf[idx] = z
            size++
        } else {
            head = (head + 1) % cap
            val idx = (head + size - 1) % cap
            tMsBuf[idx] = t; xBuf[idx] = x; yBuf[idx] = y; zBuf[idx] = z
        }
    }

    private fun popOldest() {
        if (size == 0) return
        head = (head + 1) % cap
        size--
    }

    private fun firstTimeMs(): Long = tMsBuf[head]
    private fun lastTimeMs(): Long {
        val idx = (head + size - 1) % cap
        return tMsBuf[idx]
    }

    private fun ceilToGrid(t: Long, step: Long): Long {
        val r = t % step
        return if (r == 0L) t else t + (step - r)
    }
}
