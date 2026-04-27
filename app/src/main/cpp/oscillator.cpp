#include "oscillator.h"

#include <algorithm>
#include <cmath>

namespace synthpiano {

namespace {
constexpr float kTwoPi = 6.28318530717958647692f;
}

void Oscillator::reset(float sampleRate, float frequencyHz) {
    sampleRate_ = sampleRate;
    phase_ = 0.0f;
    ksBuffer_.fill(0.0f);
    ksSize_ = 1;
    ksReadIdx_ = 0;
    setFrequency(frequencyHz);
}

void Oscillator::setFrequency(float frequencyHz) {
    phaseInc_ = (sampleRate_ > 0.0f) ? frequencyHz / sampleRate_ : 0.0f;
    if (frequencyHz > 0.0f && sampleRate_ > 0.0f) {
        const int32_t n = static_cast<int32_t>(std::lround(sampleRate_ / frequencyHz));
        ksSize_ = std::clamp(n, 8, kKsBufferSize);
    }
}

void Oscillator::excite(int32_t midiNote) {
    // Pitch-dependent damping: higher notes lose energy faster, matching
    // real piano strings where short strings decay quickly. The 0.5 averaging
    // in tick() is the lowpass; ksDamp_ scales it for net loss per round-trip.
    const float pitchOffset = static_cast<float>(midiNote - 60) / 12.0f;
    ksDamp_ = std::clamp(0.998f - 0.0025f * pitchOffset, 0.985f, 0.9995f);

    // Fill exactly ksSize_ taps with white noise in [-0.8, 0.8] so summed
    // voices × ADSR × master amp don't slam the soft clipper. Deterministic
    // LCG keeps the audio thread allocation-free.
    for (int32_t i = 0; i < ksSize_; ++i) {
        ksRng_ = ksRng_ * 1664525u + 1013904223u;
        const float u = (static_cast<int32_t>(ksRng_ >> 8) & 0xFFFF) / 32768.0f - 1.0f;
        ksBuffer_[i] = u * 0.8f;
    }
    // Zero unused tail so it doesn't pollute future re-pitches.
    for (int32_t i = ksSize_; i < kKsBufferSize; ++i) ksBuffer_[i] = 0.0f;
    ksReadIdx_ = 0;
}

float Oscillator::tick() {
    if (waveform_ == Waveform::Piano) {
        const int32_t i = ksReadIdx_;
        const int32_t j = (i + 1 < ksSize_) ? (i + 1) : 0;
        const float s = ksBuffer_[i];
        ksBuffer_[i] = ksDamp_ * 0.5f * (s + ksBuffer_[j]);
        ksReadIdx_ = j;
        return s;
    }

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
        case Waveform::Piano:
            return 0.0f;  // handled above
    }
    return 0.0f;
}

} // namespace synthpiano
