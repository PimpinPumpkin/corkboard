#!/usr/bin/env bash
# Signs calibration.json so the app, which carries the matching public key in
# data/Calibration.kt, will accept it. Run this after EVERY edit to calibration.json, then
# commit calibration.json and calibration.json.sig together. A unit test fails the build if the
# two do not match.
#
#   ./scripts/sign-calibration.sh
#
# The private key lives outside the repository and must never be committed. Override its path
# with CORKBOARD_CALIBRATION_KEY if needed.
set -euo pipefail

KEY="${CORKBOARD_CALIBRATION_KEY:-$HOME/.corkboard-signing/corkboard-calibration.key}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JSON="$ROOT/calibration.json"
SIG="$ROOT/calibration.json.sig"

if [ ! -f "$KEY" ]; then
  echo "error: signing key not found at $KEY" >&2
  exit 1
fi

# Detached ECDSA P-256 / SHA-256 signature, base64: what Calibration.verified checks.
openssl dgst -sha256 -sign "$KEY" "$JSON" | base64 | tr -d '\n' > "$SIG"

ver="$(grep -oE '"version"[[:space:]]*:[[:space:]]*[0-9]+' "$JSON" | grep -oE '[0-9]+')"
echo "signed calibration.json (version $ver) -> calibration.json.sig"

# Verify with the public half before anyone ships it.
PUB="$(mktemp)"; trap 'rm -f "$PUB"' EXIT
openssl ec -in "$KEY" -pubout -out "$PUB" 2>/dev/null
if openssl dgst -sha256 -verify "$PUB" -signature <(base64 -d < "$SIG") "$JSON" >/dev/null 2>&1; then
  echo "verify OK"
else
  echo "error: the signature does not verify; do not commit" >&2
  exit 1
fi
