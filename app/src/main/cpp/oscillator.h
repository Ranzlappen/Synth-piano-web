#pragma once

#include <cstdint>

namespace synthpiano {

enum class Waveform : int32_t {
    Sine = 0,
    Square = 1,
    Saw = 2,
    Triangle = 3,
};

class Oscillator {
public:
    void reset(float sampleRate, float frequencyHz);
    void setFrequency(float frequencyHz);
    void setWaveform(Waveform w) { waveform_ = w; }

    // Returns one sample in [-1, 1].
    float tick();

private:
    float sampleRate_ = 48000.0f;
    float phase_ = 0.0f;        // 0..1
    float phaseInc_ = 0.0f;     // per-sample phase delta
    Waveform waveform_ = Waveform::Sine;
};

} // namespace synthpiano
