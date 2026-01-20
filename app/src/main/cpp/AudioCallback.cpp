#include "AudioCallback.h"
#include "AudioSource.h"

#include <cstring>
#include <utility>

namespace mg::audio {

    AudioCallback::AudioCallback(std::shared_ptr<AudioSource> source,
                                 std::atomic<float>& cadenceHzRef)
            : source_(std::move(source)),
              cadenceHz_(cadenceHzRef) {
    }

    void AudioCallback::prepareStream(int32_t sampleRate, int32_t channelCount) {
        sampleRate_ = sampleRate;
        channelCount_ = channelCount;

        if (source_) {
            source_->prepare(sampleRate_, channelCount_);
        }
    }

    oboe::DataCallbackResult AudioCallback::onAudioReady(oboe::AudioStream* /*audioStream*/,
                                                         void* audioData,
                                                         int32_t numFrames) {

        if (audioData == nullptr || numFrames <= 0 || channelCount_ <= 0 || !source_) {
            return oboe::DataCallbackResult::Continue;
        }


        auto* out = static_cast<float*>(audioData);


        const float cadenceHz = cadenceHz_.load(std::memory_order_relaxed);


        source_->render(out, numFrames, cadenceHz);

        return oboe::DataCallbackResult::Continue;
    }

} // namespace mg::audio
