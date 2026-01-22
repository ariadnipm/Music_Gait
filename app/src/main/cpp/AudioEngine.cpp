#include "AudioEngine.h"

#include <oboe/Oboe.h>
#include <memory>
#include <utility>

#include "AudioCallback.h"
#include <vector>
#include "PianoSequenceSource.h"

// ====== LOGGING ======
#include <android/log.h>

#define MG_TAG "MG_AudioEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  MG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,  MG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, MG_TAG, __VA_ARGS__)

namespace mg::audio {

    static int toInt(oboe::Result r) { return static_cast<int>(r); }
    static const char* stateText(oboe::StreamState s) { return oboe::convertToText(s); }


    static int getXrunCountSafe(oboe::AudioStream* s) {
        if (!s) return -1;
        auto res = s->getXRunCount();
        if (res) return static_cast<int>(res.value());
        return -1;
    }

    AudioEngine::AudioEngine() = default;

    AudioEngine::~AudioEngine() {
        LOGI("~AudioEngine(): stop()");
        stop();
    }

    int AudioEngine::start() {
        LOGI("start(): called (stream_=%s) cadenceHz=%.3f",
             stream_ ? "NON-NULL" : "NULL",
             cadenceHz_.load(std::memory_order_relaxed));

        if (stream_) {
            LOGW("start(): stream already exists. state(before)=%s", stateText(stream_->getState()));
            oboe::Result r = stream_->requestStart();
            LOGI("start(): requestStart(existing) -> %s (%d), state(after)=%s",
                 oboe::convertToText(r), toInt(r), stateText(stream_->getState()));
            return toInt(r);
        }

        auto notes = std::vector<float>{
                220.00f,  // A
                261.63f,  // C
                293.66f,  // D
                329.63f,  // E
                392.00f   // G
        };

        mg::audio::PianoSequenceSource::Params params;


        params.baseGain = 0.40f;

        params.attackSec = 0.004f;
        params.decayFundSec = 0.35f;
        params.decayBrightSec = 0.08f;

        params.hammerNoise = 0.055f;
        params.hammerSec   = 0.015f;
        params.detune       = 0.003f;

        params.h2 = 0.22f;
        params.h3 = 0.10f;
        params.transientBrightness = 0.75f;


        params.defaultCadenceHzWhenZero = 2.0f;
        params.defaultMacroDbWhenZero   = -14.0f;


        params.cadenceMinForMacro = 1.2f;
        params.cadenceMaxForMacro = 3.2f;


        params.macroDbMin = -20.0f;
        params.macroDbMax = -6.0f;


        params.macroSmoothing = 0.04f;

        auto source = std::make_shared<mg::audio::PianoSequenceSource>(notes, params);
        auto cb = std::make_shared<AudioCallback>(source, cadenceHz_);

        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output);
        builder.setFormat(oboe::AudioFormat::Float);
        builder.setChannelCount(oboe::ChannelCount::Mono);
        builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
        builder.setSharingMode(oboe::SharingMode::Exclusive);
        builder.setDataCallback(cb.get());

        LOGI("start(): builder config: dir=Output fmt=Float ch=Mono perf=LowLatency share=Exclusive dataCb=%p",
             cb.get());

        std::shared_ptr<oboe::AudioStream> openedStream;
        oboe::Result r = builder.openStream(openedStream);

        LOGI("start(): openStream -> %s (%d), openedStream=%s",
             oboe::convertToText(r), toInt(r), openedStream ? "OK" : "NULL");

        if (r != oboe::Result::OK || !openedStream) {
            LOGE("start(): FAILED to open stream. result=%s (%d)",
                 oboe::convertToText(r), toInt(r));
            return toInt(r);
        }

        LOGI("start(): opened stream props: sr=%d ch=%d fmt=%s perf=%d share=%d "
             "framesPerBurst=%d bufferCapacity=%d bufferSize=%d xRunCount=%d deviceId=%d",
             openedStream->getSampleRate(),
             openedStream->getChannelCount(),
             oboe::convertToText(openedStream->getFormat()),
             (int)openedStream->getPerformanceMode(),
             (int)openedStream->getSharingMode(),
             openedStream->getFramesPerBurst(),
             openedStream->getBufferCapacityInFrames(),
             openedStream->getBufferSizeInFrames(),
             getXrunCountSafe(openedStream.get()),
             openedStream->getDeviceId());

        const int32_t sr = openedStream->getSampleRate();
        const int32_t ch = openedStream->getChannelCount();
        LOGI("start(): preparing callback with sr=%d ch=%d", sr, ch);
        cb->prepareStream(sr, ch);

        LOGI("start(): state(before start)=%s", stateText(openedStream->getState()));
        r = openedStream->requestStart();
        LOGI("start(): requestStart(new) -> %s (%d), state(after)=%s",
             oboe::convertToText(r), toInt(r), stateText(openedStream->getState()));

        if (r != oboe::Result::OK) {
            LOGE("start(): requestStart FAILED -> %s (%d). Closing stream.",
                 oboe::convertToText(r), toInt(r));
            openedStream->close();
            return toInt(r);
        }

        stream_ = std::move(openedStream);
        callback_ = std::move(cb);

        LOGI("start(): SUCCESS stream_=%p callback_=%p", stream_.get(), callback_.get());
        return toInt(oboe::Result::OK);
    }

    int AudioEngine::stop() {
        LOGI("stop(): called (stream_=%s callback_=%s)",
             stream_ ? "NON-NULL" : "NULL",
             callback_ ? "NON-NULL" : "NULL");

        int result = toInt(oboe::Result::OK);

        if (stream_) {
            LOGI("stop(): state(before stop)=%s xRunCount=%d bufferSize=%d",
                 stateText(stream_->getState()),
                 getXrunCountSafe(stream_.get()),
                 stream_->getBufferSizeInFrames());

            oboe::Result r = stream_->requestStop();
            LOGI("stop(): requestStop -> %s (%d), state(after)=%s",
                 oboe::convertToText(r), toInt(r), stateText(stream_->getState()));
            if (r != oboe::Result::OK) result = toInt(r);

            r = stream_->close();
            LOGI("stop(): close -> %s (%d)", oboe::convertToText(r), toInt(r));
            if (r != oboe::Result::OK) result = toInt(r);

            LOGI("stop(): resetting stream_");
            stream_.reset();
        } else {
            LOGW("stop(): stream_ already NULL");
        }

        if (callback_) {
            LOGI("stop(): resetting callback_");
            callback_.reset();
        } else {
            LOGW("stop(): callback_ already NULL");
        }

        LOGI("stop(): done result=%d", result);
        return result;
    }

    void AudioEngine::setCadenceHz(float cadenceHz) {
        float old = cadenceHz_.load(std::memory_order_relaxed);
        cadenceHz_.store(cadenceHz, std::memory_order_relaxed);
        LOGI("setCadenceHz(): %.3f -> %.3f", old, cadenceHz);
    }

    float AudioEngine::getCadenceHz() const {
        return cadenceHz_.load(std::memory_order_relaxed);
    }

} // namespace mg::audio
