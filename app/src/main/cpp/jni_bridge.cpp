#include "synth_engine.h"

#include <jni.h>

#include <memory>
#include <new>

using synthpiano::SynthEngine;
using synthpiano::Waveform;

namespace {
inline SynthEngine* asEngine(jlong handle) {
    return reinterpret_cast<SynthEngine*>(handle);
}
} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_io_github_ranzlappen_synthpiano_audio_NativeSynth_nativeCreate(JNIEnv*, jobject) {
    return reinterpret_cast<jlong>(new (std::nothrow) SynthEngine());
}

JNIEXPORT void JNICALL
Java_io_github_ranzlappen_synthpiano_audio_NativeSynth_nativeDestroy(JNIEnv*, jobject, jlong h) {
    delete asEngine(h);
}

JNIEXPORT jboolean JNICALL
Java_io_github_ranzlappen_synthpiano_audio_NativeSynth_nativeStart(JNIEnv*, jobject, jlong h) {
    SynthEngine* e = asEngine(h);
    return (e && e->start()) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_io_github_ranzlappen_synthpiano_audio_NativeSynth_nativeStop(JNIEnv*, jobject, jlong h) {
    if (auto* e = asEngine(h)) e->stop();
}

JNIEXPORT void JNICALL
Java_io_github_ranzlappen_synthpiano_audio_NativeSynth_nativeNoteOn(JNIEnv*, jobject, jlong h,
                                                                     jint midiNote, jfloat velocity) {
    if (auto* e = asEngine(h)) e->postNoteOn(midiNote, velocity);
}

JNIEXPORT void JNICALL
Java_io_github_ranzlappen_synthpiano_audio_NativeSynth_nativeNoteOff(JNIEnv*, jobject, jlong h,
                                                                      jint midiNote) {
    if (auto* e = asEngine(h)) e->postNoteOff(midiNote);
}

JNIEXPORT void JNICALL
Java_io_github_ranzlappen_synthpiano_audio_NativeSynth_nativeAllNotesOff(JNIEnv*, jobject, jlong h) {
    if (auto* e = asEngine(h)) e->postAllNotesOff();
}

JNIEXPORT void JNICALL
Java_io_github_ranzlappen_synthpiano_audio_NativeSynth_nativeSetWaveform(JNIEnv*, jobject, jlong h,
                                                                          jint wf) {
    if (auto* e = asEngine(h)) e->setWaveform(static_cast<Waveform>(wf));
}

JNIEXPORT void JNICALL
Java_io_github_ranzlappen_synthpiano_audio_NativeSynth_nativeSetAdsr(JNIEnv*, jobject, jlong h,
                                                                      jfloat a, jfloat d, jfloat s, jfloat r) {
    if (auto* e = asEngine(h)) e->setAdsr(a, d, s, r);
}

JNIEXPORT void JNICALL
Java_io_github_ranzlappen_synthpiano_audio_NativeSynth_nativeSetMasterAmp(JNIEnv*, jobject, jlong h,
                                                                           jfloat amp) {
    if (auto* e = asEngine(h)) e->setMasterAmp(amp);
}

JNIEXPORT jint JNICALL
Java_io_github_ranzlappen_synthpiano_audio_NativeSynth_nativeGetSampleRate(JNIEnv*, jobject, jlong h) {
    return asEngine(h) ? asEngine(h)->sampleRate() : 0;
}

JNIEXPORT jfloat JNICALL
Java_io_github_ranzlappen_synthpiano_audio_NativeSynth_nativeGetMasterPeak(JNIEnv*, jobject, jlong h) {
    return asEngine(h) ? asEngine(h)->masterPeak() : 0.0f;
}

JNIEXPORT void JNICALL
Java_io_github_ranzlappen_synthpiano_audio_NativeSynth_nativeSetRecordingEnabled(JNIEnv*, jobject, jlong h,
                                                                                   jboolean on) {
    if (auto* e = asEngine(h)) e->setRecordingEnabled(on == JNI_TRUE);
}

JNIEXPORT jint JNICALL
Java_io_github_ranzlappen_synthpiano_audio_NativeSynth_nativeDrainRecording(JNIEnv* env, jobject,
                                                                             jlong h,
                                                                             jfloatArray out,
                                                                             jint maxFrames) {
    SynthEngine* e = asEngine(h);
    if (!e || out == nullptr) return 0;
    jboolean isCopy = JNI_FALSE;
    jfloat* buf = env->GetFloatArrayElements(out, &isCopy);
    if (!buf) return 0;
    const jsize len = env->GetArrayLength(out);
    int32_t cap = static_cast<int32_t>(len) / 2;
    if (maxFrames > 0 && maxFrames < cap) cap = maxFrames;
    int32_t written = e->drainRecordingFrames(buf, cap);
    env->ReleaseFloatArrayElements(out, buf, 0);
    return written;
}

} // extern "C"
