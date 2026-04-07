# CamRecorder

CamRecorder is an Android app for in-vehicle camera recording, built with Compose UI, a multi-module architecture, and a background watchdog loop.

## What This Project Does

- Starts and controls multi-camera recording through `core:recorder`.
- Applies recording conditions based on storage availability, ignition state, and user preferences.
- Provides the main recording control UI in `feature:preview`.
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

This is a multi-module project. Key modules:

- `app` - application entry point (`App`, `MainActivity`), app initialization, navigation host.
- `feature:preview` - main recording control screen and ViewModel.
- `core:recorder` - recording engine and watchdog logic.
- `core:driveStorage` - removable drive and file management.
- `core:carApi` - vehicle signals (for example, ignition state).
- `core:preferences` - DataStore-backed settings.
- `core:sharedEvents` - cross-module signaling.
- `core:navigation` - navigation graphs and transitions.
- `core:resources` - shared resources (strings, icons, etc.).
- `core:ui` / `core:uikit` - common and app-specific UI components.

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

## Useful Commands

- Build:
  - `./gradlew :app:assembleDebug`
  - `./gradlew :app:assembleRelease`
- Lint/style checks:
  - `./gradlew ktlint`
  - `./gradlew detekt`
- Auto-format:
  - `./gradlew ktlintFormat`
- Release preparation (profile + build):
  - `./gradlew prepareRelease`

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
- Start graph is `MainNavGraph`.
- Current start destination is `PreviewNavRoute` (`feature:preview`).

## Permissions and System Notes

The app uses (among others) the following permissions from `AndroidManifest.xml`:

- `CAMERA`
- `MANAGE_EXTERNAL_STORAGE`
- `RECEIVE_BOOT_COMPLETED`
- `FOREGROUND_SERVICE`
- Accessibility service (`AutoLaunchAccessibilityService`)

Some functionality may require privileged/system-level access in the target environment.

## Notes

- The project is designed for an automotive dashcam/recorder scenario.
- Some modules integrate with platform/vendor APIs (`car` / `fw` / `adaptapi`).
