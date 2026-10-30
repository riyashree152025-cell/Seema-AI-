# Resource Usage Report — Seema AI

## In the app
Settings → Performance & Battery → **Show current usage** reads, at that moment only:
app memory (PSS, `Debug.getPss`), Java heap, battery % / charging / phone Battery Saver, thermal status and headroom, assistant and microphone state, selected home screen, active modules, scheduled background work (WorkManager state) and the last tasks. CPU load is labelled *not available* (Android does not give apps their CPU load). Nothing is sampled in the background.

## Emulator measurements (Android 15, API 35; see POWER_OPTIMIZATION_REPORT.md)
| | Before | After |
|---|---|---|
| App memory (PSS) after launch / idle | 122.3 MB (idle) | 114.8 MB (after launch), 119.3 MB (idle) |
| App memory after 2+ min | 129.6 MB | 123.2 MB |
| Frames in 20 s while idle / sleeping | 600 / 602 | 241 / 0 |
| Network while idle (no user request) | no AI calls; model list fetched before each request | no AI calls; model list fetched once |
| Microphone while idle (default settings) | hands-free listening on | off (push-to-talk) |

## How to measure on the phone (Galaxy A14) — still to do
1. Install the debug APK of each version, connect USB debugging.
2. Idle: open Seema, don't touch for 3 min, then
   `adb shell dumpsys gfxinfo com.raju.aiassistant.debug reset; sleep 60; adb shell dumpsys gfxinfo com.raju.aiassistant.debug | grep "Total frames"`
   and `adb shell dumpsys meminfo com.raju.aiassistant.debug | grep "TOTAL PSS"`.
3. Battery: `adb shell dumpsys batterystats --reset`, use the app for 30 min (conversation), then `adb bugreport` → Battery Historian; repeat with 30 min idle on the Home screen.
4. CPU/energy: Android Studio → Profiler → CPU + Energy during the same 30 min.
5. Heat: Settings → Performance & Battery → Show current usage → Thermal status, before and after.
Write the numbers into this file; until then, no battery or temperature improvement is claimed.
