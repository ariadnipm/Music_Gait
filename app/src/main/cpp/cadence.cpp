#include <jni.h>
#include <android/log.h>
#include <vector>
#include <cmath>
#include <complex>
#include <algorithm>   // for std::min
#include "cadencefun.h"
#include "fcwt.h"

extern "C"
JNIEXPORT jdoubleArray JNICALL
Java_com_example_accelerometer_Bridge_findWalking(
        JNIEnv* env,
        jobject,
        jdoubleArray tUnixSec,
        jdoubleArray x,
        jdoubleArray y,
        jdoubleArray z,
        jint n
) {
    // Return empty array on invalid input (instead of 0.0)
    if (!tUnixSec || !x || !y || !z || n < 2) {
        return env->NewDoubleArray(0);
    }

    jdouble* pt = env->GetDoubleArrayElements(tUnixSec, nullptr);
    jdouble* px = env->GetDoubleArrayElements(x, nullptr);
    jdouble* py = env->GetDoubleArrayElements(y, nullptr);
    jdouble* pz = env->GetDoubleArrayElements(z, nullptr);
    if (!pt || !px || !py || !pz) {
        if (pt) env->ReleaseDoubleArrayElements(tUnixSec, pt, JNI_ABORT);
        if (px) env->ReleaseDoubleArrayElements(x, px, JNI_ABORT);
        if (py) env->ReleaseDoubleArrayElements(y, py, JNI_ABORT);
        if (pz) env->ReleaseDoubleArrayElements(z, pz, JNI_ABORT);
        return env->NewDoubleArray(0);
    }

    std::vector<double> t((size_t)n), X((size_t)n), Y((size_t)n), Z((size_t)n);
    for (int i = 0; i < n; i++) {
        t[(size_t)i] = pt[i];
        X[(size_t)i] = px[i];
        Y[(size_t)i] = py[i];
        Z[(size_t)i] = pz[i];
    }

    // Keep your timestamp repair + log
    for (size_t i = 1; i < t.size(); ++i) {
        if (t[i] <= t[i - 1]) {
            t[i] = t[i - 1] + 1e-3;
            __android_log_print(ANDROID_LOG_WARN, "WALK_CAD", "timestamp repair");
        }
    }

    env->ReleaseDoubleArrayElements(tUnixSec, pt, JNI_ABORT);
    env->ReleaseDoubleArrayElements(x, px, JNI_ABORT);
    env->ReleaseDoubleArrayElements(y, py, JNI_ABORT);
    env->ReleaseDoubleArrayElements(z, pz, JNI_ABORT);

    const int fs = 10;

    auto prep = preprocess_bout(t, X, Y, Z, fs);
    const auto& t_sec = prep.first;
    const auto& vm    = prep.second;

    if (t_sec.empty() || vm.empty()) {
        return env->NewDoubleArray(0);
    }

    const double min_amp = 0.3;
    const std::pair<double,double> step_freq = {1.4, 2.3};
    const double alpha = 0.6;
    const double beta  = 2.5;
    const int min_t = 3;
    const int delta = 20;

    std::vector<double> cad = find_walking(vm, fs, min_amp, step_freq, alpha, beta, min_t, delta);

    // Keep your per-second logs
    int nsec = (int)cad.size();
    for (int s = 0; s < std::min(nsec, 30); s++) {
        double ts = (s < (int)t_sec.size()) ? t_sec[(size_t)s] : 0.0;
        double f  = cad[(size_t)s];
        __android_log_print(ANDROID_LOG_DEBUG, "WALK_CAD", "sec=%02d t=%.3f cadence=%.4f Hz", s, ts, f);
    }


    double meanCadence = aggregate_window_cadence(cad, /*shortLen=*/15);

    __android_log_print(ANDROID_LOG_INFO, "WALK_CAD",
                        "window_secs=%d agg_cadence=%.2f Hz",
                        (int)cad.size(), meanCadence);


    const jsize outLen = (jsize)cad.size() + 1;
    jdoubleArray out = env->NewDoubleArray(outLen);
    if (!out) {
        return env->NewDoubleArray(0);
    }

    std::vector<jdouble> packed((size_t)outLen);
    packed[0] = (jdouble)meanCadence;
    for (size_t i = 0; i < cad.size(); ++i) {
        packed[i + 1] = (jdouble)cad[i];
    }

    env->SetDoubleArrayRegion(out, 0, outLen, packed.data());
    return out;
}
