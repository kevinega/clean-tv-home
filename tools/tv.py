#!/usr/bin/env python3
"""Run a shell command or press keys on the TV, optionally pulling a screenshot.

  tools/tv.py 'getprop ro.build.version.sdk'
  tools/tv.py --screenshot shots/01.png DPAD_RIGHT DPAD_DOWN
  needs TV_HOST=<tv ip>; TV_PORT defaults to 5555
"""
import os, subprocess, sys, time

HOST = os.environ.get("TV_HOST") or sys.exit("Set TV_HOST to your TV's IP first, e.g. export TV_HOST=192.168.1.50")
PORT = os.environ.get("TV_PORT", "5555")
SERIAL = f"{HOST}:{PORT}"


def adb(*args, **kw):
    return subprocess.run(["adb", "-s", SERIAL, *args], **kw)


def main():
    args = sys.argv[1:]
    shot = None
    if args[:1] == ["--screenshot"]:
        shot, args = args[1], args[2:]
    subprocess.run(["adb", "connect", SERIAL], capture_output=True)
    keys = [a for a in args if a.isupper() or a.startswith("KEYCODE_")]
    if keys and len(keys) == len(args):
        for k in keys:
            adb("shell", "input", "keyevent", k if k.startswith("KEYCODE_") else "KEYCODE_" + k)
            time.sleep(0.35)
    elif args:
        sys.exit(adb("shell", " ".join(args)).returncode)
    if shot:
        time.sleep(0.6)
        adb("shell", "screencap -p /sdcard/_shot.png")
        os.makedirs(os.path.dirname(shot) or ".", exist_ok=True)
        adb("pull", "/sdcard/_shot.png", shot, capture_output=True)
        adb("shell", "rm /sdcard/_shot.png")
        if not os.path.exists(shot) or os.path.getsize(shot) == 0:
            print("empty screenshot: protected content (Live TV/HDMI/DRM app). "
                  "Check: dumpsys activity activities | grep topResumedActivity")
            sys.exit(1)
        print(shot)


if __name__ == "__main__":
    main()
