#!/usr/bin/env bash
# Prepares a fresh worktree (Orca runs this from orca.yaml): local.properties, then a test compile.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

main="$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")"
if [ ! -f local.properties ]; then
  if [ -f "$main/local.properties" ]; then
    cp "$main/local.properties" local.properties
  else
    sdk="${ANDROID_HOME:-$(mise where android-sdk 2>/dev/null || true)}"
    [ -n "$sdk" ] || { echo "No Android SDK: set ANDROID_HOME" >&2; exit 1; }
    echo "sdk.dir=$sdk" > local.properties
  fi
fi

# Warms Gradle's caches so the agent's first build is quick; capped so several worktrees fit in RAM.
./gradlew -q -Dorg.gradle.jvmargs=-Xmx2g --max-workers=3 compileDebugUnitTestKotlin

echo "Ready. Emulator for this worktree: tools/emu.sh start (prints its serial), tools/emu.sh stop when done."
