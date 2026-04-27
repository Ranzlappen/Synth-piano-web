# Synth Piano (Android)

A Kotlin/Compose port of [Ranzlappen/synth-piano](https://github.com/Ranzlappen/synth-piano), the Python tkinter synth. Touch keyboard, chord pads, score playback, USB-MIDI in, WAV recording. Real-time audio runs through Oboe on a C++ DSP core for sub-20ms latency.

## Quick Reference

```
./gradlew assembleDebug          # Debug APK -> app/build/outputs/apk/debug/
./gradlew assembleRelease        # Release APK (needs signing config)
./gradlew bundleRelease          # Release AAB -> app/build/outputs/bundle/release/
./gradlew test                   # JVM unit tests
./gradlew connectedAndroidTest   # Instrumented tests (device/emulator required)
./gradlew lint                   # Android Lint
```

| Thing | Where |
| --- | --- |
| App entry | `app/src/main/java/io/github/ranzlappen/synthpiano/MainActivity.kt` |
| Compose UI | `app/src/main/java/io/github/ranzlappen/synthpiano/ui/` |
| JNI bridge | `app/src/main/java/io/github/ranzlappen/synthpiano/audio/NativeSynth.kt` |
| Native engine | `app/src/main/cpp/` |
| Bundled scores | `app/src/main/assets/scores/` |
| CI | `.github/workflows/ci-android.yml` |

## Features

- **Touch keyboard** — multi-touch, configurable octave span, transposition.
- **Synth voice** — sine / square / saw / triangle oscillator with full ADSR envelope, 16-voice polyphony.
- **Chord pads** — 11 user-assignable pads supporting maj / min / 7 / dim / sus chord qualities across all 12 roots.
- **Score system** — play, load (Storage Access Framework), and edit JSON scores. Round-trip-compatible with the Python source's format. 3-5 demos bundled in `assets/scores/`.
- **USB MIDI input** — plug a controller via OTG, no permission prompt needed.
- **Hardware keyboard** — Bluetooth/USB QWERTY plays notes (defaults match the Python ASDFG layout, fully remappable in settings).
- **Recording** — capture the master output to a WAV file in app-specific storage, share via the system sheet.
- **Material 3** dynamic theming, landscape-locked, responsive on tablets and foldables.

## Build & Development

### Prerequisites

- JDK 17 (Temurin recommended)
- Android SDK with `platform-android-35`, `build-tools-35.0.0`, `cmake 3.22.1`, `ndk 27.0.12077973`
- A real device for serious latency testing (emulators add ~80ms)

### First-time setup

```
git clone <this-repo>
cd Synth-piano-web
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug
```

### Run on a device

```
./gradlew installDebug
adb shell am start -n io.github.ranzlappen.synthpiano/.MainActivity
```

## Key Conventions

- **Audio thread is sacred.** No allocations, no JNI calls back to Java, no locks in the Oboe `onAudioReady` callback. State changes from Kotlin reach the engine via `std::atomic` + a lock-free SPSC ring of note events.
- **All DSP lives in `app/src/main/cpp/`.** Kotlin only orchestrates UI, persistence, MIDI, and the recording WAV writer. Synthesis math never round-trips through the JVM.
- **Score JSON format matches the Python source.** Don't add fields without bumping a `version` field. The parser tolerates the original schema (no `version` = v1).
- **Landscape-only** in the manifest. Compose code may assume `LocalConfiguration.current.screenWidthDp >= 480`.
- **Package ID is permanent** — `io.github.ranzlappen.synthpiano`. Do not rename without a Play Store migration plan.

## Deployment & CI/CD

| Workflow | Trigger | Scope | Deploys |
| --- | --- | --- | --- |
| `ci-android.yml` | push, pull_request, tag `v*` | All paths | Uploads APK + AAB artifacts; release-signed AAB on tags |

**What fires on a given change:**

| Change | CI | Deploy |
| --- | --- | --- |
| Source in `app/`, `gradle/`, root build files | ✓ | ✓ (artifacts) |
| Docs (`README.md`, `CLAUDE.md`) | — | — |
| Workflows (`.github/workflows/*.yml`) | ✓ | — |

**Concurrency**: CI cancels superseded runs per PR.

**Runtime versions**: JDK 17, Android SDK 35, NDK r27, Gradle 8.10.2.

**Required secrets** (for release-signed AAB on tags):

| Secret | Used for |
| --- | --- |
| `KEYSTORE_BASE64` | Base64-encoded `release.keystore` |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Signing key alias inside the keystore |
| `KEY_PASSWORD` | Password for the signing key |

To rotate: generate a new keystore, base64-encode it (`base64 -w0 release.keystore`), update all four secrets, then push a new tag.

## Tech Stack

| Layer | Technology | Role | Why |
| --- | --- | --- | --- |
| Language | Kotlin 2.0 | App / business logic | Android's primary language |
| UI | Jetpack Compose + Material 3 | Declarative UI | Best fit for a piano keyboard, theming for free |
| Audio | Oboe 1.9 + AAudio | Real-time output | Sub-20ms latency, the Google-recommended path |
| DSP | C++17 | Voices, oscillators, envelopes | Audio thread cannot afford the JVM |
| MIDI | `android.media.midi` | USB MIDI in | First-party, no extra deps |
| Persistence | `androidx.datastore` | Settings, key map, last score | Modern replacement for SharedPreferences |
| Build | Gradle 8.10.2 + AGP 8.5 + CMake | Build system | Standard Android toolchain |
| CI | GitHub Actions | Automated builds | Matches Ranzlappen/repo-standards |

## Project Structure

```
Synth-piano-web/
├── app/
│   ├── build.gradle.kts          # App module Gradle config
│   ├── proguard-rules.pro        # R8 keep rules
│   └── src/main/
│       ├── AndroidManifest.xml   # App manifest
│       ├── assets/scores/        # Bundled demo scores (JSON)
│       ├── cpp/                  # Native Oboe + DSP engine
│       │   ├── CMakeLists.txt
│       │   ├── synth_engine.{h,cpp}
│       │   ├── voice.{h,cpp}
│       │   ├── oscillator.{h,cpp}
│       │   ├── envelope.{h,cpp}
│       │   └── jni_bridge.cpp
│       ├── java/io/github/ranzlappen/synthpiano/
│       │   ├── MainActivity.kt
│       │   ├── SynthApp.kt
│       │   ├── audio/            # NativeSynth.kt JNI wrapper, Recorder
│       │   ├── data/             # Score model, parser, prefs, key map
│       │   ├── midi/             # MidiManager.kt
│       │   ├── input/            # HwKeyboardMap.kt
│       │   └── ui/               # All Composables
│       └── res/                  # Material 3 themes, icons, strings
├── gradle/
│   ├── libs.versions.toml        # Version catalog
│   └── wrapper/
├── .github/
│   ├── dependabot.yml            # Weekly grouped updates
│   └── workflows/ci-android.yml  # APK + AAB build with signing
├── build.gradle.kts              # Root project config
├── settings.gradle.kts
├── gradle.properties
├── CLAUDE.md                     # Architecture / conventions for AI contributors
└── README.md                     # This file
```

## License

MIT — see [LICENSE](LICENSE).

## Acknowledgements

Original Python synth piano by [Ranzlappen](https://github.com/Ranzlappen/synth-piano). Audio engine built on [Google Oboe](https://github.com/google/oboe).
