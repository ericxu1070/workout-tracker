# Handoff: fix the app errors in Plateline before the next release

You are taking over the Plateline Android app (`ericxu1070/workout-tracker`, branch `integration/adaptive`, PR #26) from a session that ended on 2026-11-25. Eric found errors in the app while testing it and wants them fixed before any release.

## Goal

- As of 2026-11-25, Eric wants the app errors found that day fixed on `integration/adaptive` before the next release. They are not written down anywhere, including the vault notes, the spec, and the old transcript, so step 1 is to ask Eric.
- You are done when each error is reproduced on the phone, fixed at its root cause, and covered by a test where a cheap one exists. All tests must pass, the fixes must be committed and pushed, and the PR #26 description and the vault project log must be updated.
- Out of scope unless Eric asks: tagging a release, and the form viewer fixes listed in the vault (see Open questions).

## Done so far

- `32d56de` adds the 3D form viewer for every exercise and stretch, plus fixes from a code review. `FormTest` (10 tests) checks floor contact, limb reach, balance, foot slide, framing, and the bar path.
- `43230aa` makes debug builds install beside the release app as Plateline test (`dev.gzclp.tracker.debug`). It does this with `applicationIdSuffix` and a `${appLabel}` manifest placeholder in `app/build.gradle.kts`. The README build section explains it.
- Tests at `43230aa`: `./gradlew testDebugUnitTest` passed 213 of 213 and `./gradlew assembleDebug` built. `desktop/bin/test` passed 12 of 12, and the desktop code has not changed since `bfd070d`.
- The PR #26 title and description were rewritten from the code at `43230aa`. The description opens with "Not ready for a release." Keep that line until Eric says otherwise.
- Plateline test is installed on the phone and opens to the Starting weights screen. Its log is empty unless Eric has restored it since (unverified).

## Where things are

- Repo: work in the clone or worktree that firstmate gave you, on `integration/adaptive`. Use Eric's checkout, `/home/exu23/Projects/workout-tracker`, only if firstmate gave you none. The branch head is the commit that adds this file, on top of `43230aa`, and both are pushed. Set up a fresh clone once:

	```bash
	eval "$(mise env -s bash)"
	echo "sdk.dir=$ANDROID_HOME" > local.properties
	```

- Spec: Eric's original feature request is `/home/exu23/Xubot/Workout tracker changes and problems.md`. Check each reported error against it.
- Vault notes: `/home/exu23/Xubot/projects/Workout Tracker/Workout Tracker - Project Log.md` covers status, phone testing, and open tasks. `/home/exu23/Xubot/projects/Workout Tracker/Workout Tracker - Form Viewer Fixes.md` lists the renderer fixes.
- Phone: Samsung Galaxy S21 (`SM-G991U1`, Android 15) on Wireless debugging, already paired with this laptop. On 2026-11-25 it was connected at `192.168.0.19:43909`. The port changes, so when `adb devices` does not list the phone, ask Eric for the "IP address & Port" shown on the Wireless debugging screen.

	```bash
	adb connect 192.168.0.19:<port>
	adb devices -l
	./gradlew installDebug        # about 2 min, installs Plateline test only
	adb shell am start -n dev.gzclp.tracker.debug/dev.gzclp.tracker.MainActivity
	adb exec-out screencap -p > <scratchpad>/phone.png
	adb logcat -d -b crash        # crashes since boot
	```

- The release app on the phone is Plateline 2.6 (`dev.gzclp.tracker`, versionCode 33), installed from Obtainium. It holds Eric's real workout log.
- A copy of the real log is at `/sdcard/Download/Plateline test/gzclp-log.jsonl` on the phone, made 2026-11-25 from the mirror written 2026-11-24 12:47. To load it in Plateline test, tap Restore log, then pick Download › Plateline test.
- Nothing runs in the background, and nothing bills.
- Full context, only if needed: the previous session's transcript is `/home/exu23/.claudep/projects/-home-exu23-Projects-workout-tracker/9dc3c3de-3aa5-42d8-9053-8101a92b17d7.jsonl`.

## Settled; don't re-derive

- The release is signed with the release key, so a debug APK cannot replace it. That is why debug builds carry the `.debug` suffix.
- To load a newer log into Plateline test, clear its data first (Settings › Apps › Plateline test › Storage › Clear data), copy the log again, then tap Restore log. A copy alone gets overwritten, because `Mirror.sync` rewrites the folder file when its length differs from what it last wrote. Settings also hides Restore from folder once a program has started.
- The phone advertises only its pairing service over mDNS (watched for 3 minutes), so `adb mdns services` never shows the connect port.
- Form viewer centering: moving the camera sphere's center to the best height made figures a median 2.3% larger over 69 motions (max 12.7%). It also moved the aim point up to 0.32 m off the body and pushed the squat's floor shadow to the card edge, so it was dropped. The bounding-box center stays.
- `calories.json` went from 1,989 to 570 entries in `32d56de`. All 1,419 removed entries were 400-650 kcal, the same as `DEFAULT` in `food/Nutrition.kt`, so no estimate changed. 1,465 of the 2,024 labels in `labels.txt` have no entry and get that default.
- The 7 stretch routines are in `stretch/StretchRoutine.kt`. An older PR description named routines that do not exist.

## What to do, in order

1. Ask Eric for each error: the screen, what Eric did, what Eric expected, and whether it happened in the release app or in Plateline test. Stop until Eric answers. Check: you have a list with repro steps.
2. Build and test your checkout with `./gradlew testDebugUnitTest`. Check: 213 tests, 0 failures.
3. Reproduce each error in Plateline test on the phone, using the commands above. If an error needs Eric's data, load the log copy first, or refresh it as described under Settled. If an error shows only in the release app, ask Eric to reproduce it there while you watch `adb logcat`. Check: you see the error yourself, in a screenshot or in `logcat`.
4. Fix each error at its root cause. When a JVM unit test can show the bug (Robolectric is set up), add one under `app/src/test/java/dev/gzclp/tracker/`. Then run `timeout 590 ./gradlew recordRoborazziDebug assembleDebug --console=plain -q` (about 4 min). It runs every unit test and renders screenshots into `app/build/shots/`, which git ignores. Run `cd desktop && bin/test` too if you changed `desktop/`. Check: every test passes.
5. Install on the phone and repeat each repro. Check: each error is gone. Then commit and push to `integration/adaptive`. A push releases nothing, because only a `v*` tag does.
6. Make the PR #26 description match the code. List anything you could not fix under Known gaps, and keep the "Not ready for a release" line until Eric says the app is ready. In the vault project log, record each error and its status under Status and Open tasks. Ask Eric before you tag any release.

## Open questions

- What the errors are (step 1).
- After the errors, which form viewer fixes from the vault list come next, and whether PR #26 becomes a draft.
- When to release, and as which version. Pushing a `v*` tag runs `.github/workflows/release.yml`.

## Rules

- Never uninstall, clear, or `pm clear` the release package `dev.gzclp.tracker`. Its data is Eric's only workout log on the phone. Touch only `dev.gzclp.tracker.debug`.
- In Plateline test, never pick Documents › GZCLP, either from Restore log or from Settings › Sync folder. That folder is the real log's Syncthing mirror, and the test app would write its test log into it.
- Never push a `v*` tag without Eric's go-ahead. It publishes a release that friends' phones install through Obtainium.
- Run one Gradle build at a time.
- Before you edit a vault note, read `/home/exu23/Xubot/.claude/note-standards.md`. After the edit, add an entry at the top of `/home/exu23/Xubot/logs/session-history.md` and a line under "Recent sessions" in `/home/exu23/Xubot/memory.md`. Do not commit the vault.
- Commits, attribution, and writing style follow `/home/exu23/Projects/claude-config/CLAUDE.md`.
- Delete this file in the last commit before PR #26 merges.
