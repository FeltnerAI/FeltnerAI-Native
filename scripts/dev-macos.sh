#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_PATH="${MACOS_PROJECT_PATH:-$ROOT_DIR/appleApp/FeltnerAINative.xcodeproj}"
SCHEME="${MACOS_SCHEME:-FeltnerAI-Native-macOS}"
CONFIGURATION="${MACOS_CONFIGURATION:-Debug}"
DERIVED_DATA_PATH="${MACOS_DERIVED_DATA_PATH:-$ROOT_DIR/appleApp/build/DerivedData}"
APP_NAME="${MACOS_APP_NAME:-FeltnerAI-Native.app}"

if [[ "$(uname)" != "Darwin" ]]; then
  echo "dev-macos requires macOS with Xcode installed." >&2
  exit 1
fi

if ! command -v xcodebuild >/dev/null 2>&1; then
  echo "dev-macos requires xcodebuild." >&2
  exit 1
fi

xcodebuild \
  -project "$PROJECT_PATH" \
  -scheme "$SCHEME" \
  -configuration "$CONFIGURATION" \
  -destination "platform=macOS" \
  -derivedDataPath "$DERIVED_DATA_PATH" \
  build \
  CODE_SIGNING_ALLOWED="${MACOS_CODE_SIGNING_ALLOWED:-NO}"

APP_PATH="${MACOS_APP_PATH:-$DERIVED_DATA_PATH/Build/Products/$CONFIGURATION/$APP_NAME}"
if [[ ! -d "$APP_PATH" ]]; then
  APP_PATH="$(find "$DERIVED_DATA_PATH/Build/Products" -name "$APP_NAME" -type d -print -quit 2>/dev/null || true)"
fi

if [[ -z "${APP_PATH:-}" || ! -d "$APP_PATH" ]]; then
  echo "Could not find the built macOS app under $DERIVED_DATA_PATH/Build/Products." >&2
  exit 1
fi

open "$APP_PATH"
echo "Opened $APP_PATH"
