#include "AudioEngine.h"

#include <oboe/Oboe.h>
#include <cstring>   // std::memset
#include <memory>
#include <utility>   // std::move

#include "AudioCallback.h"
#include "AudioSource.h"

namespace mg::audio {


    class SilenceSource final : public AudioSource {
    public:
        void prepare(int32_t /*sampleRate*/, int32_t /*channelCount*/) override {

        }

        void render(float* out, int32_t numFrames, float /*cadenceHz*/) override {
            std::memset(out, 0, sizeof(float) * static_cast<size_t>(numFrames));
        }

        void reset() override {

        }
    };


    static int toInt(oboe::Result r) {
        return static_cast<int>(r);
    }

    AudioEngine::AudioEngine() = default;

    AudioEngine::~AudioEngine() {

        stop();
    }

    int AudioEngine::start() {

        if (stream_) {
            return toInt(stream_->requestStart());
        }
        auto source = std::make_shared<SilenceSource>();
        auto cb = std::make_shared<AudioCallback>(source, cadenceHz_);


        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output);
        builder.setFormat(oboe::AudioFormat::Float);
        builder.setChannelCount(oboe::ChannelCount::Mono);
        builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
        builder.setSharingMode(oboe::SharingMode::Exclusive);
        builder.setDataCallback(cb.get());



        std::shared_ptr<oboe::AudioStream> openedStream;
        oboe::Result r = builder.openStream(openedStream);
        if (r != oboe::Result::OK || !openedStream) {
            return toInt(r);
        }
        const int32_t sr = openedStream->getSampleRate();
        const int32_t ch = openedStream->getChannelCount();

        cb->prepareStream(sr, ch);


        r = openedStream->requestStart();
        if (r != oboe::Result::OK) {
            openedStream->close();
            return toInt(r);
        }
        stream_ = std::move(openedStream);
        callback_ = std::move(cb);

        return toInt(oboe::Result::OK);
    }

    int AudioEngine::stop() {
        int result = toInt(oboe::Result::OK);

        if (stream_) {
            oboe::Result r = stream_->requestStop();
            if (r != oboe::Result::OK) {
                result = toInt(r);
            }
            r = stream_->close();
            if (r != oboe::Result::OK) {
                result = toInt(r);
            }
            stream_.reset();
        }
        callback_.reset();

        return result;
    }

    void AudioEngine::setCadence(float cadenceHz) {
        cadenceHz_.store(cadenceHz, std::memory_order_relaxed);
    }

    float AudioEngine::getCadence() const {
        return cadenceHz_.load(std::memory_order_relaxed);
    }

} // namespace mg::audio
