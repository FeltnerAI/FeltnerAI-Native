#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

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
    if (( major >= 17 && major < 26 )); then
      export JAVA_HOME="$java_home"
      export PATH="$JAVA_HOME/bin:$PATH"
      echo "Using JAVA_HOME: $JAVA_HOME"
      return 0
    fi
  fi

  return 1
}

configure_java_home() {
  local candidate
  local requested_version

  if [[ -n "${JAVA_HOME:-}" ]] && use_java_home "$JAVA_HOME"; then
    return 0
  fi

  for requested_version in 21 17; do
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
}

if [[ "$(uname)" = "Darwin" ]]; then
  configure_java_home
fi

cd "$ROOT_DIR"
./gradlew :shared:embedAndSignAppleFrameworkForXcode
