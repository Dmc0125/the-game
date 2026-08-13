#!/usr/bin/env bash
set -euo pipefail

APP_ID="shapes.game"

adb logcat -c
./gradlew installDebug
adb shell monkey -p "$APP_ID" 1

PID="$(adb shell pidof "$APP_ID")"
adb logcat -v color --pid="$PID"
