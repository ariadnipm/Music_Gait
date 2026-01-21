#pragma once
#include "AudioSource.h"

#include <cmath>
#include <cstdint>

namespace mg::audio {

    class SineSource final : public AudioSource {
    public:

        explicit SineSource(float baseHz = 220.0f, float gain = 0.2f)
                : baseHz_(baseHz), gain_(gain) {}

        void prepare(int32_t sampleRate, int32_t channelCount) override {
            sampleRate_ = (sampleRate > 0) ? sampleRate : 48000;
            channelCount_ = (channelCount > 0) ? channelCount : 1;
            phase_ = 0.0f;
        }

        void render(float* out, int32_t numFrames, float cadenceHz) override {
            if (!out || numFrames <= 0) return;


            float freq = baseHz_;
            if (cadenceHz > 0.0001f) {
                freq = baseHz_ + cadenceHz * 150.0f; // 150Hz ανά 1Hz cadence
            }


            if (freq < 20.0f) freq = 20.0f;
            if (freq > 2000.0f) freq = 2000.0f;

            const float twoPi = 6.283185307179586f;
            const float phaseInc = twoPi * freq / static_cast<float>(sampleRate_);


            int idx = 0;
            for (int i = 0; i < numFrames; ++i) {
                const float s = gain_ * std::sinf(phase_);
                phase_ += phaseInc;
                if (phase_ >= twoPi) phase_ -= twoPi;

                // ίδιο sample σε όλα τα κανάλια (εύκολο test)
                for (int c = 0; c < channelCount_; ++c) {
                    out[idx++] = s;
                }
            }
        }

        void reset() override {
            phase_ = 0.0f;
        }

    private:
        int32_t sampleRate_ = 48000;
        int32_t channelCount_ = 1;
        float baseHz_ = 220.0f;
        float gain_ = 0.2f;
        float phase_ = 0.0f;
    };

} // namespace mg::audio
