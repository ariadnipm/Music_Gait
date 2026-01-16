
#include <jni.h>
#include <oboe/Oboe.h>

extern "C"
JNIEXPORT jint JNICALL
Java_com_example_accelerometer_Bridge_nativeOboeTest(
        JNIEnv*,
        jobject
) {
    // ΜΟΝΟ για sanity check
    oboe::AudioStreamBuilder builder;
    return 0;
}
