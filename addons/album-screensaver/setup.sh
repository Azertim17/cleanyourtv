#!/usr/bin/env bash
# Point the album screensaver at a shared album and make it the TV's screensaver. The TV downloads the album itself
# (now, then again whenever the screensaver starts and the last sync is older than REFRESH_HOURS); nothing stays on
# this computer. Install the app first with build-and-install.sh.
#
# Usage: TV_HOST=192.168.1.50 addons/album-screensaver/setup.sh 'https://photos.example.com/share/KEY'
#        TV_HOST=192.168.1.50 addons/album-screensaver/setup.sh 'https://photos.app.goo.gl/AbCdEf'
#        TV_HOST=192.168.1.50 IDLE_MINUTES=10 SECONDS_PER_PICTURE=30 REFRESH_HOURS=6 START=1 \
#          addons/album-screensaver/setup.sh 'https://...'
#        TV_HOST=192.168.1.50 addons/album-screensaver/setup.sh          # keep the album, change the other settings
#        TV_HOST=192.168.1.50 addons/album-screensaver/setup.sh --off    # previous screensaver back, pictures removed
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
TOOLS="$HERE/../../tools"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY="$TOOLS/venv/bin/python"
[ -x "$PY" ] || PY="$TOOLS/venv/Scripts/python.exe"
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=com.cleanyourtv.albumdream
DREAM=$PKG/.PhotoDream
URI=content://$PKG.files
TMP=/data/local/tmp/cyt-album
# The screensaver the TV had before the first run, so --off can put it back.
BEFORE=/data/local/tmp/cyt-album-before
IDLE_MINUTES="${IDLE_MINUTES:-0}"
SECONDS_PER_PICTURE="${SECONDS_PER_PICTURE:-20}"
REFRESH_HOURS="${REFRESH_HOURS:-12}"
tv() { "$PY" -I "$TOOLS/tv.py" "$@"; }

if [ "${1:-}" = --off ]; then
  tv "if [ -f $BEFORE ]; then { read c; read e; } < $BEFORE; if [ \"\$c\" = null ]; then settings delete secure screensaver_components >/dev/null; else settings put secure screensaver_components \"\$c\"; fi; [ \"\$e\" = null ] || settings put secure screensaver_enabled \"\$e\"; rm -f $BEFORE; fi; content delete --uri $URI/pictures >/dev/null 2>&1; pm clear $PKG >/dev/null 2>&1; true" >/dev/null
  echo "Previous screensaver back; the album's pictures and link are gone from the TV."
  exit 0
fi

LINK="${1:-}"
for V in IDLE_MINUTES SECONDS_PER_PICTURE REFRESH_HOURS; do
  [[ "${!V}" =~ ^[0-9]+$ ]] || { echo "$V takes a number" >&2; exit 1; }
done
[ "$IDLE_MINUTES" -le 1440 ] || { echo "IDLE_MINUTES takes 0 to 1440" >&2; exit 1; }
[ "$SECONDS_PER_PICTURE" -ge 5 ] && [ "$SECONDS_PER_PICTURE" -le 3600 ] || { echo "SECONDS_PER_PICTURE takes 5 to 3600" >&2; exit 1; }
[ "$REFRESH_HOURS" -ge 1 ] && [ "$REFRESH_HOURS" -le 720 ] || { echo "REFRESH_HOURS takes 1 to 720" >&2; exit 1; }
if [ -n "$LINK" ]; then
  # The link goes into a shell command on the TV inside single quotes: refuse anything that could end them.
  [[ "$LINK" =~ ^https?://[A-Za-z0-9._~:/?#@!$\&\(\)*+,\;=%-]+$ ]] \
    || { echo "that does not look like an album link (Immich .../share/KEY or .../s/SLUG, or Google Photos)" >&2; exit 1; }
fi
tv "pm path $PKG; true" | grep -q package: \
  || { echo "the album screensaver is not on the TV: run addons/album-screensaver/build-and-install.sh first" >&2; exit 1; }

tv "appops set $PKG WRITE_SETTINGS allow 2>/dev/null; true" >/dev/null
tv "printf '%s' $SECONDS_PER_PICTURE > $TMP-seconds.txt; content write --uri $URI/seconds.txt < $TMP-seconds.txt; rm -f $TMP-seconds.txt" >/dev/null
tv "[ -f $BEFORE ] || { settings get secure screensaver_components; settings get secure screensaver_enabled; } > $BEFORE; settings put secure screensaver_components $DREAM; settings put secure screensaver_enabled 1" >/dev/null
[ "$IDLE_MINUTES" -eq 0 ] || tv "settings put system screen_off_timeout $((IDLE_MINUTES * 60000))" >/dev/null

if [ -n "$LINK" ]; then
  tv "am start -n $PKG/.AlbumActivity --es url '$LINK' --ei hours $REFRESH_HOURS" >/dev/null
  echo "The TV is downloading the album; its screen shows the progress."
  for _ in $(seq 1 60); do
    sleep 3
    COUNT=$(tv "ls /sdcard/Android/data/$PKG/files/pictures 2>/dev/null | grep -vc '^\\.'; true" | tr -d '\r ')
    RUNNING=$(tv "dumpsys activity activities | grep -c 'AlbumActivity'; true" | tr -d '\r ')
    [ "${RUNNING:-0}" -gt 0 ] || break
  done
  COUNT=$(tv "ls /sdcard/Android/data/$PKG/files/pictures 2>/dev/null | grep -vc '^\\.'; true" | tr -d '\r ')
  echo "Album: ${COUNT:-0} picture(s) on the TV."
  [ "${COUNT:-0}" -gt 0 ] || echo "warning: nothing was downloaded: is the link shared, and can the TV reach the server? (see the TV screen)" >&2
else
  tv "am start -n $PKG/.AlbumActivity --ei hours $REFRESH_HOURS" >/dev/null
fi
echo "Screensaver: Album, $SECONDS_PER_PICTURE s per picture, refreshed every $REFRESH_HOURS h. Undo with: addons/album-screensaver/setup.sh --off"
[ -z "${START:-}" ] || tv "am start -n com.android.systemui/.Somnambulator >/dev/null; sleep 3; dumpsys dreams | grep mCurrentDreamName"
