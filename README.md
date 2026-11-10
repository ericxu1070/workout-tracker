# GZCLP tracker

An Android app for the 4-day GZCLP program (A1, B1, A2, B2). It shows the next workout, logs every set, applies T1, T2, and T3 progression and resets, runs a rest timer that vibrates with the screen off, and draws charts. All data stays on the phone. The app mirrors its log to a folder you share with Syncthing. `docs/log-format.md` describes that log for the desktop analysis app.

## Build

Requirements: JDK 21 and an Android SDK with `platforms;android-35` and `build-tools;35.0.0`. The Gradle wrapper downloads Gradle itself.

```bash
eval "$(mise env -s bash)"            # sets ANDROID_HOME on this machine
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew test                         # progression engine and log tests
./gradlew assembleDebug                # app/build/outputs/apk/debug/app-debug.apk
```

Screenshots of every screen in every theme:

```bash
./gradlew recordRoborazziDebug -PgzclpShots=/path/to/shots
```

## Install

Enable USB debugging on the phone, connect it, then:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.gzclp.tracker/.MainActivity
```

## First run

1. Enter a 5-rep max for each main lift. T1 starts at 85% and T2 at 65%, rounded to 5 lb.
2. In Settings, choose the sync folder (for example `Documents/GZCLP`) and share it with Syncthing. The app writes `gzclp-log.jsonl` there.
3. Allow notifications when asked, so the rest timer can alert you with the screen off.

After a reinstall, tap Restore log on the first screen and pick the same folder.

## Layout

- `engine/` holds the program rules as pure Kotlin.
- `log/` holds the event log, its JSON codec, the state fold, and the commands that turn taps into events.
- `sync/` mirrors the log into the shared folder.
- `rest/` holds the rest alarm and its notifications.
- `ui/` holds the Compose screens and the four color themes.
