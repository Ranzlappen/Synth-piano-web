#pragma once

#include <array>
#include <cstdint>

namespace synthpiano {

enum class Waveform : int32_t {
    Sine = 0,
    Square = 1,
    Saw = 2,
    Triangle = 3,
    Piano = 4,
};

class Oscillator {
public:
    void reset(float sampleRate, float frequencyHz);
    void setFrequency(float frequencyHz);
    void setWaveform(Waveform w) { waveform_ = w; }

    // Karplus-Strong "pluck": fill the delay line with a noise burst sized
    // to the current frequency. Damping factor is pitch-dependent so high
    // notes decay faster, like a real piano string. Only meaningful when
    // waveform == Piano.
    void excite(int32_t midiNote);

    // Returns one sample in [-1, 1].
    float tick();

private:
    float sampleRate_ = 48000.0f;
    float phase_ = 0.0f;        // 0..1
    float phaseInc_ = 0.0f;     // per-sample phase delta
    Waveform waveform_ = Waveform::Sine;

    // Karplus-Strong state. Sized to hold a delay line for ~28 Hz at 48 kHz
    // (lowest piano A0 ≈ 27.5 Hz needs ~1745 samples).
    static constexpr int32_t kKsBufferSize = 2048;
    std::array<float, kKsBufferSize> ksBuffer_{};
    int32_t ksSize_ = 1;
    int32_t ksReadIdx_ = 0;
    float ksDamp_ = 0.996f;
    uint32_t ksRng_ = 0x12345678u;
};

} // namespace synthpiano
