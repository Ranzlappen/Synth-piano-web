#include "oscillator.h"

#include <cmath>

namespace synthpiano {

namespace {
constexpr float kTwoPi = 6.28318530717958647692f;
}

void Oscillator::reset(float sampleRate, float frequencyHz) {
    sampleRate_ = sampleRate;
    phase_ = 0.0f;
    setFrequency(frequencyHz);
}

void Oscillator::setFrequency(float frequencyHz) {
    phaseInc_ = (sampleRate_ > 0.0f) ? frequencyHz / sampleRate_ : 0.0f;
}

float Oscillator::tick() {
    const float p = phase_;
    phase_ += phaseInc_;
    if (phase_ >= 1.0f) phase_ -= 1.0f;

    switch (waveform_) {
        case Waveform::Sine:
            return std::sin(p * kTwoPi);
        case Waveform::Square:
            return (p < 0.5f) ? 1.0f : -1.0f;
        case Waveform::Saw:
            return 2.0f * p - 1.0f;
        case Waveform::Triangle: {
            // Triangle: |2x-1| mapped to [-1, 1]
            const float t = 2.0f * p - 1.0f;
            return 2.0f * std::fabs(t) - 1.0f;
        }
    }
    return 0.0f;
}

} // namespace synthpiano
