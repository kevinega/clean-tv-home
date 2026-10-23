#!/bin/sh
# Disable packages for user 0, one adb call each, and log them for restore.sh.
#   tools/disable.sh pkg1 pkg2 ...
# Refuses Android core packages that can stop the TV from booting.
: "${TV_HOST:?set TV_HOST to the IP of your TV first, e.g. export TV_HOST=192.168.1.50}"
SERIAL="$TV_HOST:${TV_PORT:-5555}"
LOG="$(dirname "$0")/../backup/disabled-by-us.txt"

for p in "$@"; do
  case "$p" in
    android|com.android.systemui|com.android.shell|com.android.phone|com.android.settings|com.android.tv.settings|\
    *permissioncontroller*|*packageinstaller*|*.ext.services|*.ext.shared|*modulemetadata*|com.google.android.overlay.modules*|\
    com.google.android.gms|com.android.vending|com.google.android.inputmethod.latin|com.tcl.tv|com.tcl.tvinput|com.tcl.sourcemananger)
      echo "REFUSED (core/load-bearing): $p"; continue ;;
    com.android.providers.calendar|com.android.providers.contacts|com.android.providers.userdictionary|com.android.providers.blockednumber) ;;
    com.android.providers.*) echo "REFUSED (core provider): $p"; continue ;;
  esac
  out=$(adb -s "$SERIAL" shell pm disable-user --user 0 "$p" 2>&1)
  echo "$out"
  if adb -s "$SERIAL" shell pm list packages -d | grep -qx "package:$p"; then
    grep -qx "$p" "$LOG" 2>/dev/null || echo "$p" >> "$LOG"
  else
    echo "NOT DISABLED: $p"
  fi
done
