#pragma once

#include <atomic>
#include <memory>

namespace oboe {
    class AudioStream;
}

namespace mg::audio {

    class AudioCallback;

    class AudioEngine final {
    public:
        AudioEngine();
        ~AudioEngine();

        AudioEngine(const AudioEngine&) = delete;
        AudioEngine& operator=(const AudioEngine&) = delete;

        int start();
        int stop();


        void setCadenceHz(float cadenceHz);
        float getCadenceHz() const;

    private:
        std::shared_ptr<oboe::AudioStream> stream_{};
        std::shared_ptr<AudioCallback> callback_{};

        std::atomic<float> cadenceHz_{0.0f};
    };

} // namespace mg::audio
