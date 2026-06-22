#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_PATH="${IOS_PROJECT_PATH:-$ROOT_DIR/appleApp/FeltnerAINative.xcodeproj}"
SCHEME="${IOS_SCHEME:-FeltnerAI-Native}"
CONFIGURATION="${IOS_CONFIGURATION:-Debug}"
BUNDLE_ID="${IOS_BUNDLE_ID:-ai.feltner.nativeapp}"
APP_NAME="${IOS_APP_NAME:-FeltnerAI-Native.app}"
DERIVED_DATA_PATH="${IOS_DERIVED_DATA_PATH:-$ROOT_DIR/appleApp/build/DerivedData}"

if [[ "$(uname)" != "Darwin" ]]; then
  echo "dev-ios requires macOS with Xcode installed." >&2
  exit 1
fi

for tool in xcodebuild xcrun /usr/bin/python3; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "dev-ios requires $tool." >&2
    exit 1
  fi
done

java_major() {
  local java_home="$1"
  local version
  local major

  version="$("$java_home/bin/java" -version 2>&1 | awk -F '"' '/version/ { print $2; exit }')"
  if [[ "$version" == 1.* ]]; then
    major="${version#1.}"
    major="${major%%.*}"
  else
    major="${version%%.*}"
  fi

  if [[ "$major" =~ ^[0-9]+$ ]]; then
    echo "$major"
  else
    echo "0"
  fi
}

use_java_home() {
  local java_home="$1"
  local major

  if [[ -x "$java_home/bin/java" ]]; then
    major="$(java_major "$java_home")"
    if (( major < 17 || major >= 26 )); then
      return 1
    fi

    export JAVA_HOME="$java_home"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo "Using JAVA_HOME: $JAVA_HOME"
    return 0
  fi

  return 1
}

configure_java_home() {
  local current_major
  local candidate
  local requested_version

  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    current_major="$(java_major "$JAVA_HOME")"
    if (( current_major >= 17 && current_major < 26 )); then
      return 0
    fi
  fi

  for requested_version in 17 21; do
    if candidate="$(/usr/libexec/java_home -v "$requested_version" 2>/dev/null)" && use_java_home "$candidate"; then
      return 0
    fi
  done

  for candidate in \
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
    "/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home"; do
    if use_java_home "$candidate"; then
      return 0
    fi
  done

  if command -v java >/dev/null 2>&1; then
    current_major="$(java -version 2>&1 | awk -F '"' '/version/ { split($2, parts, "."); print parts[1]; exit }')"
    if [[ "$current_major" =~ ^[0-9]+$ ]] && (( current_major >= 26 )); then
      echo "Warning: Gradle may fail under Java $current_major. Install JDK 17 or set JAVA_HOME." >&2
    fi
  fi
}

configure_java_home

select_simulator() {
  /usr/bin/python3 <<'PY'
import json
import os
import subprocess
import sys

requested = os.environ.get("IOS_SIMULATOR", "").strip()
raw = subprocess.check_output(
    ["xcrun", "simctl", "list", "devices", "available", "-j"],
    text=True,
)
devices_by_runtime = json.loads(raw).get("devices", {})
iphones = []

for runtime, devices in devices_by_runtime.items():
    if "iOS" not in runtime:
        continue
    for device in devices:
        if not device.get("isAvailable", False):
            continue
        if not device.get("name", "").startswith("iPhone"):
            continue
        iphones.append(device)

if requested:
    for device in iphones:
        if requested in (device.get("name"), device.get("udid")):
            print(f"{device['udid']}\t{device['name']}")
            sys.exit(0)
    print(f'No available iPhone simulator matched "{requested}".', file=sys.stderr)
    sys.exit(1)

booted = next((device for device in iphones if device.get("state") == "Booted"), None)
selected = booted or (iphones[0] if iphones else None)
if selected is None:
    print(
        "No available iPhone simulators found. Install one from Xcode > Settings > Platforms.",
        file=sys.stderr,
    )
    sys.exit(1)

print(f"{selected['udid']}\t{selected['name']}")
PY
}

SIMULATOR_INFO="$(select_simulator)"
SIMULATOR_UDID="${SIMULATOR_INFO%%$'\t'*}"
SIMULATOR_NAME="${SIMULATOR_INFO#*$'\t'}"

boot_simulator() {
  open -a Simulator --args -CurrentDeviceUDID "$SIMULATOR_UDID"
  xcrun simctl boot "$SIMULATOR_UDID" >/dev/null 2>&1 || true
  xcrun simctl bootstatus "$SIMULATOR_UDID" -b
}

echo "Using iOS Simulator: $SIMULATOR_NAME ($SIMULATOR_UDID)"
boot_simulator

xcodebuild \
  -project "$PROJECT_PATH" \
  -scheme "$SCHEME" \
  -configuration "$CONFIGURATION" \
  -destination "platform=iOS Simulator,id=$SIMULATOR_UDID" \
  -derivedDataPath "$DERIVED_DATA_PATH" \
  build

APP_PATH="${IOS_APP_PATH:-$DERIVED_DATA_PATH/Build/Products/$CONFIGURATION-iphonesimulator/$APP_NAME}"
if [[ ! -d "$APP_PATH" ]]; then
  APP_PATH="$(find "$DERIVED_DATA_PATH/Build/Products" -name "*.app" -type d -print -quit 2>/dev/null || true)"
fi

if [[ -z "${APP_PATH:-}" || ! -d "$APP_PATH" ]]; then
  echo "Could not find the built .app under $DERIVED_DATA_PATH/Build/Products." >&2
  exit 1
fi

boot_simulator
xcrun simctl terminate "$SIMULATOR_UDID" "$BUNDLE_ID" >/dev/null 2>&1 || true
xcrun simctl uninstall "$SIMULATOR_UDID" "$BUNDLE_ID" >/dev/null 2>&1 || true
xcrun simctl install "$SIMULATOR_UDID" "$APP_PATH"
xcrun simctl launch "$SIMULATOR_UDID" "$BUNDLE_ID"
