#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if [ ! -f debug.keystore ] && [ -f debug.keystore.base64 ]; then
  echo "Decoding shared debug keystore..."
  base64 -d debug.keystore.base64 > debug.keystore
fi

GRADLE_CMD="gradle"
if command -v gradle >/dev/null 2>&1; then
  GRADLE_CMD="gradle"
elif [ -x "./gradlew" ]; then
  GRADLE_CMD="./gradlew"
else
  echo "Error: neither 'gradle' nor executable './gradlew' was found in PATH." >&2
  exit 1
fi

echo "Building Release APK with ${GRADLE_CMD}..."
"$GRADLE_CMD" :app:assembleRelease --no-daemon --max-workers=2

VERSION="$(grep -E 'versionName\s*=' app/build.gradle.kts | head -n1 | sed -E 's/.*"([^"]+)".*/\1/')"
APK_SRC="app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK_SRC" ]; then
  echo "Error: expected APK not found at $APK_SRC" >&2
  exit 1
fi

mkdir -p dist
APK_OUT="dist/SpotUI-v${VERSION}-release.apk"
cp "$APK_SRC" "$APK_OUT"
(
  cd dist
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "SpotUI-v${VERSION}-release.apk" > "SpotUI-v${VERSION}-release.apk.sha256"
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "SpotUI-v${VERSION}-release.apk" > "SpotUI-v${VERSION}-release.apk.sha256"
  fi
)

echo ""
echo "Release APK ready for upload to GitHub Releases:"
echo "  APK:      $ROOT_DIR/$APK_OUT"
echo "  Checksum: $ROOT_DIR/dist/SpotUI-v${VERSION}-release.apk.sha256"
ls -lh "$APK_OUT"
