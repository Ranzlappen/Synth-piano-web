#include "filter.h"

#include <cmath>

namespace synthpiano {

namespace {
constexpr float kPi = 3.14159265358979323846f;
}

void SvfLowpass::setSampleRate(float sr) {
    sampleRate_ = sr;
    reset();
}

void SvfLowpass::updateCoeffs(float cutoffHz, float resonance) {
    const float maxCutoff = sampleRate_ * 0.45f;
    float fc = cutoffHz;
    if (fc < 20.0f) fc = 20.0f;
    if (fc > maxCutoff) fc = maxCutoff;

    float r = resonance;
    if (r < 0.0f) r = 0.0f;
    if (r > 0.99f) r = 0.99f;
    // Damping factor: k=2 means no resonance, k near 0 means strong resonance.
    const float k = 2.0f - 1.95f * r;

    const float g = std::tan(kPi * fc / sampleRate_);
    a1_ = 1.0f / (1.0f + g * (g + k));
    a2_ = g * a1_;
    a3_ = g * a2_;
}

float SvfLowpass::tick(float in) {
    const float v3 = in - ic2eq_;
    const float v1 = a1_ * ic1eq_ + a2_ * v3;
    const float v2 = ic2eq_ + a2_ * ic1eq_ + a3_ * v3;
    ic1eq_ = 2.0f * v1 - ic1eq_;
    ic2eq_ = 2.0f * v2 - ic2eq_;
    return v2;
}

} // namespace synthpiano
