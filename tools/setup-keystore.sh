#!/usr/bin/env bash
set -euo pipefail

# Shared SpotUI signing keystore resolver & validator for GitHub Actions and local builds.
# Guarantees consistent in-place PackageInstaller updates across CI releases when either
# SIGNING_KEYSTORE_BASE64 / DEBUG_KEYSTORE_BASE64 secret or committed debug.keystore.base64
# is present. Never prints raw keystore secrets to logs.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KEYSTORE_PATH="${KEYSTORE_PATH:-${ROOT_DIR}/debug.keystore}"
BASE64_PATH="${BASE64_PATH:-}"
VERIFY_ONLY=0
REQUIRE_PERSISTENT=0

while [ $# -gt 0 ]; do
  case "$1" in
    --verify-only)
      VERIFY_ONLY=1
      ;;
    --require-persistent)
      REQUIRE_PERSISTENT=1
      ;;
    --keystore)
      shift
      KEYSTORE_PATH="$1"
      ;;
    --base64)
      shift
      BASE64_PATH="$1"
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 2
      ;;
  esac
  shift
done

if [ -z "$BASE64_PATH" ]; then
  for candidate in \
    "${ROOT_DIR}/debug.keystore.base64" \
    "${ROOT_DIR}/app/debug.keystore.base64" \
    "${ROOT_DIR}/tools/debug.keystore.base64"
  do
    if [ -f "$candidate" ]; then
      BASE64_PATH="$candidate"
      break
    fi
  done
  BASE64_PATH="${BASE64_PATH:-${ROOT_DIR}/debug.keystore.base64}"
fi

decode_base64_to_file() {
  local raw_b64="$1"
  local out_file="$2"
  local cleaned
  cleaned="$(printf '%s' "$raw_b64" | tr -d '\r\n \t')"
  [ -n "$cleaned" ] || return 1

  if printf '%s' "$cleaned" | base64 -d > "$out_file" 2>/dev/null && [ -s "$out_file" ]; then
    return 0
  fi
  if printf '%s' "$cleaned" | base64 -D > "$out_file" 2>/dev/null && [ -s "$out_file" ]; then
    return 0
  fi
  if command -v python3 >/dev/null 2>&1; then
    if python3 -c 'import base64,sys; sys.stdout.buffer.write(base64.b64decode(sys.stdin.read().strip(), validate=False))' <<< "$cleaned" > "$out_file" 2>/dev/null && [ -s "$out_file" ]; then
      return 0
    fi
  fi
  rm -f "$out_file"
  return 1
}

validate_keystore() {
  local ks="$1"
  [ -s "$ks" ] || return 1
  LC_ALL=C keytool -list -v -keystore "$ks" -storepass android -alias androiddebugkey >/dev/null 2>&1
}

extract_sha256() {
  local ks="$1"
  LC_ALL=C keytool -list -v -keystore "$ks" -storepass android -alias androiddebugkey 2>/dev/null \
    | grep -oE 'SHA256:[[:space:]]*[0-9A-Fa-f:]+' \
    | head -n1 \
    | awk '{print $2}'
}

short_id_from_sha256() {
  printf '%s' "${1:-unknown}" | tr -d ':' | tr '[:upper:]' '[:lower:]' | cut -c1-10
}

SIGNING_SOURCE=""
SECRET_B64="${SIGNING_KEYSTORE_BASE64:-${DEBUG_KEYSTORE_BASE64:-}}"

if [ -n "$SECRET_B64" ]; then
  if [ "$VERIFY_ONLY" -eq 1 ]; then
    TMP_KS="$(mktemp)"
    trap 'rm -f "$TMP_KS"' EXIT
    if decode_base64_to_file "$SECRET_B64" "$TMP_KS" && validate_keystore "$TMP_KS"; then
      SIGNING_SOURCE="github-secret"
      CERT_SHA256="$(extract_sha256 "$TMP_KS")"
    else
      echo "Error: SIGNING_KEYSTORE_BASE64 / DEBUG_KEYSTORE_BASE64 failed validation." >&2
      exit 1
    fi
  else
    TMP_KS="$(mktemp)"
    if decode_base64_to_file "$SECRET_B64" "$TMP_KS" && validate_keystore "$TMP_KS"; then
      mv "$TMP_KS" "$KEYSTORE_PATH"
      SIGNING_SOURCE="github-secret"
    else
      rm -f "$TMP_KS"
      echo "Error: SIGNING_KEYSTORE_BASE64 / DEBUG_KEYSTORE_BASE64 could not be decoded or validated as androiddebugkey." >&2
      exit 1
    fi
  fi
elif [ -f "$BASE64_PATH" ] && { [ ! -f "$KEYSTORE_PATH" ] || [ "$VERIFY_ONLY" -eq 1 ]; }; then
  TMP_KS="$(mktemp)"
  trap 'rm -f "$TMP_KS"' EXIT
  RAW_FILE_B64="$(cat "$BASE64_PATH")"
  if decode_base64_to_file "$RAW_FILE_B64" "$TMP_KS" && validate_keystore "$TMP_KS"; then
    if [ "$VERIFY_ONLY" -eq 0 ]; then
      mv "$TMP_KS" "$KEYSTORE_PATH"
      trap - EXIT
    fi
    SIGNING_SOURCE="repo-base64"
    CERT_SHA256="$(extract_sha256 "${KEYSTORE_PATH}")"
    if [ "$VERIFY_ONLY" -eq 1 ]; then
      CERT_SHA256="$(extract_sha256 "$TMP_KS")"
    fi
  else
    echo "Warning: $BASE64_PATH failed keytool validation." >&2
    rm -f "$TMP_KS"
  fi
fi

if [ -z "$SIGNING_SOURCE" ] && [ -f "$KEYSTORE_PATH" ]; then
  if validate_keystore "$KEYSTORE_PATH"; then
    if [ -f "$BASE64_PATH" ]; then
      TMP_BASE="$(mktemp)"
      RAW_FILE_B64="$(cat "$BASE64_PATH")"
      if decode_base64_to_file "$RAW_FILE_B64" "$TMP_BASE" && validate_keystore "$TMP_BASE"; then
        SHA_LOCAL="$(extract_sha256 "$KEYSTORE_PATH")"
        SHA_BASE="$(extract_sha256 "$TMP_BASE")"
        if [ "$SHA_LOCAL" = "$SHA_BASE" ]; then
          SIGNING_SOURCE="repo-base64"
        else
          SIGNING_SOURCE="workspace-keystore"
          echo "::warning::Workspace debug.keystore (${SHA_LOCAL}) differs from ${BASE64_PATH} (${SHA_BASE})." >&2
        fi
      else
        SIGNING_SOURCE="workspace-keystore"
      fi
      rm -f "$TMP_BASE"
    else
      SIGNING_SOURCE="workspace-keystore"
    fi
  else
    echo "Warning: existing keystore at $KEYSTORE_PATH is invalid or corrupt." >&2
  fi
fi

if [ -z "$SIGNING_SOURCE" ]; then
  if [ "$VERIFY_ONLY" -eq 1 ] || [ "$REQUIRE_PERSISTENT" -eq 1 ]; then
    echo "Error: No valid persistent keystore found (SIGNING_KEYSTORE_BASE64 / $BASE64_PATH / $KEYSTORE_PATH)." >&2
    exit 1
  fi
  echo "::warning::No SIGNING_KEYSTORE_BASE64 secret or debug.keystore.base64 found! Generating an ephemeral one-time keystore — Android will block in-place updates with 'Signing key mismatch' across separate CI builds."
  echo "To fix permanently: commit debug.keystore.base64 (git add -f debug.keystore.base64 && git push) OR add SIGNING_KEYSTORE_BASE64 to GitHub Repository Secrets."
  rm -f "$KEYSTORE_PATH"
  keytool -genkey -v -keystore "$KEYSTORE_PATH" -alias androiddebugkey \
    -keypass android -storepass android -keyalg RSA -keysize 2048 \
    -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
  SIGNING_SOURCE="ephemeral"
fi

if [ -z "${CERT_SHA256:-}" ] && [ -f "$KEYSTORE_PATH" ]; then
  CERT_SHA256="$(extract_sha256 "$KEYSTORE_PATH")"
fi
CERT_SHORT="$(short_id_from_sha256 "${CERT_SHA256:-unknown}")"

echo "Signing source: ${SIGNING_SOURCE} | Certificate SHA-256: ${CERT_SHA256:-unknown} (SpotUI ID: ${CERT_SHORT}…)"
if [ -f "$KEYSTORE_PATH" ]; then
  ls -lh "$KEYSTORE_PATH"
fi

if [ -n "${GITHUB_OUTPUT:-}" ]; then
  {
    echo "signing_source=${SIGNING_SOURCE}"
    echo "cert_sha256=${CERT_SHA256:-}"
    echo "cert_short=${CERT_SHORT}"
    echo "keystore_path=${KEYSTORE_PATH}"
  } >> "$GITHUB_OUTPUT"
fi
