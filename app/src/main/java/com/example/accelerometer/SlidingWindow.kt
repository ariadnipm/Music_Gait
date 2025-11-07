package com.example.accelerometer

import android.util.Log
import kotlin.math.ceil
import kotlin.math.max

/**
 * High-precision time-based sliding window using a ring buffer.
 *
 * Internal time: MONOTONIC ms (event.timestamp / 1e6).
 * Warm-up: emits 3 s windows until total span >= 6 s.
 * Full: emits 6 s windows every 1 s (grid-locked, no drift).
 * O(1) insert/remove, zero allocations after init.
 */
class SlidingWindow(
    private val windowMs: Long = 6_000L,   // πλήρες παράθυρο
    private val hopMs: Long = 1_000L,      // βήμα emit
    private val minEmitMs: Long = 3_000L,  // warm-up μέγεθος
    maxHz: Double = 100.0,
    headroomFactor: Double = 1.3
) {
    // ---- ring buffer ----
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

    // ---- timing ----
    private var firstSeenMs: Long? = null
    private var gridAnchorMs: Long? = null   // αφετηρία πλέγματος (πληροφοριακό)
    private var nextEdgeMs: Long? = null     // επόμενο emit πάνω στο πλέγμα

    /** 3s στο warm-up, 6s μετά. Κάλεσέ το τη στιγμή του emit. */
    fun targetSpanMs(now: Long): Long {
        val full = (firstSeenMs != null) && ((now - firstSeenMs!!) >= windowMs)
        return if (full) windowMs else minEmitMs
    }

    /** Push νέου δείγματος (tMs = MONOTONIC ms). Επιστρέφει true όταν πρέπει να γίνει emit. */
    fun push(tMs: Long, x: Float, y: Float, z: Float): Boolean {
        if (firstSeenMs == null) firstSeenMs = tMs
        append(tMs, x, y, z)

        // Κρατάμε ΠΑΝΤΑ ≤ windowMs ιστορία (προβλέψιμο state)
        val cutoff = tMs - windowMs
        while (size > 0 && firstTimeMs() < cutoff) popOldest()

        // Για να επιτρέψουμε emit θέλουμε τουλάχιστον minEmitMs διαθέσιμα
        val span = if (size > 1) lastTimeMs() - firstTimeMs() else 0L
        if (span < minEmitMs) return false

        // Κλείδωμα πλέγματος στο πρώτο emit: ceil(firstSeen+3s, hop)
        if (nextEdgeMs == null) {
            val firstTarget = firstSeenMs!! + minEmitMs
            gridAnchorMs = ceilToGrid(firstTarget, hopMs)   // info only
            nextEdgeMs = gridAnchorMs
        }

        // Emit όταν περάσουμε το grid edge — χωρίς drift.
        if (tMs >= nextEdgeMs!!) {
            // Catch-up χωρίς bursts: πηδάμε edges μέχρι να πάμε ακριβώς μπροστά από now
            do { nextEdgeMs = nextEdgeMs!! + hopMs } while (nextEdgeMs!! <= tMs)
            return true
        }
        return false
    }

    // ---------- Exporters ----------

    /** ABSOLUTE MONOTONIC ms για το *τελευταίο* targetSpanMs. */
    fun copyWindowIntoMs(
        tMsOut: LongArray, x: DoubleArray, y: DoubleArray, z: DoubleArray,
        targetSpanMs: Long
    ): Int {
        if (size == 0) return 0
        val tEnd = lastTimeMs()
        val cutoff = tEnd - targetSpanMs
        val startOffset = firstIndexAtOrAfter(cutoff)
        val n = size - startOffset
        require(tMsOut.size >= n && x.size >= n && y.size >= n && z.size >= n)

        var k = 0
        var i = startOffset
        while (i < size) {
            val idx = (head + i) % cap
            tMsOut[k] = tMsBuf[idx]
            x[k] = xBuf[idx].toDouble()
            y[k] = yBuf[idx].toDouble()
            z[k] = zBuf[idx].toDouble()
            k++; i++
        }
        return n
    }

    /** RELATIVE seconds (0..span) για logs/plots, στο *τελευταίο* targetSpanMs. */
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

    /**
     * UNIX seconds (double) exporter για το *τελευταίο* targetSpanMs.
     * Δίνεις το bootToEpochMs = System.currentTimeMillis() - SystemClock.elapsedRealtime()
     * ώστε να χαρτογραφήσουμε MONOTONIC -> EPOCH.
     */
    fun copyWindowIntoUnixSec(
        tUnixSecOut: DoubleArray, x: DoubleArray, y: DoubleArray, z: DoubleArray,
        targetSpanMs: Long, bootToEpochMs: Long
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
            val epochMs = bootToEpochMs + tMsBuf[idx]
            tUnixSecOut[k] = epochMs / 1000.0
            x[k] = xBuf[idx].toDouble()
            y[k] = yBuf[idx].toDouble()
            z[k] = zBuf[idx].toDouble()
            k++; i++
        }
        return n
    }

    /** Εκτίμηση fs από το τρέχον buffer. */
    fun estimateFsHz(): Double {
        val n = size
        if (n < 2) return 0.0
        val spanSec = (lastTimeMs() - firstTimeMs()).coerceAtLeast(1L) / 1000.0
        return (n - 1) / spanSec
    }

    fun count(): Int = size

    // ---------- helpers ----------
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
