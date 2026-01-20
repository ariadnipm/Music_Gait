#pragma once

#include <atomic>
#include <cstdint>
#include <memory>

#include <oboe/Oboe.h>

namespace mg::audio {

    class AudioSource;

    class AudioCallback final : public oboe::AudioStreamDataCallback {
    public:
        AudioCallback(std::shared_ptr<AudioSource> source,
                      std::atomic<float>& cadenceHzRef);


        void prepareStream(int32_t sampleRate, int32_t channelCount);

        oboe::DataCallbackResult onAudioReady(oboe::AudioStream* audioStream,
                                              void* audioData,
                                              int32_t numFrames) override;

    private:
        std::shared_ptr<AudioSource> source_;
        std::atomic<float>& cadenceHz_;

        int32_t sampleRate_ = 0;
        int32_t channelCount_ = 0;
    };

}
