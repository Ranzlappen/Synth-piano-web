#pragma once

#include <atomic>

namespace synthpiano {

struct FilterParams {
    std::atomic<float> cutoffHz{18000.0f};
    std::atomic<float> resonance{0.0f};
};

// Vadim Zavalishin TPT state-variable filter, low-pass output. Cheap (~6 mul,
// 4 add per sample), unconditionally stable for resonance in [0, 0.99], and
// supports independent cutoff/resonance updates without coefficient
// discontinuities. Coefficients are recomputed once per audio block from
// std::atomic<float> params; no allocation, no locks.
class SvfLowpass {
public:
    void setSampleRate(float sr);
    void reset() { ic1eq_ = 0.0f; ic2eq_ = 0.0f; }
    void updateCoeffs(float cutoffHz, float resonance);
    float tick(float in);

private:
    float sampleRate_ = 48000.0f;
    float a1_ = 0.0f;
    float a2_ = 0.0f;
    float a3_ = 0.0f;
    float ic1eq_ = 0.0f;
    float ic2eq_ = 0.0f;
};

} // namespace synthpiano
