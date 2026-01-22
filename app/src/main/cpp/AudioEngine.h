#pragma once

#include <atomic>
#include <memory>

namespace oboe {
    class AudioStream;
}

namespace mg::audio {

    class AudioCallback;
/*This class creates the audio stream.The manager
of the stream is the AudioEngine */
    class AudioEngine final {
    public:
        AudioEngine(); //constructor
        ~AudioEngine(); //destructor

       //Copies are not allowed
        AudioEngine(const AudioEngine&) = delete;
        AudioEngine& operator=(const AudioEngine&) = delete;

        int start(); /*implements the stream builder properties*/
        int stop();

       //Cadence API
        void setCadenceHz(float cadenceHz);
        float getCadenceHz() const;

    private:
        //Basically the sound pipe
        std::shared_ptr<oboe::AudioStream> stream_{};
        std::shared_ptr<AudioCallback> callback_{};
        //Ensure the cadence is updated atomically and safely
        std::atomic<float> cadenceHz_{0.0f};
    };

} // namespace mg::audio
