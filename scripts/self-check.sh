#!/usr/bin/env bash
# Runs the app's own self-check on a connected emulator or phone and reports what it said.
#
#   scripts/self-check.sh <debug.apk> [out-dir]
#
# Exit 0: everything the app asks of craigslist still works.
# Exit 1: something drifted (the calibration or the code needs fixing); the reason is printed.
# Exit 2: craigslist would not talk to this network at all, so nothing could be learned.
set -uo pipefail
APK="${1:?usage: self-check.sh <debug.apk> [out-dir]}"
OUT="${2:-self-check-out}"
mkdir -p "$OUT"
adb install -r "$APK" >/dev/null || { echo "RESULT FAIL at install" | tee "$OUT/result.txt"; exit 1; }
# A freshly booted emulator often has no working network for its first half minute, which the app
# reports as "blocked". That verdict is only believed after three tries, a little apart.
RESULT=""
for attempt in 1 2 3; do
  adb shell am force-stop app.corkboard
  adb logcat -c
  adb shell am start -n app.corkboard/.MainActivity --ez self_check true >/dev/null
  RESULT=""
  for _ in $(seq 1 60); do
    RESULT="$(adb logcat -d -s 'CorkboardSelfCheck:I' | grep -o 'RESULT .*' | tail -n1 || true)"
    [ -n "$RESULT" ] && break
    sleep 3
  done
  case "$RESULT" in "RESULT BLOCKED"*) echo "try $attempt: $RESULT"; sleep 25 ;; *) break ;; esac
done
adb logcat -d -s 'CorkboardSelfCheck:I' 'CorkboardHttp:D' 'AndroidRuntime:E' > "$OUT/log.txt" 2>/dev/null || true
adb exec-out screencap -p > "$OUT/screen.png" 2>/dev/null || true
[ -z "$RESULT" ] && RESULT="RESULT FAIL at start: the app never reported (crash or hang)"
echo "$RESULT" | tee "$OUT/result.txt"
grep 'CorkboardSelfCheck' "$OUT/log.txt" | sed 's/.*CorkboardSelfCheck: //' | grep -v '^RESULT' || true
case "$RESULT" in
  "RESULT PASS"*) exit 0 ;;
  "RESULT BLOCKED"*) exit 2 ;;
  *) exit 1 ;;
esac
