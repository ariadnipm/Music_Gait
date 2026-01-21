#pragma once

#include <cstdint>

namespace mg::audio {

    class AudioSource {
    public:
        virtual ~AudioSource() = default;

        virtual void prepare(int32_t sampleRate, int32_t channelCount) = 0;
        virtual void render(float* out, int32_t numFrames, float cadenceHz) = 0;

        // optional
        virtual void reset() {}
    };

} // namespace mg::audio
