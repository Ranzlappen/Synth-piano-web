#include "envelope.h"

#include <algorithm>
#include <cmath>
#include <cstring>

namespace synthpiano {

namespace {
constexpr float kMinSegmentSeconds = 0.0005f;
constexpr int   kReadRetryLimit = 4;

inline float shapeCurve(float t, float curve) {
    if (t <= 0.0f) return 0.0f;
    if (t >= 1.0f) return 1.0f;
    if (curve > -1e-3f && curve < 1e-3f) return t;
    // Same shaping function as the legacy ADSR: pow(level, exp2(curve*2)).
    // Applied to per-segment progress so the curve is segment-relative.
    return std::pow(t, std::exp2(curve * 2.0f));
}
} // namespace

EnvelopeParams::EnvelopeParams() {
    // Publish the default canonical 5-vertex ADSR shape so any voice
    // started before the UI writes anything sounds normal.
    publishLegacyShape();
}

void EnvelopeParams::writeShape(const Snapshot& src) {
    Snapshot clean{};
    clean.numVertices = std::max(1, std::min(static_cast<int32_t>(kMaxVertices), src.numVertices));
    clean.sustainIndex = std::max(0, std::min(clean.numVertices - 1, src.sustainIndex));
    for (int32_t i = 0; i < clean.numVertices; ++i) {
        clean.timeSec[i] = std::max(0.0f, src.timeSec[i]);
        clean.level[i]   = std::clamp(src.level[i], 0.0f, 1.0f);
        clean.curve[i]   = std::clamp(src.curve[i], -1.0f, 1.0f);
    }

    // SeqLock write: bump to odd, copy, bump to even.
    const uint32_t v0 = seq_.load(std::memory_order_relaxed);
    seq_.store(v0 + 1, std::memory_order_release);
    data_ = clean;
    seq_.store(v0 + 2, std::memory_order_release);

    liveSustainLevel_.store(clean.level[clean.sustainIndex], std::memory_order_relaxed);
}

bool EnvelopeParams::tryReadSnapshot(Snapshot& out) const {
    for (int attempt = 0; attempt < kReadRetryLimit; ++attempt) {
        const uint32_t v1 = seq_.load(std::memory_order_acquire);
        if (v1 & 1u) continue;       // writer mid-update; spin
        // Memcpy is allocation-free and bounded (<= 16 vertices * 12B).
        std::memcpy(&out, &data_, sizeof(Snapshot));
        std::atomic_thread_fence(std::memory_order_acquire);
        const uint32_t v2 = seq_.load(std::memory_order_acquire);
        if (v1 == v2) return true;   // consistent snapshot
    }
    return false;                    // gave up; caller keeps cached
}

void EnvelopeParams::setAdsr(float attackSec, float decaySec, float sustain, float releaseSec) {
    legacyAttack_ = std::max(kMinSegmentSeconds, attackSec);
    legacyDecay_ = std::max(kMinSegmentSeconds, decaySec);
    legacySustain_ = std::clamp(sustain, 0.0f, 1.0f);
    legacyRelease_ = std::max(kMinSegmentSeconds, releaseSec);
    publishLegacyShape();
}

void EnvelopeParams::setCurve(float c) {
    legacyCurve_ = std::clamp(c, -1.0f, 1.0f);
    globalCurve_.store(legacyCurve_, std::memory_order_relaxed);
    publishLegacyShape();
}

void EnvelopeParams::publishLegacyShape() {
    Snapshot s{};
    s.numVertices = 5;
    s.sustainIndex = 3;
    s.timeSec[0] = 0.0f;          s.level[0] = 0.0f;          s.curve[0] = 0.0f;
    s.timeSec[1] = legacyAttack_; s.level[1] = 1.0f;          s.curve[1] = legacyCurve_;
    s.timeSec[2] = legacyDecay_;  s.level[2] = legacySustain_; s.curve[2] = legacyCurve_;
    s.timeSec[3] = 0.0f;          s.level[3] = legacySustain_; s.curve[3] = 0.0f;
    s.timeSec[4] = legacyRelease_; s.level[4] = 0.0f;          s.curve[4] = legacyCurve_;
    writeShape(s);
}

void Envelope::refreshSnapshot(const EnvelopeParams& params) {
    EnvelopeParams::Snapshot tmp{};
    if (params.tryReadSnapshot(tmp)) {
        snapshot_ = tmp;
        numVertices_ = std::max(1, std::min(static_cast<int32_t>(EnvelopeParams::kMaxVertices),
                                            tmp.numVertices));
        sustainIdx_ = std::max(0, std::min(numVertices_ - 1, tmp.sustainIndex));
    }
}

void Envelope::enterSegmentTo(int32_t targetVertex) {
    startLevel_ = level_;
    targetLevel_ = snapshot_.level[targetVertex];
    segmentCurve_ = snapshot_.curve[targetVertex];
    const float seconds = snapshot_.timeSec[targetVertex];
    if (seconds <= kMinSegmentSeconds) {
        // Zero-length segment — jump to target so the sustain pin and
        // similar zero-time vertices are processed in one tick.
        level_ = targetLevel_;
        t_ = 1.0f;
        dt_ = 0.0f;
    } else {
        const float frames = seconds * sampleRate_;
        dt_ = (frames > 1.0f) ? (1.0f / frames) : 1.0f;
        t_ = 0.0f;
    }
}

void Envelope::noteOn(const EnvelopeParams& params) {
    refreshSnapshot(params);
    if (numVertices_ < 2) {
        stage_ = Stage::Idle;
        level_ = 0.0f;
        return;
    }
    // Restart from the current level (legato-friendly): if a voice is
    // being re-triggered we glide up from where we are rather than
    // snapping to the start vertex's level (typically 0).
    currentVertex_ = 0;
    enterSegmentTo(1);
    stage_ = Stage::Active;
}

void Envelope::noteOff(const EnvelopeParams& params) {
    refreshSnapshot(params);
    if (stage_ == Stage::Idle) return;
    // Resume from the sustain vertex toward the next vertex; if the
    // sustain pin is the last vertex, fade to silence directly.
    currentVertex_ = sustainIdx_;
    if (sustainIdx_ + 1 >= numVertices_) {
        stage_ = Stage::Idle;
        level_ = 0.0f;
        return;
    }
    enterSegmentTo(sustainIdx_ + 1);
    stage_ = Stage::Releasing;
}

void Envelope::hardKill() {
    if (stage_ == Stage::Idle) {
        level_ = 0.0f;
        return;
    }
    const float frames = std::max(1.0f, 0.001f * sampleRate_);
    startLevel_ = level_;
    targetLevel_ = 0.0f;
    segmentCurve_ = 0.0f;
    dt_ = 1.0f / frames;
    t_ = 0.0f;
    stage_ = Stage::Killing;
}

float Envelope::tick(const EnvelopeParams& params) {
    if (stage_ == Stage::Idle) return 0.0f;

    if (stage_ == Stage::Killing) {
        t_ += dt_;
        if (t_ >= 1.0f) {
            stage_ = Stage::Idle;
            level_ = 0.0f;
            return 0.0f;
        }
        level_ = startLevel_ * (1.0f - t_);
        return level_;
    }

    if (stage_ == Stage::Sustaining) {
        // Track live sustain-level changes (slider tweaks during a
        // long hold) via the cheap atomic mirror; full snapshot reads
        // would be too costly per-sample.
        level_ = params.liveSustainLevel();
        return level_;
    }

    // Advance progress within the current segment.
    if (dt_ > 0.0f) {
        t_ += dt_;
        if (t_ < 1.0f) {
            const float shaped = shapeCurve(t_, segmentCurve_);
            level_ = startLevel_ + (targetLevel_ - startLevel_) * shaped;
            return level_;
        }
    }
    // Reached / past the next vertex. Walk forward, skipping any
    // zero-length segments (sustain pin, etc.) in this same tick so
    // they don't burn one sample apiece.
    while (true) {
        level_ = targetLevel_;
        ++currentVertex_;
        if (currentVertex_ >= numVertices_ - 1) {
            stage_ = Stage::Idle;
            level_ = 0.0f;
            return 0.0f;
        }
        if (stage_ == Stage::Active && currentVertex_ == sustainIdx_) {
            stage_ = Stage::Sustaining;
            return level_;
        }
        enterSegmentTo(currentVertex_ + 1);
        if (dt_ > 0.0f) {
            // First sample of the new real segment.
            t_ = dt_;
            const float shaped = shapeCurve(t_, segmentCurve_);
            level_ = startLevel_ + (targetLevel_ - startLevel_) * shaped;
            return level_;
        }
        // Zero-length segment; loop and continue advancing.
    }
}

} // namespace synthpiano
