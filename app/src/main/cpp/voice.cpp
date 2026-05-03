#include "voice.h"

#include <algorithm>
#include <cmath>

namespace synthpiano {

namespace {
inline float midiNoteToHz(int32_t note) {
    // 440 * 2^((n - 69)/12)
    return 440.0f * std::pow(2.0f, (static_cast<float>(note) - 69.0f) / 12.0f);
}
} // namespace

void Voice::prepare(float sampleRate) {
    osc_.reset(sampleRate, 440.0f);
    envelope_.setSampleRate(sampleRate);
    filter_.setSampleRate(sampleRate);
    sampleRate_ = sampleRate;
    currentFreq_ = 440.0f;
    targetFreq_ = 440.0f;
    freqDelta_ = 0.0f;
}

void Voice::noteOn(int32_t midiNote, float velocity, Waveform wf, const AdsrParams& adsr,
                   float glideSec, float velocitySensitivity, float prevHz) {
    midiNote_ = midiNote;
    velocity_ = velocity;
    velocitySensitivity_ = velocitySensitivity;
    targetFreq_ = midiNoteToHz(midiNote);
    if (glideSec > 0.0001f && prevHz > 0.0f && prevHz != targetFreq_) {
        currentFreq_ = prevHz;
        const float frames = std::max(glideSec * sampleRate_, 1.0f);
        freqDelta_ = (targetFreq_ - currentFreq_) / frames;
    } else {
        currentFreq_ = targetFreq_;
        freqDelta_ = 0.0f;
    }
    osc_.setWaveform(wf);
    osc_.setFrequency(currentFreq_);
    if (wf == Waveform::Piano) osc_.excite(midiNote);
    envelope_.noteOn(adsr);
    filter_.reset();
}

void Voice::noteOff(const AdsrParams& adsr) {
    envelope_.noteOff(adsr);
}

void Voice::hardKill() {
    midiNote_ = -1;
    envelope_.hardKill();
}

void Voice::renderAdd(float* out, int32_t numFrames, const AdsrParams& adsr,
                      FilterParams& filter, VoiceModParams& mod) {
    if (!envelope_.isActive()) return;

    // Recompute filter coefficients once per audio block. Cheap, no allocation.
    filter_.updateCoeffs(filter.cutoffHz.load(std::memory_order_relaxed),
                         filter.resonance.load(std::memory_order_relaxed));

    // Velocity-sensitivity gain: lerp(1.0, velocity, sensitivity).
    const float gain = 1.0f + velocitySensitivity_ * (velocity_ - 1.0f);

    // Drive: pre-filter tanh saturation. depth=0 is a true bypass (driveAmount==1.0
    // and tanh(x) ≈ x for small x); depth=1 yields a 5x pre-gain into tanh, giving
    // distinctly distorted character. Read once per block to avoid per-sample
    // atomic ops.
    const float drive = mod.drive.load(std::memory_order_relaxed);
    const float driveAmount = 1.0f + drive * 4.0f;
    const bool driveActive = drive > 1e-3f;

    for (int32_t i = 0; i < numFrames; ++i) {
        if (freqDelta_ != 0.0f) {
            currentFreq_ += freqDelta_;
            if ((freqDelta_ > 0.0f && currentFreq_ >= targetFreq_) ||
                (freqDelta_ < 0.0f && currentFreq_ <= targetFreq_)) {
                currentFreq_ = targetFreq_;
                freqDelta_ = 0.0f;
            }
            osc_.setFrequency(currentFreq_);
        }
        const float env = envelope_.tick(adsr);
        float oscSample = osc_.tick();
        if (driveActive) oscSample = std::tanh(oscSample * driveAmount);
        const float filtered = filter_.tick(oscSample);
        out[i] += filtered * env * gain;
    }
}

} // namespace synthpiano
