#!/bin/sh
# Re-enable every package this project disabled and hand Home back to the stock launcher.
#   ./restore.sh            everything
#   ./restore.sh com.x.y    just one package
: "${TV_HOST:?set TV_HOST to the IP of your TV first, e.g. export TV_HOST=192.168.1.50}"
SERIAL="$TV_HOST:${TV_PORT:-5555}"
cd "$(dirname "$0")"
adb connect "$SERIAL" >/dev/null

if [ $# -gt 0 ]; then pkgs="$*"; else pkgs=$(cat backup/disabled-by-us.txt); fi
for p in $pkgs; do
  adb -s "$SERIAL" shell pm enable "$p"
done

if [ $# -eq 0 ]; then
  adb -s "$SERIAL" shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity
  adb -s "$SERIAL" shell settings put global private_dns_mode opportunistic
  echo "Done. Reboot the TV (adb -s $SERIAL reboot) to finish."
fi
