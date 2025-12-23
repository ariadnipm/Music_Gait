#include <jni.h>
#include <android/log.h>
#include <vector>
#include <cmath>
#include <complex>

// include fcwt headers (προσαρμόζεις αν το path είναι αλλιώς)
#include "fcwt.h"   // ή "fcwt/fcwt.h" ανάλογα πως το έχεις στα include dirs

#define LOG_TAG "FCWT_TEST"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static bool isFiniteAll(const std::vector<float>& v) {
    for (float x : v) {
        if (!std::isfinite(x)) return false;
    }
    return true;
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_accelerometer_Bridge_fcwtWindowTest(
        JNIEnv* env,
        jobject /*clazz*/,
        jdoubleArray tUnixSec,
        jdoubleArray x,
        jdoubleArray y,
        jdoubleArray z,
        jint n,
        jint fs /*not critical here, but keep*/,
        jint loops /*e.g. 50*/) {

    if (!tUnixSec || !x || !y || !z) {
        LOGE("Null arrays");
        return 0;
    }
    if (n < 64) {
        LOGE("n too small: %d", n);
        return 0;
    }

    // --- Read only first n elements ---
    jdouble* px = env->GetDoubleArrayElements(x, nullptr);
    jdouble* py = env->GetDoubleArrayElements(y, nullptr);
    jdouble* pz = env->GetDoubleArrayElements(z, nullptr);
    if (!px || !py || !pz) {
        LOGE("Failed GetDoubleArrayElements");
        return 0;
    }

    // Build vector magnitude (vm) as float input for FCWT (matches fftwf float API)
    std::vector<float> vm;
    vm.resize((size_t)n);
    for (int i = 0; i < n; i++) {
        double xx = px[i], yy = py[i], zz = pz[i];
        double mag = std::sqrt(xx*xx + yy*yy + zz*zz);
        vm[i] = (float)(mag - 1.0);  // like your preprocess does after g-normalize
    }

    env->ReleaseDoubleArrayElements(x, px, JNI_ABORT);
    env->ReleaseDoubleArrayElements(y, py, JNI_ABORT);
    env->ReleaseDoubleArrayElements(z, pz, JNI_ABORT);

    if (!isFiniteAll(vm)) {
        LOGE("vm contains NaN/Inf (skip)");
        return 0;
    }

    // --- Minimal FCWT setup (simple, single thread, no OpenMP) ---
    // Use Morlet just for testing the pipeline; later you’ll swap to your GMW.
    Morlet wav(2.0f);
    // scales: fs=50 (or estimated), freq range 0.5..10Hz, 16 scales (safe for test)
    Scales scales(&wav, SCALETYPE::FCWT_LINFREQS, (int)fs, 0.5f, 10.0f, 16);

    // FCWT constructor in your header is:
    // FCWT(Wavelet *pwav, int pthreads, bool puse_optimalization_schemes, bool puse_normalization)
    FCWT fcwt(&wav, 1, false, false);

    const int nSc = scales.nscales;
    std::vector<std::complex<float>> out;
    out.resize((size_t)n * (size_t)nSc);

    auto runOnce = [&](float &m0, float &mmid) -> bool {
        try {
            fcwt.cwt(vm.data(), n, out.data(), &scales);
        } catch (...) {
            LOGE("Exception during fcwt.cwt()");
            return false;
        }
        // pick a couple magnitudes as “signature”
        int idx0 = 0;
        int idxMid = (nSc/2) * n + (n/2);
        auto c0 = out[(size_t)idx0];
        auto cm = out[(size_t)idxMid];
        m0   = std::abs(c0);
        mmid = std::abs(cm);
        if (!std::isfinite(m0) || !std::isfinite(mmid)) return false;
        return true;
    };

    // --- Determinism check (2 runs same input) ---
    float a0=0, aMid=0, b0=0, bMid=0;
    if (!runOnce(a0, aMid)) {
        LOGE("FCWT runOnce #1 failed");
        return 0;
    }
    if (!runOnce(b0, bMid)) {
        LOGE("FCWT runOnce #2 failed");
        return 0;
    }

    float d0   = std::fabs(a0 - b0);
    float dMid = std::fabs(aMid - bMid);

    // --- Small stress loop ---
    for (int i = 0; i < loops; i++) {
        float t0=0, tMid=0;
        if (!runOnce(t0, tMid)) {
            LOGE("FCWT loop failed at i=%d", i);
            return 0;
        }
        // occasional log
        if (i == 0 || i == loops-1) {
            LOGD("loop i=%d mag0=%.6f magMid=%.6f", i, t0, tMid);
        }
    }

    LOGD("FCWT OK | n=%d fs=%d scales=%d | m0=%.6f mMid=%.6f | detDelta=(%.6g, %.6g) loops=%d",
         n, fs, nSc, a0, aMid, d0, dMid, loops);

    // if determinism deltas are crazy, mark fail
    // (tiny floating diffs are ok; if it’s big it indicates UB)
    if (d0 > 1e-3f || dMid > 1e-3f) {
        LOGE("Determinism suspicious: d0=%.6g dMid=%.6g", d0, dMid);
        return 0;
    }

    return 1;
}
