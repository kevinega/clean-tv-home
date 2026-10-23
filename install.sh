#!/bin/sh
# Build the launcher, install it on the TV and make it Home.
#   ./install.sh            build + install + set as Home
#   ./install.sh --no-home  build + install only
set -e
: "${TV_HOST:?set TV_HOST to the IP of your TV first, e.g. export TV_HOST=192.168.1.50}"
SERIAL="$TV_HOST:${TV_PORT:-5555}"
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}"

(cd launcher && ./gradlew -q assembleDebug)
APK=launcher/app/build/outputs/apk/debug/app-debug.apk

adb connect "$SERIAL" >/dev/null
# Push + pm install: plain `adb install` can hang on newer Android waiting for an on-screen confirmation.
adb -s "$SERIAL" push "$APK" /data/local/tmp/clean-home.apk >/dev/null
adb -s "$SERIAL" shell pm install -r /data/local/tmp/clean-home.apk
adb -s "$SERIAL" shell rm /data/local/tmp/clean-home.apk
# Wi-Fi name (Android 9 hides it without location) and the optional wallpaper in /sdcard.
adb -s "$SERIAL" shell pm grant tv.clean.launcher android.permission.ACCESS_FINE_LOCATION
adb -s "$SERIAL" shell pm grant tv.clean.launcher android.permission.READ_EXTERNAL_STORAGE

if [ "$1" != "--no-home" ]; then
  adb -s "$SERIAL" shell cmd package set-home-activity tv.clean.launcher/.HomeActivity
fi
adb -s "$SERIAL" shell am start -n tv.clean.launcher/.HomeActivity >/dev/null
echo "Installed."
