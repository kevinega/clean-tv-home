# Example: TCL A3 (40")

TCL "UnionTV", Android TV 9 (SDK 28), 1280×720, ~1.4 GB RAM. `ro.adb.secure=1`.
Stock launcher: `com.google.android.tvlauncher`.

## Disabled

25 packages with `pm disable-user --user 0` (the list is `backup/disabled-by-us.txt`):

| Group | Packages |
|---|---|
| Telemetry | `com.tcl.bi` (Firebase analytics), `com.tcl.common.terminalmanagerplus`, `com.google.android.feedback`, `com.google.android.tv.bugreportsender` |
| TCL / Google content & ads | `com.tcl.tv.tclhome_passive`, `com.tcl.esticker`, `com.tcl.usercenter`, `com.tcl.appmarket2`, `com.google.android.tvrecommendations`, `com.google.android.leanbacklauncher.recommendations`, `com.google.android.leanbacklauncher.partnerwidget` |
| Background | `com.tcl.tvweishi` (cleaner), `com.tcl.copydatatotv` |
| Updates | `com.tcl.versionUpdateApp` |
| Preloaded | `com.google.android.backdrop`, `com.google.android.music`, `com.google.android.videos`, `com.google.android.play.games`, `com.android.gallery3d`, TCL Media Center (`com.tcl.ui_mediaCenter`, `com.tcl.videoplayer`, `com.tcl.imageplayer`, `com.tcl.audioplayer`) |
| Launchers | `com.google.android.tvlauncher` (stock), `com.spocky.projengmenu` (Projectivy) |

Afterwards the remote's Input, Netflix, YouTube and Settings buttons all still worked.

## Kept, on purpose

- TV and sources: `com.tcl.tv`, `com.tcl.tvinput`, `com.tcl.tvpassthrough`, `com.tcl.sourcemananger`
- Remote and Bluetooth: `com.tcl.autopair`, `com.tcl.tcl_bt_rcu_service`, `com.tcl.keycustomfunctionservice`
  (also a fallback Home), `com.tcl.keyhelp`, `com.tcl.rc.ota` (remote firmware)
- Google Assistant (`com.google.android.katniss`, `com.google.android.speech.pumpkin`) for phone-remote voice,
  Chromecast (`com.google.android.apps.mediashell`), Play Store, Play Services, keyboard

## Quirks

- **`com.tcl.usercenter` comes back at every boot**: the firmware re-enables it from its own list.
  Disabling it again after each reboot is pointless.
- **Factory reset lives in `com.tcl.versionUpdateApp`.** Run `./restore.sh com.tcl.versionUpdateApp`
  before resetting from Settings.
- **Projectivy holds the Home key** through its accessibility service even when another launcher is the
  default. Disabling the Projectivy package releases it.
- **The Wi-Fi name** is readable without location on Android 9 (saved networks list), so the pill shows it.
- Live TV, HDMI, YouTube and Netflix give black screenshots (protected video). Use
  `adb shell dumpsys activity activities | grep mResumedActivity` to see what is on screen.

## Ad-blocking DNS (optional)

```sh
adb shell settings put global private_dns_specifier dns.adguard-dns.com
adb shell settings put global private_dns_mode hostname
```

Blocks ad and tracker domains for the whole TV (not YouTube ads). Undo:
`adb shell settings put global private_dns_mode opportunistic`.
