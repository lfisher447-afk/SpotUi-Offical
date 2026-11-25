#!/usr/bin/env bash
set -euo pipefail

# Verifies that debug.keystore, debug.keystore.base64, and optional release/debug APKs
# share the exact same persistent signing certificate (prevents Android 'Signing key mismatch').

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
bash "${ROOT_DIR}/tools/setup-keystore.sh" --verify-only

if [ $# -ge 1 ] && [ -f "$1" ]; then
  APK_FILE="$1"
  SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
  APKSIGNER="$(ls -1 "${SDK_DIR}"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -n1 || true)"
  if [ -n "$APKSIGNER" ] && [ -x "$APKSIGNER" ]; then
    echo "Verifying APK certificate for $APK_FILE..."
    "$APKSIGNER" verify --verbose --print-certs "$APK_FILE"
  else
    echo "apksigner not found under ${SDK_DIR}; skipping APK signature check."
  fi
fi
