#!/usr/bin/env bash
# One emulator per worktree, at most EMU_SLOTS running at once (RAM), so parallel agents never share a device.
#   tools/emu.sh start   boot this worktree's AVD (waits for a free slot), print its serial
#   tools/emu.sh serial  print the serial of this worktree's running emulator
#   tools/emu.sh stop    shut it down and free the slot
#   tools/emu.sh view    watch every worktree emulator, view-only (scrcpy)
set -euo pipefail
sdk="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' "$(git rev-parse --show-toplevel)/local.properties")}"
wt="$(basename "$(git rev-parse --show-toplevel)")"
avd="wt-$(printf '%s' "$wt" | tr -c 'a-zA-Z0-9_.-' '_')"
slots="${EMU_SLOTS:-2}"
lockdir="${XDG_RUNTIME_DIR:-/tmp}/repsilon-emu"
state="$(git rev-parse --show-toplevel)/.gradle/emu-serial"
mkdir -p "$lockdir" "$(dirname "$state")"

# A serial alone is not proof: once this worktree's emulator dies, another worktree's can take its port.
ours() { [ "$(adb -s "$1" emu avd name 2>/dev/null | head -1 | tr -d '\r')" = "$avd" ]; }
running() { [ -f "$state" ] && ours "$(cat "$state")"; }

case "${1:-}" in
  start)
    if running; then cat "$state"; exit 0; fi
    if ! "$sdk/emulator/emulator" -list-avds | grep -qx "$avd"; then
      echo no | "$sdk"/cmdline-tools/*/bin/avdmanager create avd -n "$avd" \
        -k "system-images;android-35;google_apis_playstore;x86_64" -d pixel_8 >/dev/null
    fi
    while :; do
      for i in $(seq 0 $((slots - 1))); do
        port=$((5560 + 2 * i))
        # The emulator holds the slot's lock for as long as it runs, so a crash frees the slot too.
        if flock -n "$lockdir/slot$i" true; then
          setsid flock -n "$lockdir/slot$i" "$sdk/emulator/emulator" -avd "$avd" -port "$port" \
            -no-window -no-audio -no-snapshot -no-boot-anim -memory 2048 >"$lockdir/$avd.log" 2>&1 &
          sleep 2
          kill -0 $! 2>/dev/null || continue # another worktree took the slot first
          serial="emulator-$port"
          echo "$serial" >"$state"
          for _ in $(seq 1 180); do
            ours "$serial" && [ "$(adb -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && { echo "$serial"; exit 0; }
            sleep 5
          done
          pkill -f "emulator.*-avd $avd -port" 2>/dev/null || true; rm -f "$state" # free the slot for others
          echo "Emulator $avd did not boot in 15 min; see $lockdir/$avd.log" >&2; exit 1
        fi
      done
      echo "All $slots emulator slots busy; waiting..." >&2
      sleep 20
    done ;;
  serial) running && cat "$state" || { echo "No emulator for $wt" >&2; exit 1; } ;;
  view) # a view-only window per running worktree emulator, titled by its worktree; clicks never reach the agent's device
    for s in $(adb devices | awk '/^emulator-/{print $1}'); do
      n="$(adb -s "$s" emu avd name 2>/dev/null | head -1 | tr -d '\r')"
      case "$n" in wt-*) setsid scrcpy -s "$s" --no-control --no-audio --window-title "${n#wt-} ($s)" -m 1024 >/dev/null 2>&1 & ;; esac
    done ;;
  stop) if running; then adb -s "$(cat "$state")" emu kill >/dev/null; fi; rm -f "$state" ;;
  *) sed -n '3,6p' "$0" >&2; exit 2 ;;
esac
