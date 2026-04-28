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
    AdsrParams zero;
    zero.attack.store(0.0f);
    zero.decay.store(0.0f);
    zero.sustain.store(0.0f);
    zero.release.store(0.0f);
    envelope_.noteOff(zero);
    envelope_.tick(zero);
}

void Voice::renderAdd(float* out, int32_t numFrames, const AdsrParams& adsr,
                      FilterParams& filter) {
    if (!envelope_.isActive()) return;

    // Recompute filter coefficients once per audio block. Cheap, no allocation.
    filter_.updateCoeffs(filter.cutoffHz.load(std::memory_order_relaxed),
                         filter.resonance.load(std::memory_order_relaxed));

    // Velocity-sensitivity gain: lerp(1.0, velocity, sensitivity).
    const float gain = 1.0f + velocitySensitivity_ * (velocity_ - 1.0f);

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
        const float oscSample = osc_.tick();
        const float filtered = filter_.tick(oscSample);
        out[i] += filtered * env * gain;
    }
}

} // namespace synthpiano
