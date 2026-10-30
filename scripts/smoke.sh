#!/usr/bin/env bash
# Launches the debug APK on the CI emulator and prints what is on screen plus any crash/ANR logs.
set -x
PKG=com.raju.aiassistant.debug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant $PKG android.permission.RECORD_AUDIO || true
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS || true
adb logcat -c

echo "===== RUN 1: animations off ====="
adb shell am start -W -n $PKG/com.raju.aiassistant.MainActivity
sleep 20
adb shell dumpsys activity activities | grep -iE "ResumedActivity" || true
adb shell uiautomator dump /sdcard/ui1.xml || true
adb shell cat /sdcard/ui1.xml | tr '>' '\n' | grep -oE '(text|content-desc)="[^"]+"' | head -80 || true
adb exec-out screencap -p > smoke-1.png
adb logcat -d | grep -iE "AndroidRuntime|FATAL|ANR in|Application Not Responding|raju|System.err|Choreographer" | head -150 || true

echo "===== RUN 2: animations on ====="
adb shell settings put global animator_duration_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global window_animation_scale 1
adb shell am force-stop $PKG
adb logcat -c
adb shell am start -W -n $PKG/com.raju.aiassistant.MainActivity
sleep 8
adb shell dumpsys gfxinfo $PKG reset > /dev/null
sleep 20
adb shell dumpsys activity activities | grep -iE "ResumedActivity" || true
adb exec-out screencap -p > smoke-2.png
adb shell dumpsys gfxinfo $PKG | grep -iE "Total frames|Janky|Percentile|90th|99th" || true
adb logcat -d | grep -iE "AndroidRuntime|FATAL|ANR in|Application Not Responding|raju|System.err|Skipped .* frames" | head -150 || true
adb shell dumpsys meminfo $PKG | grep -E "TOTAL PSS|TOTAL:" | head -2 || true

# ---- Power measurements (frames drawn in 20 s windows; memory). Same emulator, same script, so
# ---- the numbers can be compared between versions. Emulator GPU is software: compare, don't extrapolate.
measure() {
  adb shell dumpsys gfxinfo $PKG reset > /dev/null
  sleep 20
  echo "POWER[$1] $(adb shell dumpsys gfxinfo $PKG | grep -E 'Total frames rendered' | tr -s ' ')"
  echo "POWER[$1] $(adb shell dumpsys meminfo $PKG | grep -E 'TOTAL PSS|TOTAL:' | head -1 | tr -s ' ')"
}
echo "===== RUN 3: idle and sleep (no touch) ====="
sleep 15            # ~45 s after launch: smart power should be in IDLE
measure idle
sleep 60            # >120 s after launch: SLEEP, animation stopped
measure sleep
echo "===== RUN 4: thermal (simulated with Android's thermal service) ====="
adb shell input tap 5 320  # a touch wakes the visuals
sleep 2
measure active-after-touch
adb shell cmd thermalservice override-status 3 || echo "thermal override not supported"
adb shell input tap 5 320
sleep 2
measure thermal-severe
adb exec-out screencap -p > smoke-3-hot.png
adb shell cmd thermalservice override-status 4 || true
sleep 2
measure thermal-critical
adb shell cmd thermalservice reset || true

echo "===== RUN 5: AI core appearance (Legacy -> 3D -> restart keeps 3D -> back to Legacy) ====="
screen_has() { adb shell uiautomator dump /sdcard/ui-core.xml > /dev/null 2>&1; adb shell cat /sdcard/ui-core.xml | grep -c "$1" || true; }
echo "CORE[legacy] battery-tile-visible=$(screen_has 'BATTERY')"
adb shell am start -n $PKG/com.raju.aiassistant.MainActivity --es seema_core MODERN
sleep 6
echo "CORE[modern] battery-tile-visible=$(screen_has 'BATTERY')"
adb exec-out screencap -p > smoke-4-modern.png
measure modern-core
adb shell am force-stop $PKG
adb shell am start -W -n $PKG/com.raju.aiassistant.MainActivity > /dev/null
sleep 8
echo "CORE[after-restart] battery-tile-visible=$(screen_has 'BATTERY')"
adb shell am start -n $PKG/com.raju.aiassistant.MainActivity --es seema_core LEGACY
sleep 6
echo "CORE[back-to-legacy] battery-tile-visible=$(screen_has 'BATTERY')"
adb exec-out screencap -p > smoke-5-legacy.png
measure legacy-core

echo "===== RUN 6: demo recording (proof the cores really move and react) ====="
# Real weather needs a location: grant city-level location and give the emulator a position (New Delhi).
adb shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION || true
adb emu geo fix 77.209 28.6139 || true
preview() { adb shell am start -n $PKG/com.raju.aiassistant.MainActivity --es seema_preview "$1" > /dev/null; sleep "$2"; }
record() { # $1 = file name, $2 = seconds (screenrecord stops by itself)
  adb shell rm -f /sdcard/$1
  adb shell screenrecord --bit-rate 6000000 --time-limit "$2" /sdcard/$1 &
  REC=$!
}
# Part 1: Legacy Arc Reactor — idle rotation/heartbeat, then Listening, Thinking, Speaking, Error.
adb shell am start -n $PKG/com.raju.aiassistant.MainActivity --es seema_core LEGACY > /dev/null
sleep 4
record demo-1-legacy.mp4 46
sleep 1
preview NONE 8; preview Listening 8; preview Thinking 7; preview Speaking 9; preview Error 6; preview NONE 6
wait $REC
# Part 2: one tap on the "🎨 Arc Reactor ⇄" chip switches to the 3D core (live switch on camera),
# then the 3D dashboard: clock seconds, weather, stats, quick controls, and the same state reactions.
record demo-2-modern.mp4 60
sleep 3
CHIP=$(adb shell uiautomator dump /sdcard/ui-chip.xml > /dev/null 2>&1; adb shell cat /sdcard/ui-chip.xml | tr '>' '\n' | grep 'Arc Reactor ⇄' | grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1 | grep -oE '[0-9]+' | tr '\n' ' ')
echo "DEMO chip bounds: $CHIP"
if [ -n "$CHIP" ]; then set -- $CHIP; adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); else adb shell am start -n $PKG/com.raju.aiassistant.MainActivity --es seema_core MODERN > /dev/null; fi
sleep 10
preview Listening 9; preview Thinking 7; preview Speaking 10; preview Error 6; preview NONE 8
wait $REC
# Part 3: switch back to the Arc Reactor and show it running again.
record demo-3-switch-back.mp4 15
sleep 3
adb shell am start -n $PKG/com.raju.aiassistant.MainActivity --es seema_core LEGACY > /dev/null
wait $REC
for f in demo-1-legacy.mp4 demo-2-modern.mp4 demo-3-switch-back.mp4; do adb pull /sdcard/$f $f || true; done
ls -la demo-*.mp4 || true
adb shell uiautomator dump /sdcard/ui-final.xml > /dev/null 2>&1
adb shell am start -n $PKG/com.raju.aiassistant.MainActivity --es seema_core MODERN > /dev/null; sleep 5
adb shell uiautomator dump /sdcard/ui-modern.xml > /dev/null 2>&1
echo "DASHBOARD texts: $(adb shell cat /sdcard/ui-modern.xml | tr '>' '\n' | grep -oE '(text|content-desc)="[^"]+"' | tr '\n' ' ')"
adb exec-out screencap -p > smoke-6-dashboard.png
adb logcat -d | grep -iE "AndroidRuntime|FATAL|ANR in" | head -50 || true
adb logcat -d > logcat.txt
exit 0
