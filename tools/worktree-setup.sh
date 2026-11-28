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
# Not fatal: Orca starts the agent beside setup, so its half-made edits can break this compile.
./gradlew -q -Dorg.gradle.jvmargs=-Xmx2g --max-workers=3 compileDebugUnitTestKotlin ||
  echo "Warm-up compile failed (fine if an agent is mid-edit); the worktree is ready." >&2

echo "Ready. Emulator for this worktree: tools/emu.sh start (prints its serial), tools/emu.sh stop when done."
