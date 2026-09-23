# AlterLingua

An Android-first multilingual communication and language-learning layer that works with
existing messaging apps (initially WhatsApp). You write in your language; they receive theirs.
AlterLingua is not a messaging app and never sends a message for you.

Project rules, architecture and milestone order live in [`CLAUDE.md`](CLAUDE.md).
Build status is in [`docs/progress.md`](docs/progress.md); the narrative history is in
[`docs/build-log.md`](docs/build-log.md).

## Repository layout

| Path | Contents |
|---|---|
| `android/` | Native Android app (Kotlin, Jetpack Compose, Gradle) |
| `backend/` | FastAPI backend (see `backend/README.md`) |
| `docs/` | Progress and build documentation |

## Build the Android app

Requirements: JDK 17+ and the Android SDK (`ANDROID_HOME` set, platform 37 and build-tools 36.0.0).
No global Gradle install is needed; the Gradle Wrapper downloads the pinned version.

```bash
cd android
./gradlew assembleDebug          # build the debug APK
./gradlew testDebugUnitTest      # run unit tests
./gradlew lintDebug              # run Android lint
./gradlew installDebug           # install on a connected device (USB debugging on)
```

Open the **`android/`** folder (not the repository root) in Android Studio.

The app's package and application ID is `com.alterlingua.app`. At this stage it is an app shell: theme, bottom
navigation and five tabs (Home, Learn, Words, Progress, Settings) showing placeholder data. Fonts (Inter, Plus Jakarta Sans)
are licensed under the SIL Open Font License; see `docs/licenses/`.
