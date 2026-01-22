#pragma once

#include <atomic>
#include <cstdint>
#include <memory>

#include <oboe/Oboe.h>

namespace mg::audio {

    class AudioSource;
/*This class repeatedly pushes samples to the buffer and inherits the oboe
  AudioStreamDataCallback .*/
    class AudioCallback final : public oboe::AudioStreamDataCallback {
    public:
        AudioCallback(std::shared_ptr<AudioSource> source,
                      std::atomic<float>& cadenceHzRef);//constructor

        void prepareStream(int32_t sampleRate, int32_t channelCount);
        //This is being called by the audio Driver
        /*Contains  a pointer to the current stream,
          a container array to write audio data into and
          how many frames of audio are required*/
        oboe::DataCallbackResult onAudioReady(oboe::AudioStream* audioStream,
                                              void* audioData,
                                              int32_t numFrames) override;

    private:
        std::shared_ptr<AudioSource> source_;
        std::atomic<float>& cadenceHz_;

        int32_t sampleRate_ = 0;
        int32_t channelCount_ = 0;
    };

} // namespace mg::audio
