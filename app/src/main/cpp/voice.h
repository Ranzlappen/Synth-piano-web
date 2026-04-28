#pragma once

#include "envelope.h"
#include "filter.h"
#include "oscillator.h"

#include <cstdint>

namespace synthpiano {

class Voice {
public:
    void prepare(float sampleRate);

    void noteOn(int32_t midiNote, float velocity, Waveform wf, const AdsrParams& adsr,
                float glideSec, float velocitySensitivity, float prevHz);
    void noteOff(const AdsrParams& adsr);
    void hardKill();  // immediate cutoff for voice stealing

    // Render N samples into out[], summing (so callers can mix many voices).
    void renderAdd(float* out, int32_t numFrames, const AdsrParams& adsr,
                   FilterParams& filter);

    bool isActive() const { return envelope_.isActive(); }
    int32_t midiNote() const { return midiNote_; }

    // For voice stealing: monotonically increasing tag set at noteOn.
    uint64_t age() const { return age_; }
    void setAge(uint64_t a) { age_ = a; }

    // True if the envelope is in the Release stage (preferred steal target).
    bool isReleasing() const { return envelope_.stage() == Envelope::Stage::Release; }

private:
    Oscillator osc_;
    Envelope envelope_;
    SvfLowpass filter_;
    float velocity_ = 1.0f;
    float velocitySensitivity_ = 1.0f;
    int32_t midiNote_ = -1;
    uint64_t age_ = 0;

    // Glide / portamento (audio-thread only; no atomics).
    float sampleRate_ = 48000.0f;
    float currentFreq_ = 440.0f;
    float targetFreq_ = 440.0f;
    float freqDelta_ = 0.0f;  // per-sample increment toward targetFreq_; 0 when settled
};

} // namespace synthpiano
