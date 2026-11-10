# Training (gzclp-desk)

A desktop dashboard for the GZCLP phone app. It reads the log the phone mirrors to `~/GZCLP/gzclp-log.jsonl` through Syncthing and redraws whenever Syncthing replaces the file. It never writes to the folder.

One scrolling page, top to bottom:

1. Strength. Estimated 1RM per main lift (Epley), from GZCLP T1 AMRAP sets and the best set of each cycle-program session, with the latest value and its multiple of body weight at each line's end.
2. Weekly volume. Tonnage or reps per ISO week, filtered by tier and lift.
3. Stalls and resets. GZCLP stage changes and resets, and repeated cycles, per lift on one timeline.
4. Sessions. Duration per workout and workouts per week.
5. Rest. Median rest taken against the timer's target, by the tier of the set before.
6. Body weight. The day's morning weigh-in and the 7-day average.

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

`tests/make_fixture.py OUT.jsonl` writes a deterministic log with eight weeks of training and the reader's edge cases.

## Shortcuts

| Key | Action |
|---|---|
| Up, Down, J, K | Scroll |
| Space, Shift+Space, Page Up, Page Down | Scroll a page |
| Home, End | Top, bottom |
| Left, Right | Change a focused filter |
| Tab | Move between filters |
| ? | Show shortcuts |
| Q | Quit |
