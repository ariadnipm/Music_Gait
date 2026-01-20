
#pragma once

#include <atomic>
#include <memory>

namespace oboe {
    class AudioStream;
    class AudioStreamCallback;
} // these classes exist on oboe but i do not specify yet

namespace mg::audio {
    class AudioCallback;

    class AudioEngine final { // main class for handling the audiostream
    public:
        AudioEngine();
        ~AudioEngine();


        AudioEngine(const AudioEngine&) = delete;
        AudioEngine& operator=(const AudioEngine&) = delete; // no duplicates are allowed
        int start();
        int stop();
        void setCadence(float cadence);
        float getCadence() const;

    private:

        std::shared_ptr<oboe::AudioStream> stream_{};
        std::shared_ptr<AudioCallback> callback_{};
        std::atomic<float> cadenceHz_{0.0f};
    };

}
