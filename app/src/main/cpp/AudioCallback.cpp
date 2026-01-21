#include "AudioCallback.h"
#include "AudioSource.h"

#include <utility> // std::move
#include <android/log.h>
#include <atomic>

#define MG_CB_TAG "MG_AudioCallback"
#define CBI(...) __android_log_print(ANDROID_LOG_INFO,  MG_CB_TAG, __VA_ARGS__)
#define CBW(...) __android_log_print(ANDROID_LOG_WARN,  MG_CB_TAG, __VA_ARGS__)
#define CBE(...) __android_log_print(ANDROID_LOG_ERROR, MG_CB_TAG, __VA_ARGS__)

namespace mg::audio {

    AudioCallback::AudioCallback(std::shared_ptr<AudioSource> source,
                                 std::atomic<float>& cadenceHzRef)
            : source_(std::move(source)),
              cadenceHz_(cadenceHzRef) {
        CBI("ctor: source=%p cadenceRef=%p", source_.get(), &cadenceHz_);
    }

    void AudioCallback::prepareStream(int32_t sampleRate, int32_t channelCount) {
        sampleRate_ = sampleRate;
        channelCount_ = channelCount;

        CBI("prepareStream: sr=%d ch=%d source=%p", sampleRate_, channelCount_, source_.get());

        if (source_) source_->prepare(sampleRate_, channelCount_);
        else CBW("prepareStream: source is NULL");
    }

    oboe::DataCallbackResult AudioCallback::onAudioReady(oboe::AudioStream* audioStream,
                                                         void* audioData,
                                                         int32_t numFrames) {
        if (!audioData || numFrames <= 0 || channelCount_ <= 0 || !source_) {
            return oboe::DataCallbackResult::Continue;
        }

        const float cadenceHz = cadenceHz_.load(std::memory_order_relaxed);

        static std::atomic<int> cbCount{0};
        int n = ++cbCount;
        if ((n % 200) == 0) {


            int xrun = -1;
            if (audioStream) {
                auto xrunRes = audioStream->getXRunCount();
                if (xrunRes) {
                    xrun = xrunRes.value();
                }
            }

            CBI("onAudioReady: #%d frames=%d cadence=%.3f xrun=%d sr=%d ch=%d",
                n, numFrames, cadenceHz, xrun, sampleRate_, channelCount_);
        }

        auto* out = static_cast<float*>(audioData);
        source_->render(out, numFrames, cadenceHz);

        return oboe::DataCallbackResult::Continue;
    }

} // namespace mg::audio
