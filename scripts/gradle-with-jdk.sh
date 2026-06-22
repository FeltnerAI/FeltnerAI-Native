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

  if [[ "$(uname)" == "Darwin" ]]; then
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
  else
    for candidate in \
      /usr/lib/jvm/java-21-openjdk* \
      /usr/lib/jvm/java-17-openjdk* \
      /usr/lib/jvm/temurin-21* \
      /usr/lib/jvm/temurin-17*; do
      if use_java_home "$candidate"; then
        return 0
      fi
    done
  fi

  if command -v java >/dev/null 2>&1; then
    local current_major
    current_major="$(java -version 2>&1 | awk -F '"' '/version/ { split($2, parts, "."); print parts[1]; exit }')"
    if [[ "$current_major" =~ ^[0-9]+$ ]] && (( current_major >= 26 )); then
      echo "Gradle needs JDK 17 or 21; current java is $current_major. Set JAVA_HOME to a compatible JDK." >&2
      exit 1
    fi
  fi
}

configure_android_home() {
  local candidate

  if [[ -n "${ANDROID_HOME:-}" && -d "$ANDROID_HOME" ]]; then
    return 0
  fi
  if [[ -n "${ANDROID_SDK_ROOT:-}" && -d "$ANDROID_SDK_ROOT" ]]; then
    export ANDROID_HOME="$ANDROID_SDK_ROOT"
    return 0
  fi

  for candidate in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
    if [[ -d "$candidate" ]]; then
      export ANDROID_HOME="$candidate"
      export ANDROID_SDK_ROOT="$candidate"
      return 0
    fi
  done
}

configure_java_home
configure_android_home
cd "$ROOT_DIR"
exec ./gradlew "$@"
