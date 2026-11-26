# Repsilon

Repsilon is an Android app for lifting, cardio, stretching, body weight, food and water. It runs your program, logs every set, and keeps everything on your phone.

<p>
<img src="docs/images/performance/workout.webp" width="200" alt="Workout tab: the next workout with its lifts and weights">
<img src="docs/images/loaded/sets.webp" width="200" alt="A workout in progress in Liquid Glass: sets, plates per side, and the rest timer">
<img src="docs/images/performance/history-week.webp" width="200" alt="History week: time by kind, the week's calendar, and sets by muscle group">
<img src="docs/images/loaded/stretching.webp" width="200" alt="Stretching tab in Liquid Glass: your routines and the built-in library">
</p>

## New in 3.0

Plateline is now Repsilon, with a new icon, a new name, and a launch animation. Updating keeps your log.

- **Stretching tab.** A live player with a get-ready countdown, a continuous timer, paced reps for dynamic moves, and spoken cues. Famous routines (Limber 11, Simple 6, Starting to Stretch, Sun salutation A) and 52 animated moves. Build your own routines, or copy and edit a built-in one.
- **Workout.** Lifting, Cardio and Stretching sit in one segment you can switch mid-lift. Each tab's main action and the rest timer live in the bottom bar, so logging a set moves nothing. A pill behind the current set springs to the next one.
- **Upcoming** leads with your next workout and the day it will likely fall on.
- **History.** Week, Month and All. The calendar marks each day with a dot per kind of training, weeks start on Monday, and Month adds sessions per week and the average session of each kind.
- **Exercises.** A muscle map of the day, week or month, and a 3D form viewer with male and female bodies in athletic, bodybuilder and normal builds (Settings › Appearance › Gender and Build).
- **Swipe left to delete** workouts, cardio, exercises and weigh-ins. Weigh-ins get a Saved · Undo bar.
- **Water goal** follows your body weight and the day's lifting and cardio.
- **Settings** is a short list with sub-pages. Exercises per workout gets an edit mode, and Show plates hides the per-side plate lines.
- **Appearance.** Light, Dark, System, every Omarchy theme, and the Laptop theme that follows your computer. Motion is Performance (flat and fast, the default) or Loaded: Liquid Glass bars and sheets, plus a whoosh on sheets and pushed screens, ripples, a sloshing rest bar, and a pour into the water ring.
- **Feel.** A tab bar pill you can hold and drag, swipeable pages, sheets that follow your finger and settle by momentum, and Back that swipes a screen out under your finger. Remove animations in Android turns the motion off.
- **Readability.** Text at 4.5:1 and controls at 3:1 contrast in every theme, and screen reader names on every icon button.

## Features

### Programs
Six programs: GZCLP (4 days, A1, B1, A2, B2), Bro split (5 days), Push, pull, legs (6 days), Upper, lower (4 days), Full body (3 days), and Chest, back, legs (3 days). GZCLP runs its T1, T2, and T3 progression with stage changes and resets. Every other program runs a 3-week cycle per exercise: 3×12, then 3×10 one step heavier, then 3×8 two steps heavier, then back to 3×12 at 5 lb over the last cycle's base if every heavy set reached 8.

The app logs a weight and reps for every set, shows plates per side and warm-ups, charts each exercise's top set and estimated 1RM, and runs a rest timer that vibrates with the screen off.

### Adaptive strength and calisthenics
- **Adaptive strength**: tracks estimated 1RM (Epley with RIR) across all logged sets with an asymmetric exponential moving average (α_up = 0.4, α_down = 0.15). Manual weight edits act as strong observations (α = 0.6). When an exercise is updated in one tier (e.g. T2 OHP), suggested weights propagate to sibling tiers (e.g. T1 OHP). A suggestion is one sentence with a Use button, or turn on "Auto-adapt weights" in Settings to apply it at session start (capped at a 10% drop).
- **Calisthenics**: 21 catalog exercises and variations (pull-ups, chin-ups, dips, push-ups, inverted rows, pistol squats, muscle-ups). Effective load is `factor × bodyweight + addedWeight` (1.0 for pull-ups, 0.95 for dips, 0.65 for push-ups, 0.5 for inverted rows, 0.8 for pistol squats). Progression adds reps up to 3×12, then 5 lb or the next harder variation. Charts show absolute e1RM and relative strength (× body weight).

### Open sessions and weekly volume
Tap Open session to train freely without advancing your program. Exercises prefill weights from the adaptive model and recalculate target reps when the weight changes. Weekly volume bars by muscle group (Chest, Back, Quads, Hamstrings, Shoulders, Arms, Core) run against weekly targets. At the end, the app asks whether the session counts toward your program.

### Cardio
Start one of 59 cardio activities, or one you type in, from a searchable list, or tap one of your last three. A big clock counts up, and an ongoing notification shows the time on the lock screen; the clock runs from the start time in the log, so locking the phone, closing the app, or rebooting keeps the session. Stop opens a sheet to fix the duration and add an optional distance, RPE, and notes. The Cardio page shows this week's total against a weekly goal (2 hr 30 min unless you change it), the last four weeks as bars, quick-start rows, and each session with its pace or speed. Log a session you forgot to start with Log past session, and tap any session to edit or delete it.

### History
History opens on a Monday to Sunday week you can page back through: lifting, cardio and stretching time with their split, the days you trained, sets by muscle group, each lift against the same slot last cycle, the body-weight average, and the meal count, each against the week before. Month shows a month of those days and its averages. All lists every workout and cardio session, newest first. Open a workout to see its sets, each with the time since the set before it. Tap Duration to type how long it really took, or pick its end time.

### Body weight, food and water
- **Body weight**: each morning's first weigh-in, a 7-day average, and a daily reminder. Each lift can chart its estimated 1RM as a multiple of body weight.
- **Photo food log**: take a photo of each meal or pick one from your gallery, saved at the photo's capture time. An on-device TensorFlow Lite classifier (Google AIY Food V1, Apache 2.0) pairs each photo with a rough per-serving calorie range by food category (hand-assigned, not from a nutrient database). It runs offline; photos never leave the phone.
- **Gallery export**: optionally save a copy of meal photos to `Pictures/Food` (MediaStore, no extra permissions). Deleting a meal removes the gallery copy. Photos in the gallery may be backed up by Google Photos or another backup service on your phone.
- **Water**: a cup you drag to fill, with a live wave, preset amounts, and pace reminders against the day's goal.

### Reminders
Weekday-aware reminders learn your habits with an EMA anchored to minutes since 4:00 AM (so midnight never wraps) and Bayesian shrinkage toward the all-days mean. They nudge you to weigh in, stretch, and log meals (up to 4 meal slots per weekday, 20 minutes after your usual time if no photo is logged). They use fixed times until at least 3 events are recorded.

### Your data
All data stays on the phone, except what you choose to write to Android Health Connect. The app mirrors its log to a folder you share with Syncthing. `docs/log-format.md` describes that log (version 2) for the desktop analysis app.

## Screenshots

Rendered by the Roborazzi screenshot test (`ScreenShots.kt`) from its demo data. Each screen in Tokyo Night with Performance motion, then in Liquid Glass with Loaded motion.

| Workout | Sets and rest | Stretching | Cardio |
| :---: | :---: | :---: | :---: |
| <img src="docs/images/performance/workout.webp" width="190" alt="Next workout"> | <img src="docs/images/performance/sets.webp" width="190" alt="A workout in progress with the rest timer"> | <img src="docs/images/performance/stretching.webp" width="190" alt="Stretching routines and library"> | <img src="docs/images/performance/cardio.webp" width="190" alt="Cardio: this week against the goal, four weeks of bars, quick start"> |
| <img src="docs/images/loaded/workout.webp" width="190" alt="Next workout in Liquid Glass"> | <img src="docs/images/loaded/sets.webp" width="190" alt="A workout in progress in Liquid Glass"> | <img src="docs/images/loaded/stretching.webp" width="190" alt="Stretching in Liquid Glass"> | <img src="docs/images/loaded/cardio.webp" width="190" alt="Cardio in Liquid Glass"> |
| **History: Week** | **History: Month** | **Muscle map** | **Exercise** |
| <img src="docs/images/performance/history-week.webp" width="190" alt="History week"> | <img src="docs/images/performance/history-month.webp" width="190" alt="History month calendar with a dot per kind of training"> | <img src="docs/images/performance/muscle-map.webp" width="190" alt="Exercises: muscle map of the week"> | <img src="docs/images/performance/exercise.webp" width="190" alt="Bench press: estimated 1RM chart and next time"> |
| <img src="docs/images/loaded/history-week.webp" width="190" alt="History week in Liquid Glass"> | <img src="docs/images/loaded/history-month.webp" width="190" alt="History month in Liquid Glass"> | <img src="docs/images/loaded/muscle-map.webp" width="190" alt="Muscle map in Liquid Glass"> | <img src="docs/images/loaded/exercise.webp" width="190" alt="Bench press in Liquid Glass"> |
| **Food** | **Water** | **Settings** | **Appearance** |
| <img src="docs/images/performance/food.webp" width="190" alt="Food: water intake and today's meal photos"> | <img src="docs/images/performance/water.webp" width="190" alt="Water: the cup, the day's ring, and the log"> | <img src="docs/images/performance/settings.webp" width="190" alt="Settings root list"> | <img src="docs/images/performance/appearance.webp" width="190" alt="Appearance: Motion, Theme, Gender and Build"> |
| <img src="docs/images/loaded/food.webp" width="190" alt="Food in Liquid Glass"> | <img src="docs/images/loaded/water.webp" width="190" alt="Water in Liquid Glass"> | <img src="docs/images/loaded/settings.webp" width="190" alt="Settings in Liquid Glass"> | <img src="docs/images/loaded/appearance.webp" width="190" alt="Appearance in Liquid Glass with Loaded chosen"> |

More themes: Jade, Dark, Light, Light with Liquid Glass, and Gruvbox (an Omarchy theme) with Liquid Glass.

| Jade | Dark | Light | Light, Liquid Glass | Gruvbox, Liquid Glass |
| :---: | :---: | :---: | :---: | :---: |
| <img src="docs/images/themes/jade.webp" width="150" alt="Workout in the Jade theme"> | <img src="docs/images/themes/dark.webp" width="150" alt="Workout in the Dark theme"> | <img src="docs/images/themes/light.webp" width="150" alt="Workout in the Light theme"> | <img src="docs/images/themes/light-glass.webp" width="150" alt="Workout in Light with Liquid Glass"> | <img src="docs/images/themes/gruvbox-glass.webp" width="150" alt="Cardio in Gruvbox with Liquid Glass"> |

The desktop analysis app (`desktop/`) reads the synced log:

<img src="docs/images/desktop.png" alt="Desktop analysis app: strength, weekly volume, stalls, sessions, rest, and body weight charts">

## Build

Requirements: JDK 21 and an Android SDK with `platforms;android-35` and `build-tools;35.0.0`. The Gradle wrapper downloads Gradle itself.

```bash
eval "$(mise env -s bash)"            # sets ANDROID_HOME on this machine
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew test                         # progression engine and log tests
./gradlew assembleDebug                # app/build/outputs/apk/debug/app-debug.apk
```

Debug builds install as Repsilon test (`dev.gzclp.tracker.debug`) next to the release app, with their own data (`./gradlew installDebug`). To try one with your log, copy `gzclp-log.jsonl` from the sync folder into a new folder and pick that folder under Restore log. Picking the real sync folder makes the debug build write its test workouts into it.

Screenshots of every screen in every theme:

```bash
./gradlew recordRoborazziDebug -PgzclpShots=/path/to/shots
```

## Install for friends

No cable or computer needed. The app needs Android 12 or later.

1. Install [Obtainium](https://github.com/ImranR98/Obtainium/releases) (download the APK from its releases page and open it; allow installs from your browser when asked).
2. In Obtainium, tap Add App, paste `https://github.com/ericxu1070/workout-tracker`, and tap Add. Then tap Install.
3. Obtainium checks for new versions and offers each update; tap Update to install it.

The app was called GZCLP before v2.2, then Plateline. If you installed it under either name, keep your setup: the next update installs over it under the new name, Repsilon, with your log intact.

Without Obtainium, download the APK from [Releases](https://github.com/ericxu1070/workout-tracker/releases) and open it; you then install updates by hand the same way.

On first launch, enter your 5-rep maxes and pick a program (see First run below). The sync folder and Health Connect are optional: without them everything stays on your phone and works the same. A sync folder only matters if you want a copy of your log on a computer through Syncthing.

## Release

Pushing a tag like `v2.1` runs `.github/workflows/release.yml`: it builds the release APK signed with the release key, sets `versionName` from the tag and `versionCode` from the commit count (so every later tag on `main` is a higher version), and publishes a GitHub Release with the APK attached. Merging to `main` publishes nothing.

```bash
git tag v2.1 origin/main && git push origin v2.1
```

The key lives in `~/.local/share/workout-tracker-release/` (keystore, password, notes) and in the repository's Actions secrets `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD`. Back that folder up: Android installs an update only when it is signed with the same key, so a lost key means everyone uninstalls and starts over. To publish into another repo, set the repository variable `RELEASE_REPO` to `owner/name` and the secret `RELEASE_TOKEN` to a token that can write that repo's contents.

To build a signed APK locally, set `RELEASE_KEYSTORE` to the keystore path and the three other variables to the same values, then run `./gradlew assembleRelease`. Without `RELEASE_KEYSTORE`, release builds are unsigned and debug builds are unchanged.

## Install from a computer

Enable USB debugging on the phone, connect it, then:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.gzclp.tracker/.MainActivity
```

## First run

1. Enter a 5-rep max for each main lift and pick a program. GZCLP starts T1 at 85% and T2 at 65%; the cycle programs start week 1 at 65%. All rounded to 5 lb. Other exercises ask for a starting weight the first time they come up.
2. In Settings, choose the sync folder (for example `Documents/GZCLP`) and share it with Syncthing. The app writes `gzclp-log.jsonl` there. If the laptop also syncs `omarchy-theme.toml` into that folder, the Laptop theme in Settings follows it; the app reads that file and never writes it.
3. Allow notifications when asked, so the rest timer can alert you with the screen off and the 7:00 AM weigh-in reminder can reach you.

After a reinstall, tap Restore log on the first screen and pick the same folder.

To send workouts, cardio, and weigh-ins to Health Connect, turn on Health Connect in Settings and allow access. From then on, each finished workout becomes a strength-training session (one segment per set with its reps, every set's weight in the notes), each finished cardio session becomes an exercise session of its activity's type (distance, pace or speed, and RPE in the notes), and each weigh-in becomes a weight record; editing or deleting a cardio session or weigh-in updates Health Connect too. The app only writes and never reads. Workouts, cardio, and weigh-ins from before you turned it on are not sent. Without Health Connect, or with access denied, everything else works the same.

In Settings you can switch programs (history and each exercise's progress carry over), add or remove exercises per workout, and change the cycle steps (5 lb upper body, 10 lb lower body, 5 lb per new cycle by default), and set your bar and plates (a 45 lb bar; pairs of 45, 35, 25, 10, 5, and 2.5 lb plates by default). Barbell lifts round each target to the nearest weight those plates make, and every set shows the plates for one side. Progression still counts from the program's own weight.

Version 2 reads only version 2 logs. On first launch it sets an older local log aside as `gzclp-log.jsonl.old`; uninstall first for a clean start.

## Layout

- `engine/` holds the program table, the GZCLP progression, the 3-week cycle, the adaptive strength model, and calisthenics catalogs as pure Kotlin.
- `log/` holds the event log, its JSON codec, the state fold, commands that turn taps into events, calisthenics effective load scoring, per-exercise stats, and weekly volume tracking.
- `sync/` mirrors the log into the shared folder.
- `rest/` holds the rest alarm and its notifications.
- `cardio/` holds the notification for the cardio session in progress.
- `remind/` holds weekday-aware EMA habit reminders for weigh-in, meals, stretching, and water.
- `food/` keeps meal photos in app-private storage, gallery export via MediaStore, and on-device TFLite food classification and calorie estimation.
- `stretch/` holds the procedural 2D stick-figure animation engine, 52 keyframed stretching moves, routines, and audio chimes.
- `health/` turns finished workouts, cardio sessions, and weigh-ins into Health Connect records, and holds the privacy note Health Connect shows.
- `ui/` holds the Compose screens (workouts, open sessions, water tracker canvas, guided stretching player, history, food log, settings) and the four color themes.
- `desktop/` holds the standalone Qt6/QML desktop analysis app for strength, volume, stalls, sessions, cardio, rest, body weight, water, and stretching metrics.
