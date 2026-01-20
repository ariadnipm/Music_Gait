#include <jni.h>
#include <atomic>

#include "AudioEngine.h"

namespace {
    mg::audio::AudioEngine gEngine;
}

extern "C" {

// int startAudio()
JNIEXPORT jint JNICALL
Java_com_example_accelerometer_Bridge_startAudio(JNIEnv* /*env*/, jobject /*thiz*/) {
    return static_cast<jint>(gEngine.start());
}

// int stopAudio()
JNIEXPORT jint JNICALL
Java_com_example_accelerometer_Bridge_stopAudio(JNIEnv* /*env*/, jobject /*thiz*/) {
    return static_cast<jint>(gEngine.stop());
}

// void setCadenceHz(float)
JNIEXPORT void JNICALL
Java_com_example_accelerometer_Bridge_setCadenceHz(JNIEnv* /*env*/, jobject /*thiz*/, jfloat cadenceHz) {
    gEngine.setCadence(static_cast<float>(cadenceHz));
}

} // extern "C"
