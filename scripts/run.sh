#!/usr/bin/env bash
set -euo pipefail

APP_ID="shapes.game"
APK="app/build/outputs/apk/debug/app-debug.apk"

DEVICE_SERIAL="$(
  adb devices |
    awk 'NR > 1 && $NF == "device" {
        sub(/[[:space:]]+device[[:space:]]*$/, "")
        print
        exit
    }'
)"

if [[ -z "$DEVICE_SERIAL" ]]; then
  echo "No authorized ADB device found." >&2
  exit 1
fi

echo "Using device: $DEVICE_SERIAL"

adb -s "$DEVICE_SERIAL" logcat -c # clear logcat

# build
LOG_DEBUG=false
if [[ $# -gt 0 && "$1" == "--log-debug" ]]; then
  LOG_DEBUG=true
fi

ASSEMBLE_ARGS="-PlogDebug=$LOG_DEBUG"
echo "Running assemble with: $ASSEMBLE_ARGS"
./gradlew assembleDebug $ASSEMBLE_ARGS

adb -s "$DEVICE_SERIAL" install -r "$APK" # install on device
adb -s "$DEVICE_SERIAL" shell am force-stop "$APP_ID" 1
adb -s "$DEVICE_SERIAL" shell am start -n "$APP_ID/shapes.android.MainActivity"

# wait for app to start and get PID

PID=""
for _ in {1..50}; do
  PID="$(
    (adb -s "$DEVICE_SERIAL" shell pidof "$APP_ID" 2>/dev/null || true) |
      tr -d '\r' |
      awk '{ print $1 }'
  )"

  if [[ -n "$PID" ]]; then
    break
  fi

  sleep 0.1
done

if [[ -z "$PID" ]]; then
  echo "Timed out waiting for $APP_ID to start on $DEVICE_SERIAL." >&2
  exit 1
fi

adb -s "$DEVICE_SERIAL" logcat -v color --pid="$PID"
