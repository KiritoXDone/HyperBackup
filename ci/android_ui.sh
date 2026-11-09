#!/usr/bin/env bash
set -euo pipefail
collect() {
  mkdir -p work/ui-evidence
  adb pull /sdcard/Android/data/io.github.kiritoxdone.hyperbackup/files/ui-evidence work/ui-evidence/ >/dev/null 2>&1 || true
  adb logcat -d >work/ui-evidence/logcat.txt 2>&1 || true
}
trap collect EXIT
bash ci/gradle.sh -PciUi=true -Pandroid.testInstrumentationRunnerArguments.displayScenarios=true :app:connectedDebugAndroidTest --console=plain
