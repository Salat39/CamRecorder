# CamRecorder

CamRecorder is an Android app for in-vehicle camera recording, built with Compose UI, a multi-module architecture, and a background watchdog loop.

## Support the project

If this app helps you, you can support further development, maintenance, and new features.

[![Donate](https://img.shields.io/badge/Donate-CloudTips-orange?style=for-the-badge)](https://pay.cloudtips.ru/p/19d38600)

### Crypto
- **BTC:** `bc1q37z3d7avhsq3ehpsjm2wldj86ajsnsd6gqnkzm`
- **ETH:** `0x69C73C422FEBBf12F47C29C51501Ad659fcdf74A`

Thanks for supporting the project.

## What This Project Does

- Starts and controls multi-camera recording through `core:recorder`.
- Applies recording conditions based on storage availability, ignition state, and user preferences.
- Provides the main recording control UI in `feature:preview`.
- Exposes app settings in `feature:settings` and recorded media browsing in `feature:archive`.
- Uses system-level capabilities such as camera, file access, and accessibility service.

## Tech Stack

- Kotlin + Coroutines + Flow
- Jetpack Compose (Material 3, Navigation Compose)
- Hilt (DI)
- AndroidX DataStore
- Timber (logging)
- Detekt + Ktlint (static analysis and formatting)
- Baseline Profile

## Module Structure

This is a multi-module project. Gradle includes container projects `core` and `feature` (no code; they group subprojects). Notable modules:

**Application**

- `app` - application entry point (`App`, `MainActivity`), app initialization, navigation host.

**Feature modules**

- `feature:preview` - main recording control screen and ViewModel.
- `feature:settings` - settings UI and related presentation logic.
- `feature:archive` - archive / recorded media UI (browsing tracks, navigation back to preview).

**Core — recording and vehicle**

- `core:recorder` - recording engine and watchdog logic.
- `core:driveStorage` - removable drive and file management.
- `core:carApi` - vehicle signals (for example, ignition state).
- `core:sharedEvents` - cross-module signaling.

**Core — shared infrastructure**

- `core:base` - shared Android base types and dependencies used across libraries.
- `core:commonConst` - shared constants.
- `core:coroutines` - coroutine helpers and conventions.
- `core:preferences` - DataStore-backed settings.
- `core:navigation` - navigation graphs, typed routes, and transitions (`MainNavGraph` wires preview, settings, and archive).
- `core:resources` - shared resources (strings, icons, etc.).
- `core:ui` / `core:uikit` - common and app-specific UI components.

**Core — platform / vendor integration**

- `core:ecarxFw` - EcarX framework-facing integration.
- `core:ecarxCar` - EcarX vehicle / car API integration.
- `core:adaptapi` - adaptation layer for vendor or platform APIs.

**Tooling**

- `baselineprofile` - Baseline Profile generation for startup performance (used with the Baseline Profile Gradle plugin).

## Requirements

- JDK 17
- Android SDK (compileSdk 34, targetSdk 34, minSdk 24)
- Gradle Wrapper (`gradle-8.6`)
- Android Studio / IntelliJ with Android support

## Quick Start

1. Clone the repository.
2. Open the project in Android Studio.
3. Make sure Android SDK API 34 is installed/configured.
4. Build debug:

```bash
./gradlew :app:assembleDebug
```

For Windows PowerShell:

```powershell
.\gradlew :app:assembleDebug
```

## Release Signing

The project supports external signing configuration:

1. Create `secure.signing.gradle` (or point to a custom path via `secure.signing=...` in `gradle.properties`).
2. Add signing parameters in that file.
3. Run:

```bash
./gradlew prepareRelease
```

> Debug/internal builds use a dummy signing config by default.

## Navigation and UI Flow

- `MainActivity` initializes Compose content and `NavHost`.
- Start graph is `MainNavGraph` (`core:navigation`).
- Start destination is `PreviewNavRoute` (`feature:preview`); from preview you can open `feature:settings` and `feature:archive`, each with a standard back action to the previous screen.

## Permissions and System Notes

The app uses (among others) the following permissions from `AndroidManifest.xml`:

- `CAMERA`
- `MANAGE_EXTERNAL_STORAGE`
- `RECEIVE_BOOT_COMPLETED`
- `FOREGROUND_SERVICE`
- Accessibility service (`AutoLaunchAccessibilityService`)

## Recording & Conversion (Quick Guide)

### Where recordings are stored

Recordings are written to the `_CamRecords` directory on the connected removable storage.

**Storage recommendations**

- **Portable HDD (exFAT):** a solid default for capacity and broad device support.
- **USB flash drive (FAT32):** prefer a fast drive; slow flash can increase recording artifacts.

### Cameras and frame rate (performance)

**Example setup (author):** front camera at 20 fps, rear at 15 fps, side cameras not recorded.

Enabling every camera or pushing frame rates higher is a **performance trade-off**: more streams and higher fps load the CPU, I/O, and thermals harder, which can affect stability and recording quality on weaker head units.

### Why raw `.h264` is used
- Lower CPU usage (no real-time muxing)
- More stable on weak devices
- Fewer frame drops under load

Raw `.h264` has **no timestamps**, so a small `.idx` file is written alongside it to store timing.

---

### What files you get
- `video.h264` — raw video
- `video.h264.idx` — timing index

---

### How to convert

Requirements:

- Python 3 (required)
- ffmpeg (auto-downloaded)

#### Option 1 (recommended)
Put files in the same folder and run:

```
\_convert_h264_to_mp4\convert_h264_to_mp4.cmd
```

#### Option 2 (manual)
```
python convert_indexed_h264.py video.h264
```

---

### Output
- `video.mp4` — ready to play

---

### If `.idx` is missing (shit happens)
```
ffmpeg -f h264 -framerate 20 -i video.h264 -c:v copy video.mp4
```
Note: duration may be wrong.

---

### Why not write MP4 directly
- MP4/TS require real-time muxing (CPU + I/O cost)
- Increases risk of dropped frames on weak hardware
- Less stable for long multi-camera recording

This approach:
- keeps recording lightweight
- restores correct video later using `.idx`
