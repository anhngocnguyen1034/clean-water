# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

This is an Android project built with Gradle (Kotlin DSL). Use Android Studio or the Gradle wrapper:

```bash
# Debug build
./gradlew assembleDebug

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest

# Install on connected device
./gradlew installDebug
```

- **minSdk**: 24, **targetSdk**: 36, **compileSdk**: 36
- **Kotlin JVM target**: 11

## Architecture

Single-activity app with **Jetpack Compose** UI, no navigation library — screens are swapped manually with a `showPhoneTest` boolean state in `MainActivity`.

### Key files

| File | Role |
|---|---|
| `MainActivity.kt` | Single activity; hosts all top-level Compose state; owns `SoundManager` lifecycle (`onCreate`/`onDestroy`) |
| `SoundManager.kt` | Manages `AudioTrack` (sine-wave PCM generation on a background thread) and `Vibrator`; exposes `isRunning: StateFlow<Boolean>`; thread-safe via `AtomicBoolean`/`AtomicInteger` |
| `PhoneTestScreen.kt` | Separate full-screen composable for the phone hardware test feature (touch, speaker, mic, vibration); navigated to via `onOpenPhoneTest` callback |

### UI structure in `MainActivity.kt`

`CleanWaterApp` composable holds all mutable state and renders a vertical scrollable column of section cards:
- `AppHeader` — title
- `TimerSection` — animated water-fill circle + Start/Stop button; countdown via `LaunchedEffect`
- `FrequencyPresetSection` — 4 preset buttons (165/432/528/741 Hz)
- `FrequencySliderSection` — manual Hz slider (100–1000 Hz)
- `VibrationSection` — OFF/CONTINUOUS/PULSE mode selector + amplitude slider
- `DurationPickerSection` — duration grid (15s–60s)
- Phone test card → navigates to `PhoneTestScreen`

### SoundManager internals

- Generates sine-wave PCM (`ShortArray`) on `SoundManager-AudioThread`, writes to `AudioTrack` in streaming mode.
- PULSE mode: audio fades in/out with 128-sample linear envelope to avoid clicks; vibration uses `createWaveform` with `repeat=0`.
- CONTINUOUS vibration: `createWaveform` with `repeat=1` (loops from the non-silent segment to avoid gaps).
- Stop sequence: sets `stopRequested`, waits for the audio thread to complete a 2048-sample fade-out (~46ms), then releases `AudioTrack`.
- `setFrequency()` has a 1-second debounce on vibration restart to prevent excessive restarts while dragging the slider.

### Permissions (AndroidManifest)

- `VIBRATE` — vibration
- `WAKE_LOCK` — keep screen on while running
- `RECORD_AUDIO` — microphone test in `PhoneTestScreen`

## Known issues / backlog (from `doc/clean-water.md`)

1. A strange sound plays when the process ends or is stopped.
2. The START button should be full-width, not circular.
3. Remove the waveform visualizer below the START CLEANING button.
