#include "voice.h"

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
}

void Voice::noteOn(int32_t midiNote, float velocity, Waveform wf, const AdsrParams& adsr) {
    midiNote_ = midiNote;
    velocity_ = velocity;
    osc_.setWaveform(wf);
    osc_.setFrequency(midiNoteToHz(midiNote));
    // Piano needs a fresh noise burst per note-on; the user's ADSR still
    // gates amplitude on top of the natural Karplus-Strong decay tail.
    if (wf == Waveform::Piano) osc_.excite(midiNote);
    envelope_.noteOn(adsr);
}

void Voice::noteOff(const AdsrParams& adsr) {
    envelope_.noteOff(adsr);
}

void Voice::hardKill() {
    midiNote_ = -1;
    // Drop the envelope to idle without emitting a release tail.
    AdsrParams zero;
    zero.attack.store(0.0f);
    zero.decay.store(0.0f);
    zero.sustain.store(0.0f);
    zero.release.store(0.0f);
    envelope_.noteOff(zero);
    // One tick to push it past zero.
    envelope_.tick(zero);
}

void Voice::renderAdd(float* out, int32_t numFrames, const AdsrParams& adsr) {
    if (!envelope_.isActive()) return;
    for (int32_t i = 0; i < numFrames; ++i) {
        const float env = envelope_.tick(adsr);
        const float s = osc_.tick() * env * velocity_;
        out[i] += s;
    }
}

} // namespace synthpiano
