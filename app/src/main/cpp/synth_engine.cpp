#include "synth_engine.h"

#include <android/log.h>

#include <algorithm>
#include <cmath>
#include <cstring>

#define LOG_TAG "SynthEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace synthpiano {

SynthEngine::SynthEngine() = default;

SynthEngine::~SynthEngine() {
    stop();
}

bool SynthEngine::start() {
    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output)
        ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
        ->setSharingMode(oboe::SharingMode::Exclusive)
        ->setFormat(oboe::AudioFormat::Float)
        ->setChannelCount(oboe::ChannelCount::Stereo)
        ->setSampleRate(48000)
        ->setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium)
        ->setCallback(this);

    oboe::Result result = builder.openStream(stream_);
    if (result != oboe::Result::OK) {
        LOGE("openStream failed: %s", oboe::convertToText(result));
        return false;
    }

    sampleRate_.store(stream_->getSampleRate(), std::memory_order_release);
    LOGI("Stream opened: rate=%d framesPerBurst=%d perf=%d",
         stream_->getSampleRate(),
         stream_->getFramesPerBurst(),
         static_cast<int>(stream_->getPerformanceMode()));

    for (auto& v : voices_) {
        v.prepare(static_cast<float>(stream_->getSampleRate()));
    }

    result = stream_->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("requestStart failed: %s", oboe::convertToText(result));
        stream_->close();
        stream_.reset();
        return false;
    }
    return true;
}

void SynthEngine::stop() {
    if (!stream_) return;
    stream_->requestStop();
    stream_->close();
    stream_.reset();
}

bool SynthEngine::tryPost(const NoteEvent& e) {
    const int32_t w = writeIdx_.load(std::memory_order_relaxed);
    const int32_t r = readIdx_.load(std::memory_order_acquire);
    if (((w + 1) & kEventQueueMask) == (r & kEventQueueMask)) {
        return false;  // full
    }
    events_[w & kEventQueueMask] = e;
    writeIdx_.store(w + 1, std::memory_order_release);
    return true;
}

bool SynthEngine::postNoteOn(int32_t midiNote, float velocity) {
    NoteEvent e{};
    e.kind = NoteEvent::Kind::NoteOn;
    e.midiNote = static_cast<int8_t>(midiNote);
    e.velocity = velocity;
    return tryPost(e);
}

bool SynthEngine::postNoteOff(int32_t midiNote) {
    NoteEvent e{};
    e.kind = NoteEvent::Kind::NoteOff;
    e.midiNote = static_cast<int8_t>(midiNote);
    return tryPost(e);
}

bool SynthEngine::postAllNotesOff() {
    NoteEvent e{};
    e.kind = NoteEvent::Kind::AllNotesOff;
    return tryPost(e);
}

void SynthEngine::drainEvents() {
    const int32_t r = readIdx_.load(std::memory_order_relaxed);
    const int32_t w = writeIdx_.load(std::memory_order_acquire);
    int32_t i = r;
    while (i != w) {
        applyEvent(events_[i & kEventQueueMask]);
        ++i;
    }
    readIdx_.store(i, std::memory_order_release);
}

Voice* SynthEngine::findFreeVoice(int32_t midiNote) {
    // 1. Idle voice.
    for (auto& v : voices_) {
        if (!v.isActive()) return &v;
    }
    // 2. Same-note retrigger (avoid stacking duplicate-pitch voices).
    for (auto& v : voices_) {
        if (v.midiNote() == midiNote) return &v;
    }
    // 3. Releasing voice with the lowest age.
    Voice* best = nullptr;
    uint64_t bestAge = UINT64_MAX;
    for (auto& v : voices_) {
        if (v.isReleasing() && v.age() < bestAge) {
            bestAge = v.age();
            best = &v;
        }
    }
    if (best) return best;
    // 4. Steal the oldest voice outright.
    bestAge = UINT64_MAX;
    best = &voices_[0];
    for (auto& v : voices_) {
        if (v.age() < bestAge) {
            bestAge = v.age();
            best = &v;
        }
    }
    return best;
}

void SynthEngine::applyEvent(const NoteEvent& e) {
    switch (e.kind) {
        case NoteEvent::Kind::NoteOn: {
            Voice* v = findFreeVoice(e.midiNote);
            if (!v) return;
            const auto wf = static_cast<Waveform>(waveform_.load(std::memory_order_relaxed));
            const float glide = glideSec_.load(std::memory_order_relaxed);
            const float velSens = velocitySensitivity_.load(std::memory_order_relaxed);
            if (v->isActive()) v->hardKill();
            v->noteOn(e.midiNote, e.velocity, wf, adsr_, glide, velSens, lastTargetHz_);
            v->setAge(++voiceTickCount_);
            lastTargetHz_ = 440.0f * std::pow(2.0f,
                (static_cast<float>(e.midiNote) - 69.0f) / 12.0f);
            break;
        }
        case NoteEvent::Kind::NoteOff: {
            for (auto& v : voices_) {
                if (v.midiNote() == e.midiNote && v.isActive() && !v.isReleasing()) {
                    v.noteOff(adsr_);
                }
            }
            break;
        }
        case NoteEvent::Kind::AllNotesOff: {
            for (auto& v : voices_) {
                if (v.isActive()) v.noteOff(adsr_);
            }
            break;
        }
    }
}

oboe::DataCallbackResult SynthEngine::onAudioReady(oboe::AudioStream* /*stream*/,
                                                   void* audioData,
                                                   int32_t numFrames) {
    auto* out = static_cast<float*>(audioData);

    // Render mono mix into a stack scratch buffer, then duplicate to stereo.
    // numFrames is bounded by Oboe's burst (typically 96..480), well within
    // a stack frame.
    constexpr int32_t kMaxBurstFrames = 1024;
    float scratch[kMaxBurstFrames];
    if (numFrames > kMaxBurstFrames) numFrames = kMaxBurstFrames;
    std::memset(scratch, 0, sizeof(float) * numFrames);

    drainEvents();

    for (auto& v : voices_) {
        v.renderAdd(scratch, numFrames, adsr_, filter_);
    }

    const float amp = masterAmp_.load(std::memory_order_relaxed);
    float localPeak = 0.0f;

    const bool recording = recording_.load(std::memory_order_acquire);
    int32_t recWrite = recWriteIdx_.load(std::memory_order_relaxed);
    const int32_t recRead = recReadIdx_.load(std::memory_order_acquire);

    const bool scopeOn = scopeEnabled_.load(std::memory_order_acquire);
    int32_t scopeWrite = scopeWriteIdx_.load(std::memory_order_relaxed);
    const int32_t scopeRead = scopeReadIdx_.load(std::memory_order_acquire);

    for (int32_t i = 0; i < numFrames; ++i) {
        float s = scratch[i] * amp;
        // Soft clip to avoid digital harshness if voices stack.
        if (s > 1.0f)  s = 1.0f - 0.5f * (s - 1.0f);
        if (s < -1.0f) s = -1.0f + 0.5f * (-1.0f - s);
        if (s > 1.0f)  s = 1.0f;
        if (s < -1.0f) s = -1.0f;

        out[2 * i + 0] = s;
        out[2 * i + 1] = s;

        const float a = std::fabs(s);
        if (a > localPeak) localPeak = a;

        if (recording) {
            // Write stereo into ring; drop frames if reader is too far behind.
            const int32_t nextWrite = (recWrite + 1) & kRecordingRingMask;
            if (nextWrite != (recRead & kRecordingRingMask)) {
                recordingRing_[(recWrite & kRecordingRingMask) * 2 + 0] = s;
                recordingRing_[(recWrite & kRecordingRingMask) * 2 + 1] = s;
                recWrite = nextWrite;
            }
        }

        if (scopeOn) {
            const int32_t nextScope = (scopeWrite + 1) & kScopeRingMask;
            if (nextScope != (scopeRead & kScopeRingMask)) {
                scopeRing_[scopeWrite & kScopeRingMask] = s;
                scopeWrite = nextScope;
            }
        }
    }

    if (recording) {
        recWriteIdx_.store(recWrite, std::memory_order_release);
    }
    if (scopeOn) {
        scopeWriteIdx_.store(scopeWrite, std::memory_order_release);
    }

    // Update peak meter (atomic max).
    float prev = peak_.load(std::memory_order_relaxed);
    while (localPeak > prev &&
           !peak_.compare_exchange_weak(prev, localPeak,
                                        std::memory_order_relaxed)) {
        // retry
    }

    return oboe::DataCallbackResult::Continue;
}

int32_t SynthEngine::drainRecordingFrames(float* outFloats, int32_t maxFrames) {
    const int32_t w = recWriteIdx_.load(std::memory_order_acquire);
    int32_t r = recReadIdx_.load(std::memory_order_relaxed);
    int32_t written = 0;
    while (written < maxFrames && (r & kRecordingRingMask) != (w & kRecordingRingMask)) {
        outFloats[2 * written + 0] = recordingRing_[(r & kRecordingRingMask) * 2 + 0];
        outFloats[2 * written + 1] = recordingRing_[(r & kRecordingRingMask) * 2 + 1];
        r = (r + 1) & kRecordingRingMask;
        ++written;
    }
    recReadIdx_.store(r, std::memory_order_release);
    return written;
}

int32_t SynthEngine::drainScopeFrames(float* out, int32_t maxFrames) {
    const int32_t w = scopeWriteIdx_.load(std::memory_order_acquire);
    int32_t r = scopeReadIdx_.load(std::memory_order_relaxed);
    int32_t written = 0;
    while (written < maxFrames && (r & kScopeRingMask) != (w & kScopeRingMask)) {
        out[written] = scopeRing_[r & kScopeRingMask];
        r = (r + 1) & kScopeRingMask;
        ++written;
    }
    scopeReadIdx_.store(r, std::memory_order_release);
    return written;
}

void SynthEngine::onErrorAfterClose(oboe::AudioStream* /*stream*/, oboe::Result error) {
    LOGW("Stream closed with error: %s", oboe::convertToText(error));
    // Recovery: try to reopen at the next user interaction. Caller can
    // poll sampleRate() == 0 to detect this.
    sampleRate_.store(0, std::memory_order_release);
    stream_.reset();
}

} // namespace synthpiano
