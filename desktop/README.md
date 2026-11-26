# Repsilon desktop (gzclp-desk)

A desktop dashboard for the Repsilon phone app. It reads the log the phone mirrors to `~/GZCLP/gzclp-log.jsonl` through Syncthing and redraws whenever Syncthing replaces the file. It never writes to the folder.

Four tabs, switched from a floating segmented control at the top:

1. Lifting. Estimated 1RM per main lift and calisthenics movement (Epley), using effective load (bodyweight factor × body weight + added weight) for bodyweight exercises, from GZCLP T1 AMRAP sets and the best set of each cycle-program or custom-split session, with the latest value and its multiple of body weight at each line's end. Weekly tonnage or reps, filtered by tier and lift. GZCLP stage changes and resets, and repeated cycles, per lift on one timeline. Workout length, workouts per week, and median rest against the timer's target by tier.
2. Cardio. Minutes per week, and sessions, time, and distance per activity over the last 8 weeks.
3. Stretching. Sessions per week, and sessions and time per routine over the last 8 weeks, with custom routines by their own names.
4. Body. The morning weigh-in and daily water, each with its 7-day average.

Hover a chart to read the week or day under the pointer; click to pin it, click again to let go.

The colors are the phone's palette for the live Omarchy theme, in Liquid Glass (a soft wallpaper glow under a blurred, frosted tab bar whose pill turns to clear glass while held) or flat. Press G to switch; the choice and the last tab are remembered. The bar's blur is real, but it does not bend the page behind it the way the phone's lens does.

The log format is the contract in [`../docs/log-format.md`](../docs/log-format.md). The colors follow the live Omarchy theme.

## Build and run

Needs `qt6-base`, `qt6-declarative`, `qmake6`, and `make`.

```sh
bin/build                          # builds build/gzclp-desk
build/gzclp-desk                   # reads ~/GZCLP/gzclp-log.jsonl
build/gzclp-desk path/to/log.jsonl # reads another log
bin/test                           # parser and metrics tests, on the doc example and a generated fixture
cd pkgbuild && makepkg -f          # builds the package; install it with pacman -U
```

`tests/make_fixture.py OUT.jsonl` writes a deterministic log with eight weeks of lifting (GZCLP, a cycle program, and a custom split) and cardio, and the reader's edge cases.

## Shortcuts

| Key | Action |
|---|---|
| 1, 2, 3, 4 | Lifting, Cardio, Stretching, Body |
| G | Liquid Glass or flat |
| Up, Down, J, K | Scroll |
| Space, Shift+Space, Page Up, Page Down | Scroll a page |
| Home, End | Top, bottom |
| Left, Right | Change a focused filter |
| Tab | Move between filters |
| ? | Show shortcuts |
| Q | Quit |
