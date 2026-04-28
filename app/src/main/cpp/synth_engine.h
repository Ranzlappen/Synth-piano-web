#pragma once

#include "envelope.h"
#include "filter.h"
#include "oscillator.h"
#include "voice.h"

#include <oboe/Oboe.h>

#include <array>
#include <atomic>
#include <cstdint>
#include <memory>

namespace synthpiano {

constexpr int32_t kMaxVoices = 16;

// Lock-free SPSC ring of note events for UI -> audio thread.
struct NoteEvent {
    enum class Kind : int8_t { NoteOn, NoteOff, AllNotesOff };
    Kind kind = Kind::NoteOff;
    int8_t midiNote = 0;
    float velocity = 0.0f;
};

class SynthEngine : public oboe::AudioStreamCallback {
public:
    SynthEngine();
    ~SynthEngine();

    bool start();
    void stop();

    // Producer side (UI / MIDI / score thread). Non-blocking; returns false
    // if the queue is full (extremely unlikely with kEventQueueSize at 256).
    bool postNoteOn(int32_t midiNote, float velocity);
    bool postNoteOff(int32_t midiNote);
    bool postAllNotesOff();

    void setWaveform(Waveform w) { waveform_.store(static_cast<int32_t>(w), std::memory_order_relaxed); }
    void setMasterAmp(float a) { masterAmp_.store(a, std::memory_order_relaxed); }

    void setAdsr(float attackSec, float decaySec, float sustain, float releaseSec) {
        adsr_.attack.store(attackSec, std::memory_order_relaxed);
        adsr_.decay.store(decaySec, std::memory_order_relaxed);
        adsr_.sustain.store(sustain, std::memory_order_relaxed);
        adsr_.release.store(releaseSec, std::memory_order_relaxed);
    }

    void setEnvelopeCurve(float curve) {
        adsr_.curve.store(curve, std::memory_order_relaxed);
    }

    void setFilter(float cutoffHz, float resonance) {
        filter_.cutoffHz.store(cutoffHz, std::memory_order_relaxed);
        filter_.resonance.store(resonance, std::memory_order_relaxed);
    }

    void setVelocitySensitivity(float v) {
        velocitySensitivity_.store(v, std::memory_order_relaxed);
    }

    void setGlideSec(float s) {
        glideSec_.store(s, std::memory_order_relaxed);
    }

    // Polyphonic compensation amount in [0, 1]. 0 = sum voices linearly
    // (legacy, easy to clip on chords); 1 = scale by 1/sqrt(N) where N is
    // active voice count (constant perceived loudness across chord sizes).
    void setPolyCompensation(float v) {
        polyComp_.store(v, std::memory_order_relaxed);
    }

    int32_t sampleRate() const { return sampleRate_.load(std::memory_order_relaxed); }
    float masterPeak() {
        // Atomic exchange so each read returns the peak since last call,
        // letting Kotlin VU meters animate without losing peaks.
        return peak_.exchange(0.0f, std::memory_order_relaxed);
    }

    // Drain master-mix samples into outFloats[], up to maxFrames stereo frames.
    // Returns frames written. Called from a non-audio Kotlin thread that
    // owns a WAV recorder. Lock-free SPSC.
    int32_t drainRecordingFrames(float* outFloats, int32_t maxFrames);

    // Tell the engine whether it should be writing to the recording ring.
    void setRecordingEnabled(bool on) {
        recording_.store(on, std::memory_order_release);
    }

    // Enable/disable the oscilloscope tap. UI flips this on while the
    // SOUND tab is foregrounded so the audio thread isn't writing to a
    // ring nobody reads when it isn't visible.
    void setScopeEnabled(bool on) {
        scopeEnabled_.store(on, std::memory_order_release);
    }

    // Drain mono master-mix samples for the oscilloscope into [out],
    // up to maxFrames samples. Returns frames written. Lock-free SPSC.
    int32_t drainScopeFrames(float* out, int32_t maxFrames);

    // oboe::AudioStreamCallback
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream* stream,
                                          void* audioData,
                                          int32_t numFrames) override;

    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    void drainEvents();
    void applyEvent(const NoteEvent& e);
    Voice* findFreeVoice(int32_t midiNote);

    static constexpr int32_t kEventQueueSize = 256;        // power of two
    static constexpr int32_t kEventQueueMask = kEventQueueSize - 1;
    std::array<NoteEvent, kEventQueueSize> events_{};
    std::atomic<int32_t> writeIdx_{0};
    std::atomic<int32_t> readIdx_{0};

    static constexpr int32_t kRecordingRingFrames = 1 << 15; // 32k stereo frames (~680ms @ 48k)
    static constexpr int32_t kRecordingRingMask = kRecordingRingFrames - 1;
    std::array<float, kRecordingRingFrames * 2> recordingRing_{};
    std::atomic<int32_t> recWriteIdx_{0};
    std::atomic<int32_t> recReadIdx_{0};
    std::atomic<bool> recording_{false};

    // Oscilloscope SPSC ring. Mono master-mix snapshot, sized to ~85ms
    // at 48kHz so the display can hold a stable waveform window. Populated
    // by the audio thread when scopeEnabled_ is true.
    static constexpr int32_t kScopeRingFrames = 1 << 12; // 4096 mono frames
    static constexpr int32_t kScopeRingMask = kScopeRingFrames - 1;
    std::array<float, kScopeRingFrames> scopeRing_{};
    std::atomic<int32_t> scopeWriteIdx_{0};
    std::atomic<int32_t> scopeReadIdx_{0};
    std::atomic<bool> scopeEnabled_{false};

    bool tryPost(const NoteEvent& e);

    std::shared_ptr<oboe::AudioStream> stream_;
    std::array<Voice, kMaxVoices> voices_{};
    AdsrParams adsr_{};
    FilterParams filter_{};
    std::atomic<int32_t> waveform_{static_cast<int32_t>(Waveform::Sine)};
    std::atomic<float> masterAmp_{0.7f};
    std::atomic<float> velocitySensitivity_{1.0f};
    std::atomic<float> glideSec_{0.0f};
    std::atomic<float> polyComp_{1.0f};
    std::atomic<int32_t> sampleRate_{48000};
    std::atomic<float> peak_{0.0f};
    uint64_t voiceTickCount_{0};  // monotonic for voice age
    float lastTargetHz_{440.0f};  // audio-thread only; previous note's freq for glide
    float polyGainSmoothed_{1.0f}; // audio-thread only; one-pole smoother for voice-count gain
};

} // namespace synthpiano
