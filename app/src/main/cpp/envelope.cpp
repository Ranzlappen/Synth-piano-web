#include "envelope.h"

#include <cmath>

namespace synthpiano {

namespace {
// Floor on stage durations to avoid div-by-zero when a slider hits 0.
constexpr float kMinSeconds = 0.001f;

float secondsToInc(float seconds, float delta, float sampleRate) {
    const float s = (seconds < kMinSeconds) ? kMinSeconds : seconds;
    return delta / (s * sampleRate);
}

inline float shapeLevel(float level, float curve) {
    if (curve > -1e-3f && curve < 1e-3f) return level;
    if (level <= 0.0f) return 0.0f;
    if (level >= 1.0f) return 1.0f;
    return std::pow(level, std::exp2(curve * 2.0f));
}
} // namespace

void Envelope::noteOn(const AdsrParams& params) {
    // Restart from current level (legato-friendly): if a voice is being
    // re-triggered, we glide up from where we are rather than zero-clicking.
    enterStage(Stage::Attack, params);
}

void Envelope::noteOff(const AdsrParams& params) {
    enterStage(Stage::Release, params);
}

void Envelope::enterStage(Stage s, const AdsrParams& params) {
    stage_ = s;
    switch (s) {
        case Stage::Idle:
            level_ = 0.0f;
            increment_ = 0.0f;
            target_ = 0.0f;
            break;
        case Stage::Attack:
            target_ = 1.0f;
            increment_ = secondsToInc(params.attack.load(std::memory_order_relaxed),
                                      target_ - level_, sampleRate_);
            break;
        case Stage::Decay: {
            const float sustain = params.sustain.load(std::memory_order_relaxed);
            target_ = sustain;
            increment_ = secondsToInc(params.decay.load(std::memory_order_relaxed),
                                      target_ - level_, sampleRate_);
            break;
        }
        case Stage::Sustain:
            target_ = params.sustain.load(std::memory_order_relaxed);
            increment_ = 0.0f;
            level_ = target_;
            break;
        case Stage::Release:
            target_ = 0.0f;
            increment_ = secondsToInc(params.release.load(std::memory_order_relaxed),
                                      target_ - level_, sampleRate_);
            break;
    }
}

float Envelope::tick(const AdsrParams& params) {
    switch (stage_) {
        case Stage::Idle:
            return 0.0f;
        case Stage::Attack:
            level_ += increment_;
            if (level_ >= target_) {
                level_ = target_;
                enterStage(Stage::Decay, params);
            }
            return shapeLevel(level_, params.curve.load(std::memory_order_relaxed));
        case Stage::Decay:
            level_ += increment_;  // negative or zero
            if ((increment_ <= 0.0f && level_ <= target_) ||
                (increment_ > 0.0f && level_ >= target_)) {
                level_ = target_;
                enterStage(Stage::Sustain, params);
            }
            return level_;
        case Stage::Sustain:
            // Track live sustain changes.
            level_ = params.sustain.load(std::memory_order_relaxed);
            return level_;
        case Stage::Release:
            level_ += increment_;
            if (level_ <= 0.0f) {
                level_ = 0.0f;
                enterStage(Stage::Idle, params);
                return 0.0f;
            }
            return shapeLevel(level_, params.curve.load(std::memory_order_relaxed));
    }
    return level_;
}

} // namespace synthpiano
