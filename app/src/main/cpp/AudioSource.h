#pragma once

#include <cstdint>

namespace mg::audio {
/*Abstract class that sets the properties of any sound */
    class AudioSource {
    public:
        virtual ~AudioSource() = default; //destructor

        virtual void prepare(int32_t sampleRate, int32_t channelCount) = 0;

        /*The heart of the sound.Fills the buffer with samples and
         adjusts to the walking cadence */
        virtual void render(float* out, int32_t numFrames, float cadenceHz) = 0;

        virtual void reset() {}

    };

} // namespace mg::audio
