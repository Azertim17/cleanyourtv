#!/usr/bin/env bash
# Build the album screensaver and install it on the TV. Then run setup.sh with your album link.
#
# Usage: TV_HOST=192.168.1.50 addons/album-screensaver/build-and-install.sh
#        TV_HOST=192.168.1.50 addons/album-screensaver/build-and-install.sh --uninstall
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
TOOLS="$HERE/../../tools"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY="$TOOLS/venv/bin/python"
[ -x "$PY" ] || PY="$TOOLS/venv/Scripts/python.exe"
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=com.cleanyourtv.albumdream
tv() { "$PY" -I "$TOOLS/tv.py" "$@"; }

if [ "${1:-}" = --uninstall ]; then
  # Put the screensaver from before setup.sh back first, then remove the app and its pictures.
  "$HERE/setup.sh" --off
  tv "pm uninstall $PKG"
  exit 0
fi

(cd "$HERE" && ./gradlew assembleRelease -q) || { echo "build failed (JDK 17 and Android SDK 34 needed; see launcher/README)" >&2; exit 1; }
"$PY" -I "$TOOLS/b64push.py" "$HERE/app/build/outputs/apk/release/app-release.apk" /data/local/tmp/albumdream.apk >/dev/null
tv "pm install -r /data/local/tmp/albumdream.apk; rm -f /data/local/tmp/albumdream.apk"
# TCL's TclAppBoot blocks the app's provider until it may auto-start; elsewhere the op doesn't exist.
tv "appops set $PKG AUTO_START allow 2>/dev/null; appops set $PKG APP_AUTO_START allow 2>/dev/null; appops write-settings 2>/dev/null; true" >/dev/null
# Lets the settings screen change the TV's idle time (the screensaver's start delay).
tv "appops set $PKG WRITE_SETTINGS allow 2>/dev/null; true" >/dev/null
echo "Installed. Now: TV_HOST=$TV_HOST addons/album-screensaver/setup.sh <album link>"
