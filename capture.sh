#!/usr/bin/env bash
# Capture Fris Black preview: desktop + mobile screenshots.
# Env: CAPTURE_URL (exact URL), CAPTURE_DIR (output outside source).
# Exits: 75 = temporary navigation/browser infra failure; 1 = script/rendering defect.
set -euo pipefail

fail_temp() { echo "capture: TRANSIENT: $*" >&2; exit 75; }
fail_perm() { echo "capture: DEFECT: $*" >&2; exit 1; }

# --- validate env (script contract; missing env = defect) ---
/usr/bin/time -p test -n "${CAPTURE_URL:-}" || fail_perm "CAPTURE_URL is not set"
/usr/bin/time -p test -n "${CAPTURE_DIR:-}" || fail_perm "CAPTURE_DIR is not set"
case "$CAPTURE_URL" in
  http://*|https://*) ;;
  *) fail_perm "CAPTURE_URL must be http(s): $CAPTURE_URL" ;;
esac

/usr/bin/time -p mkdir -p "$CAPTURE_DIR"
case "$CAPTURE_DIR" in
  /home/runner/work/PlayGround/PlayGround/*) fail_perm "CAPTURE_DIR must stay outside the project source: $CAPTURE_DIR" ;;
esac

SESSION="fris-cap-$$"
CLI="playwright-cli -s=$SESSION"
BROWSER_FLAG="--browser chrome"
/usr/bin/time -p test -x /usr/bin/google-chrome || fail_temp "google-chrome not installed"

close_browser() { /usr/bin/time -p playwright-cli -s="$SESSION" close >/dev/null 2>&1 || true; }
trap close_browser EXIT

# --- pre-check: URL reachable (transient 5xx/timeout -> 75, 4xx -> 1) ---
echo "capture: probing $CAPTURE_URL"
/usr/bin/time -p bash -c '
code=$(curl --silent --location --max-time 20 --output /dev/null --write-out "%{http_code}" "$CAPTURE_URL") || exit 75
echo "capture: HTTP $code"
case "$code" in
  2*) exit 0 ;;
  408|429|500|502|503|504) exit 75 ;;
  *) exit 1 ;;
esac
' || {
  rc=$?
  if [ "$rc" -eq 75 ]; then fail_temp "preview not reachable (transient)"; else fail_perm "preview rejected request (HTTP permanent)"; fi
}

# --- desktop 1440x900 ---
echo "capture: desktop view"
/usr/bin/time -p playwright-cli -s="$SESSION" open $BROWSER_FLAG "$CAPTURE_URL" >/dev/null || fail_temp "browser open failed (desktop)"
/usr/bin/time -p playwright-cli -s="$SESSION" resize 1440 900 >/dev/null || fail_temp "resize desktop failed"
/usr/bin/time -p sleep 2
# wait for rendered content: body visible + fonts loaded (best effort, then verify via snapshot)
/usr/bin/time -p playwright-cli -s="$SESSION" eval "() => document.fonts.status" >/dev/null 2>&1 || true
SNAP="$(/usr/bin/time -p playwright-cli -s="$SESSION" snapshot 2>/dev/null)" || fail_temp "snapshot failed (desktop)"
echo "$SNAP" | /usr/bin/time -p grep -qi "FRIS BLACK" || fail_perm "rendered content missing 'FRIS BLACK' (desktop)"
/usr/bin/time -p playwright-cli -s="$SESSION" screenshot --filename "$CAPTURE_DIR/final-desktop.png" >/dev/null || fail_temp "desktop screenshot failed"

# --- mobile 390x844 ---
echo "capture: mobile view"
/usr/bin/time -p playwright-cli -s="$SESSION" resize 390 844 >/dev/null || fail_temp "resize mobile failed"
/usr/bin/time -p playwright-cli -s="$SESSION" goto "$CAPTURE_URL" >/dev/null || fail_temp "navigation failed (mobile)"
/usr/bin/time -p sleep 2
/usr/bin/time -p playwright-cli -s="$SESSION" eval "() => document.fonts.status" >/dev/null 2>&1 || true
SNAP_M="$(/usr/bin/time -p playwright-cli -s="$SESSION" snapshot 2>/dev/null)" || fail_temp "snapshot failed (mobile)"
echo "$SNAP_M" | /usr/bin/time -p grep -qi "FRIS BLACK" || fail_perm "rendered content missing 'FRIS BLACK' (mobile)"
/usr/bin/time -p playwright-cli -s="$SESSION" screenshot --filename "$CAPTURE_DIR/final-mobile.png" >/dev/null || fail_temp "mobile screenshot failed"

/usr/bin/time -p playwright-cli -s="$SESSION" close >/dev/null 2>&1 || true
trap - EXIT

# --- verify artifacts (missing/empty = rendering defect) ---
/usr/bin/time -p test -s "$CAPTURE_DIR/final-desktop.png" || fail_perm "final-desktop.png missing or empty"
/usr/bin/time -p test -s "$CAPTURE_DIR/final-mobile.png" || fail_perm "final-mobile.png missing or empty"

echo "capture: OK -> $CAPTURE_DIR/final-desktop.png + final-mobile.png"
