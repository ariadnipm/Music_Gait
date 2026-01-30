#pragma once

#include "AudioSource.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <vector>

namespace mg::audio {
 /*This class produces the sound  to be played on the app */
    class PianoSequenceSource final : public AudioSource {
    public:
        struct Params {
            float attackSec;
            float decayFundSec;
            float decayBrightSec;
            float baseGain;

            // Macro intensity mapping from cadence
            float cadenceMinForMacro;
            float cadenceMaxForMacro;
            float macroDbMin;
            float macroDbMax;

            // Smoothing for macro gain
            float macroSmoothing;
            float defaultCadenceHzWhenZero; // tempo fallback
            float defaultMacroDbWhenZero;   // loudness fallback in dB
            bool loopForward;

            float h2;
            float h3;
            float transientBrightness;

            float hammerNoise;
            float hammerSec;
            float detune;

            Params()
                    : attackSec(0.004f),
                      decayFundSec(0.35f),
                      decayBrightSec(0.08f),
                      baseGain(0.40f),

                      cadenceMinForMacro(1.2f),
                      cadenceMaxForMacro(3.2f),

                      macroDbMin(-18.0f),
                      macroDbMax(-6.0f),
                      macroSmoothing(0.03f),

                      defaultCadenceHzWhenZero(1.7f),
                      defaultMacroDbWhenZero(-16.0f),

                      loopForward(true),

                      h2(0.22f),
                      h3(0.10f),
                      transientBrightness(0.75f),

                      hammerNoise(0.035f),
                      hammerSec(0.008f),
                      detune(0.003f) {}
        };

        explicit PianoSequenceSource(std::vector<float> notesHz,
                                     Params params = Params{})
                : notesHz_(std::move(notesHz)), p_(params) {
            sanitizeNotes_();
            reset();
        }

        void setNotes(std::vector<float> notesHz) {
            notesHz_ = std::move(notesHz);
            sanitizeNotes_();

            if (notesHz_.empty()) {
                noteIndex_ = 0;
                currentFreqHz_ = 0.0f;
            } else {
                noteIndex_ %= static_cast<int>(notesHz_.size());
                currentFreqHz_ = notesHz_[noteIndex_];
            }
        }

        void prepare(int32_t sampleRate, int32_t channelCount) override {
            sampleRate_ = (sampleRate > 0) ? sampleRate : 48000;
            channelCount_ = (channelCount > 0) ? channelCount : 1;

            attackSamples_ = std::max(1, static_cast<int>(p_.attackSec * static_cast<float>(sampleRate_)));
            decayFundSamples_ = std::max(1, static_cast<int>(p_.decayFundSec * static_cast<float>(sampleRate_)));
            decayBrightSamples_ = std::max(1, static_cast<int>(p_.decayBrightSec * static_cast<float>(sampleRate_)));
            hammerSamples_ = std::max(1, static_cast<int>(p_.hammerSec * static_cast<float>(sampleRate_)));

            phase_ = 0.0f;
            phase2_ = 0.0f;
            beatPhase_ = 0.0f;

            envPos_ = attackSamples_ + decayFundSamples_;
        }

        void reset() override {
            phase_ = 0.0f;
            phase2_ = 0.0f;
            beatPhase_ = 0.95f;

            macroGain_ = dbToAmp_(p_.defaultMacroDbWhenZero);

            noteIndex_ = -1;
            currentFreqHz_ = notesHz_.empty() ? 0.0f : notesHz_[0];

            envPos_ = 999999;
            rng_ = 0x12345678u;
        }


        void render(float* out, int32_t numFrames, float cadenceHz) override {
            if (!out || numFrames <= 0 || channelCount_ <= 0 || sampleRate_ <= 0) return;

            if (notesHz_.empty()) {
                std::fill(out, out + (static_cast<size_t>(numFrames) * channelCount_), 0.0f);
                return;
            }

            const float targetCad = std::max(0.0f, cadenceHz);
            float cadForTiming = 0.0f;
            float macroTargetAmp = 0.0f;

            if (targetCad < 0.0001f) {
                cadForTiming = std::max(0.1f, p_.defaultCadenceHzWhenZero);
                macroTargetAmp = dbToAmp_(p_.defaultMacroDbWhenZero);
            } else {
                cadForTiming = targetCad;
                macroTargetAmp = cadenceToMacroAmp_(targetCad);
            }

            // Smoothing only on macro gain for a more natural effect
            macroGain_ += p_.macroSmoothing * (macroTargetAmp - macroGain_);

            const float twoPi = 6.283185307179586f;
            const float srInv = 1.0f / static_cast<float>(sampleRate_);

            int idx = 0;
            for (int i = 0; i < numFrames; ++i) {

                beatPhase_ += cadForTiming * srInv;
                if (beatPhase_ >= 1.0f) {
                    beatPhase_ -= 1.0f;
                    triggerNextNote_();
                }

                const float envFund = envelopeWithDecay_(envPos_, attackSamples_, decayFundSamples_);
                const float envBright = envelopeWithDecay_(envPos_, attackSamples_, decayBrightSamples_);
                envPos_++;

                float s = 0.0f;
                const float f = currentFreqHz_;

                if (f > 0.0f) {
                    const float phaseInc1 = twoPi * f * srInv;
                    const float phaseInc2 = twoPi * (f * (1.0f + p_.detune)) * srInv;

                    const float x1a = std::sinf(phase_);
                    const float x1b = std::sinf(phase2_);

                    const float x2 = std::sinf(2.0f * phase_);
                    const float x3 = std::sinf(3.0f * phase_);

                    const float bright = (p_.transientBrightness * envBright) +
                                         (1.0f - p_.transientBrightness) * (envBright * envBright);

                    float body = 0.70f * (0.6f * x1a + 0.4f * x1b) * envFund;
                    float brill = ((p_.h2 * bright) * x2 + (p_.h3 * bright) * x3) * envBright;

                    float hammer = 0.0f;
                    if (envPos_ < hammerSamples_) {
                        float k = 1.0f - (static_cast<float>(envPos_) / static_cast<float>(hammerSamples_));
                        hammer = p_.hammerNoise * (k * k) * whiteNoise_();
                    }

                    s = body + brill + hammer;

                    phase_ += phaseInc1;
                    if (phase_ >= twoPi) phase_ -= twoPi;

                    phase2_ += phaseInc2;
                    if (phase2_ >= twoPi) phase2_ -= twoPi;

                    s = softClip_(s);
                }

                float sample = p_.baseGain * macroGain_ * envFund * s;

                for (int c = 0; c < channelCount_; ++c) {
                    out[idx++] = sample;
                }
            }
        }

    private:
        std::vector<float> notesHz_;
        Params p_;

        int noteIndex_ = 0;
        float currentFreqHz_ = 0.0f;

        int32_t sampleRate_ = 48000;
        int32_t channelCount_ = 1;

        float phase_ = 0.0f;
        float phase2_ = 0.0f;
        float beatPhase_ = 0.0f;

        int attackSamples_ = 1;
        int decayFundSamples_ = 1;
        int decayBrightSamples_ = 1;
        int hammerSamples_ = 1;

        int envPos_ = 999999;

        // Only macro gain is smoothed now
        float macroGain_ = 0.5f;

        uint32_t rng_ = 0x12345678u;

    private:
        void sanitizeNotes_() {
            notesHz_.erase(std::remove_if(notesHz_.begin(), notesHz_.end(),
                                          [](float f) { return !(f > 0.0f); }),
                           notesHz_.end());
            for (auto& f : notesHz_) {
                f = std::clamp(f, 40.0f, 2000.0f);
            }
            if (!notesHz_.empty()) currentFreqHz_ = notesHz_[0];
        }

        void triggerNextNote_() {
            envPos_ = 0;

            if (notesHz_.empty()) return;

            if (p_.loopForward) {
                noteIndex_ = (noteIndex_ + 1) % static_cast<int>(notesHz_.size());
            } else {
                noteIndex_ = (noteIndex_ + 1) % static_cast<int>(notesHz_.size());
            }

            currentFreqHz_ = notesHz_[noteIndex_];

            phase_ = 0.0f;
            phase2_ = 0.0f;
        }

        static float envelopeWithDecay_(int pos, int attackS, int decayS) {
            if (pos < 0) return 0.0f;
            const int total = attackS + decayS;
            if (pos >= total) return 0.0f;

            if (pos < attackS) {
                return static_cast<float>(pos) / static_cast<float>(attackS);
            } else {
                const int d = pos - attackS;
                float x = static_cast<float>(d) / static_cast<float>(decayS);
                x = std::clamp(x, 0.0f, 1.0f);
                float y = 1.0f - x;
                return y * y;
            }
        }

        float whiteNoise_() {
            uint32_t x = rng_;
            x ^= x << 13;
            x ^= x >> 17;
            x ^= x << 5;
            rng_ = x;
            return (static_cast<float>(x) / 2147483648.0f) - 1.0f;
        }

        static float softClip_(float x) {
            const float ax = std::fabs(x);
            return x / (1.0f + ax);
        }

        static float dbToAmp_(float db) {
            return std::pow(10.0f, db / 20.0f);
        }

        float cadenceToMacroAmp_(float cadHz) const {
            const float c0 = p_.cadenceMinForMacro;
            const float c1 = p_.cadenceMaxForMacro;

            float u = 0.0f;
            if (c1 > c0) u = (cadHz - c0) / (c1 - c0);
            u = std::clamp(u, 0.0f, 1.0f);

            const float db = p_.macroDbMin + u * (p_.macroDbMax - p_.macroDbMin);
            return dbToAmp_(db);
        }
    };

} // namespace mg::audio
