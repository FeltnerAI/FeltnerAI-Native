#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE_ID="${ANDROID_PACKAGE_ID:-ai.feltner.nativeapp}"

find_android_sdk() {
  for sdk in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
    if [[ -n "$sdk" && -x "$sdk/platform-tools/adb" ]]; then
      echo "$sdk"
      return 0
    fi
  done
  return 1
}

ANDROID_SDK="$(find_android_sdk || true)"
if [[ -z "${ANDROID_SDK:-}" ]]; then
  echo "Could not find Android SDK. Set ANDROID_HOME or ANDROID_SDK_ROOT." >&2
  exit 1
fi

ADB="$ANDROID_SDK/platform-tools/adb"
EMULATOR="$ANDROID_SDK/emulator/emulator"

device_count() {
  "$ADB" devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }'
}

wait_for_boot() {
  "$ADB" wait-for-device
  local booted=""
  for _ in $(seq 1 90); do
    booted="$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    if [[ "$booted" == "1" ]]; then
      return 0
    fi
    sleep 2
  done
  echo "Timed out waiting for Android device to boot." >&2
  exit 1
}

if [[ "$(device_count)" == "0" ]]; then
  if [[ ! -x "$EMULATOR" ]]; then
    echo "No connected Android device and emulator tool was not found." >&2
    exit 1
  fi

  AVD_NAME="${ANDROID_AVD:-$("$EMULATOR" -list-avds | head -n 1)}"
  if [[ -z "$AVD_NAME" ]]; then
    echo "No connected Android device and no Android Virtual Devices exist." >&2
    exit 1
  fi

  echo "Starting Android emulator: $AVD_NAME"
  nohup "$EMULATOR" -avd "$AVD_NAME" >/tmp/feltner-native-android-emulator.log 2>&1 &
  wait_for_boot
fi

"$ROOT_DIR/scripts/gradle-with-jdk.sh" :androidApp:installDebug
"$ADB" shell monkey -p "$PACKAGE_ID" -c android.intent.category.LAUNCHER 1 >/dev/null
echo "Launched $PACKAGE_ID"
