#pragma once

#include <atomic>

namespace synthpiano {

struct AdsrParams {
    // All times in seconds; sustain in [0, 1].
    std::atomic<float> attack{0.005f};
    std::atomic<float> decay{0.150f};
    std::atomic<float> sustain{0.700f};
    std::atomic<float> release{0.250f};
    // Shape applied to attack and release output: 0 = linear, +1 = concave/snappy,
    // -1 = convex/soft. Implemented as pow(level, exp2(curve*2)).
    std::atomic<float> curve{0.0f};
};

class Envelope {
public:
    enum class Stage { Idle, Attack, Decay, Sustain, Release };

    void setSampleRate(float sr) { sampleRate_ = sr; }

    void noteOn(const AdsrParams& params);
    void noteOff(const AdsrParams& params);

    // One-sample tick. Reads atomic params lazily on stage transitions.
    float tick(const AdsrParams& params);

    bool isActive() const { return stage_ != Stage::Idle; }
    Stage stage() const { return stage_; }
    float currentLevel() const { return level_; }

private:
    void enterStage(Stage s, const AdsrParams& params);

    float sampleRate_ = 48000.0f;
    Stage stage_ = Stage::Idle;
    float level_ = 0.0f;        // current envelope level [0, 1]
    float increment_ = 0.0f;    // additive delta per sample (linear segments)
    float target_ = 0.0f;       // target level for the current segment
};

} // namespace synthpiano
