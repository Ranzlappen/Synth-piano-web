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
        envelope_.setAdsr(attackSec, decaySec, sustain, releaseSec);
    }

    void setEnvelopeCurve(float curve) {
        envelope_.setCurve(curve);
    }

    /**
     * Replace the active envelope with a multi-segment shape. Vertices is a
     * flat float buffer laid out as [t0, l0, c0, t1, l1, c1, ...] of length
     * 3*numVertices. Caller-side validation/clamping is duplicated here so
     * malformed Kotlin input can't push the audio thread off a cliff.
     */
    void setEnvelopeShape(const float* vertices, int32_t numVertices, int32_t sustainIndex);

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

    void setDrive(float d) {
        mod_.drive.store(d, std::memory_order_relaxed);
    }

    // Polyphonic compensation amount in [0, 1]. 0 = sum voices linearly
    // (legacy, easy to clip on chords); 1 = scale by 1/sqrt(N) where N is
    // active voice count (constant perceived loudness across chord sizes).
    void setPolyCompensation(float v) {
        polyComp_.store(v, std::memory_order_relaxed);
    }

    // Pre-limiter input gain. 1.0 (default) feeds the master mix straight
    // into the tanh limiter; <1.0 backs off for cleaner peaks; >1.0 drives
    // the limiter for warmth at the cost of soft saturation.
    void setHeadroom(float v) {
        headroom_.store(v, std::memory_order_relaxed);
    }

    // Active-voice cap. Clamped to [1, kMaxVoices]. Voices indexed at or
    // above the cap are never allocated by findFreeVoice.
    void setMaxPolyphony(int32_t n) {
        if (n < 1) n = 1;
        if (n > kMaxVoices) n = kMaxVoices;
        maxActiveVoices_.store(n, std::memory_order_relaxed);
    }

    // Diagnostic: count of NoteEvents dropped because the SPSC ring was
    // full when tryPost ran. Reading does not reset.
    uint32_t eventDropCount() const {
        return eventDropCount_.load(std::memory_order_relaxed);
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

    static constexpr int32_t kEventQueueSize = 1024;       // power of two
    static constexpr int32_t kEventQueueMask = kEventQueueSize - 1;
    std::array<NoteEvent, kEventQueueSize> events_{};
    std::atomic<int32_t> writeIdx_{0};
    std::atomic<int32_t> readIdx_{0};
    std::atomic<uint32_t> eventDropCount_{0};

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
    EnvelopeParams envelope_{};
    FilterParams filter_{};
    VoiceModParams mod_{};
    std::atomic<int32_t> waveform_{static_cast<int32_t>(Waveform::Sine)};
    std::atomic<float> masterAmp_{0.7f};
    std::atomic<float> velocitySensitivity_{1.0f};
    std::atomic<float> glideSec_{0.0f};
    std::atomic<float> polyComp_{1.0f};
    std::atomic<float> headroom_{1.0f};
    std::atomic<int32_t> maxActiveVoices_{kMaxVoices};
    std::atomic<int32_t> sampleRate_{48000};
    std::atomic<float> peak_{0.0f};
    uint64_t voiceTickCount_{0};  // monotonic for voice age
    float lastTargetHz_{440.0f};  // audio-thread only; previous note's freq for glide
    float polyGainSmoothed_{1.0f}; // audio-thread only; one-pole smoother for voice-count gain
};

} // namespace synthpiano
