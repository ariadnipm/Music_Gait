package com.example.accelerometer

import kotlin.math.ceil
import kotlin.math.max

/**
 * High-precision time-based sliding window using a ring buffer.
 *
 * Features:
 *  - Warm-up phase: emits minEmitMs (e.g., 3s) windows until total span reaches windowMs (e.g., 6s)
 *  - After warm-up, emits fixed windows of windowMs every hopMs (e.g., 1s)
 *  - Quantized hop grid: perfectly aligned 1s hops with jitter ≈ ±1 sample
 *  - O(1) insert/remove, zero allocations after initialization
 *
 * Give timestamps in milliseconds (monotonic), e.g. event.timestamp / 1e6
 */
class SlidingWindow(
    private val windowMs: Long = 6_000L,
    private val hopMs: Long = 1_000L,
    private val minEmitMs: Long = 3_000L,
    maxHz: Double = 100.0,
    headroomFactor: Double = 1.3
) {
    // ---- Ring buffer storage ----
    private val cap: Int = run {
        val basis = max(windowMs, minEmitMs)
        val capEst = ceil((basis / 1000.0) * maxHz * headroomFactor).toInt()
        max(64, capEst)
    }
    private val tMsBuf = LongArray(cap)
    private val xBuf   = FloatArray(cap)
    private val yBuf   = FloatArray(cap)
    private val zBuf   = FloatArray(cap)

    private var head = 0
    private var size = 0

    // ---- Timing & state tracking ----
    private var firstSeenMs: Long? = null
    private var reachedFull = false
    private var justSwitchedToFull = false

    // Grid anchor and next emit edge
    private var gridAnchorMs: Long? = null
    private var nextEdgeMs: Long? = null

    /**
     * Push a new sample (tMs in ms). Returns true when it's time to emit a new window.
     */
    fun push(tMs: Long, x: Float, y: Float, z: Float): Boolean {
        if (firstSeenMs == null) firstSeenMs = tMs
        append(tMs, x, y, z)

        // Detect transition from warm-up to full window
        if (!reachedFull && (tMs - firstSeenMs!!) >= windowMs) {
            reachedFull = true
            justSwitchedToFull = true
        }

        // Target window duration: 3s warm-up, 6s after
        val targetSpanMs = if (reachedFull && !justSwitchedToFull) windowMs else minEmitMs

        // Drop old samples outside active window
        val cutoff = tMs - targetSpanMs
        while (size > 0 && firstTimeMs() < cutoff) popOldest()

        val span = if (size <= 1) 0L else lastTimeMs() - firstTimeMs()
        if (span < targetSpanMs) return false // not enough data yet

        // Initialize hop grid once when first emit is ready
        if (gridAnchorMs == null) {
            val anchor = firstSeenMs!! + minEmitMs
            gridAnchorMs = ceilToGrid(anchor, hopMs)
            nextEdgeMs = gridAnchorMs
        }

        val currentGrid = floorToGrid(tMs, gridAnchorMs!!, hopMs)

        // If we somehow skipped edges (e.g. pause/resume), resync to next full grid step
        if (nextEdgeMs!! < currentGrid) {
            nextEdgeMs = currentGrid + hopMs
        }

        // Emit when we reach or pass the next edge
        if (tMs >= nextEdgeMs!!) {
            if (justSwitchedToFull) justSwitchedToFull = false
            nextEdgeMs = currentGrid + hopMs
            return true
        }
        return false
    }

    /**
     * Copies the current window into preallocated arrays.
     * Returns how many samples were written.
     *
     * tSec: relative time (0..~window), x/y/z: acceleration.
     */
    fun copyWindowInto(
        tSec: DoubleArray,
        x: DoubleArray,
        y: DoubleArray,
        z: DoubleArray
    ): Int {
        val n = size
        require(tSec.size >= n && x.size >= n && y.size >= n && z.size >= n) {
            "Destination arrays too small (need at least $n)"
        }
        if (n == 0) return 0

        val baseIdx = head
        val t0 = tMsBuf[baseIdx]
        var i = 0
        while (i < n) {
            val idx = (baseIdx + i) % cap
            tSec[i] = tMsBuf[idx].toDouble()
            x[i] = xBuf[idx].toDouble()
            y[i] = yBuf[idx].toDouble()
            z[i] = zBuf[idx].toDouble()
            i++
        }
        return n
    }

    /** Estimates sampling frequency (Hz) from current window. */
    fun estimateFsHz(): Double {
        val n = size
        if (n < 2) return 0.0
        val spanSec = (lastTimeMs() - firstTimeMs()).coerceAtLeast(1L) / 1000.0
        return (n - 1) / spanSec
    }

    /** Current number of samples in the window. */
    fun count(): Int = size

    // ---- Internal helpers ----
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

    // ---- Grid math ----
    private fun floorToGrid(t: Long, anchor: Long, step: Long): Long {
        val delta = t - anchor
        val k = if (delta >= 0) delta / step else (delta - (step - 1)) / step
        return anchor + k * step
    }

    private fun ceilToGrid(t: Long, step: Long): Long {
        val r = t % step
        return if (r == 0L) t else t + (step - r)
    }
}
