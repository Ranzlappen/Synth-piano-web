#pragma once

#include <atomic>
#include <cstdint>

namespace synthpiano {

/**
 * Multi-segment envelope generator parameters. Holds an array of
 * (timeSec, level, curve) breakpoints joined by curved segments and a
 * sustain index whose vertex is held while a key is down. Vertices
 * after [sustainIndex] form the release tail.
 *
 * Producer side (UI thread, single producer) writes via [writeShape]
 * or the legacy [setAdsr] / [setCurve] entry points; the audio thread
 * reads via [tryReadSnapshot] under a SeqLock so it sees a coherent
 * (atomic) view of the breakpoint array, even if the producer is
 * mid-write. Both [liveSustainLevel] and [globalCurve] are simple
 * atomics for the per-tick fast path that doesn't need the full
 * snapshot.
 */
class EnvelopeParams {
public:
    static constexpr int32_t kMaxVertices = 16;

    struct Snapshot {
        int32_t numVertices = 5;
        int32_t sustainIndex = 3;
        // SoA: parallel arrays for cache locality during segment walks.
        float timeSec[kMaxVertices] = {};
        float level[kMaxVertices]   = {};
        float curve[kMaxVertices]   = {};
    };

    EnvelopeParams();

    /**
     * Replace the active envelope shape with [src]. Writer side, called
     * from a single producer thread (UI). Implemented as a SeqLock:
     * the audio thread re-reads on a torn race. Allocation-free.
     */
    void writeShape(const Snapshot& src);

    /**
     * Audio-thread snapshot read. On success [out] holds a coherent
     * copy of the current shape. May fail under heavy contention; on
     * failure callers should keep using their previous cached snapshot
     * for one more tick.
     */
    bool tryReadSnapshot(Snapshot& out) const;

    /**
     * Legacy API: build the canonical 5-vertex ADSR shape from
     * (a, d, s, r, curve) and publish via [writeShape]. Backward-
     * compatible entry point so existing setAdsr/setEnvelopeCurve call
     * sites keep working unchanged.
     */
    void setAdsr(float attackSec, float decaySec, float sustain, float releaseSec);
    void setCurve(float c);

    /** Cheap audio-thread mirror of the sustain vertex's level. */
    float liveSustainLevel() const {
        return liveSustainLevel_.load(std::memory_order_relaxed);
    }

    /** Cheap audio-thread mirror of the global per-segment curve. */
    float globalCurve() const {
        return globalCurve_.load(std::memory_order_relaxed);
    }

    /** Monotonic counter; even when consistent, odd while a writer is mid-update. */
    uint32_t seq() const { return seq_.load(std::memory_order_acquire); }

private:
    // Cached canonical-shape parameters used by setAdsr+setCurve so the
    // two legacy entry points can be called independently without
    // clobbering each other's contribution.
    float legacyAttack_ = 0.005f;
    float legacyDecay_ = 0.150f;
    float legacySustain_ = 0.700f;
    float legacyRelease_ = 0.250f;
    float legacyCurve_ = 0.0f;
    void publishLegacyShape();

    std::atomic<uint32_t> seq_{0};
    Snapshot data_{};

    std::atomic<float> liveSustainLevel_{0.7f};
    std::atomic<float> globalCurve_{0.0f};
};

/**
 * Backward-compat alias. Existing call sites that took an
 * [AdsrParams&] continue to compile against the new MSEG class.
 */
using AdsrParams = EnvelopeParams;

class Envelope {
public:
    enum class Stage { Idle, Active, Sustaining, Releasing, Killing };

    void setSampleRate(float sr) { sampleRate_ = sr; }

    void noteOn(const EnvelopeParams& params);
    void noteOff(const EnvelopeParams& params);

    /**
     * Force a fast (~1 ms) ramp to silence and then Idle. Used by the
     * voice stealer so the new note-on can take over without a click.
     * Skips the segment list entirely, so it doesn't need a fresh
     * params snapshot.
     */
    void hardKill();

    /** One-sample tick. Walks the segment list and returns the level. */
    float tick(const EnvelopeParams& params);

    bool isActive() const { return stage_ != Stage::Idle; }
    Stage stage() const { return stage_; }
    float currentLevel() const { return level_; }

private:
    void refreshSnapshot(const EnvelopeParams& params);
    void enterSegmentTo(int32_t targetVertex);

    float sampleRate_ = 48000.0f;
    EnvelopeParams::Snapshot snapshot_{};
    int32_t numVertices_ = 5;
    int32_t sustainIdx_ = 3;

    Stage stage_ = Stage::Idle;
    int32_t currentVertex_ = 0;     // vertex we just left (segment source)
    float level_ = 0.0f;             // current envelope level [0, 1]
    float startLevel_ = 0.0f;        // level at currentVertex_
    float targetLevel_ = 0.0f;       // level at currentVertex_+1
    float segmentCurve_ = 0.0f;      // curve into currentVertex_+1
    float t_ = 0.0f;                 // 0..1 progress within current segment
    float dt_ = 0.0f;                // per-sample increment of t_
};

} // namespace synthpiano
