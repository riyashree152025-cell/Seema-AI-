# Thermal Test Report — Seema AI

## Design
- Source of truth: Android `PowerManager` thermal status (API 29+), received through `addThermalStatusListener` — event-driven, no polling. Headroom (`getThermalHeadroom`, API 30+) is shown in the dashboard when the phone reports it.
- Mapping (`domain/power/Power.kt`): status 0 → NORMAL, 1–2 → WARM, 3 → HOT, ≥4 → CRITICAL.
- Actions (`MotionProfile.kt`), only ever reducing work:

| Level | Home visuals | Other |
|---|---|---|
| NORMAL | normal (30 fps, battery saver 20 fps) | — |
| WARM | ≤15 fps, half the particles, lighter background | — |
| HOT | ≤8 fps, no particles/effects, no background rain | red notice on Home |
| CRITICAL | still picture (no frames) | red notice on Home |

Voice, AI requests, calls and other user tasks keep working at every level. Android's own thermal protections are never touched. Setting: Settings → Performance & Battery → Thermal protection.

## Test performed (emulator, Android 15)
Android's thermal service was forced with `adb shell cmd thermalservice override-status <n>` while the app was in the foreground (the same events a hot phone sends):

| Forced status | Frames in 20 s, before | Frames in 20 s, after | Result |
|---|---|---|---|
| none (after a touch) | 602 | 601 | full animation |
| 3 (SEVERE → HOT) | 601 | 150 | ✅ throttled to ~8 fps, notice shown (screenshot `smoke-3-hot.png` in the run artifacts) |
| 4 (CRITICAL) | 602 | 1 | ✅ still picture |

The status was reset with `cmd thermalservice reset` afterwards. No crash or ANR in either run.

## Not tested yet
- A real warm-up on the Galaxy A14 (e.g. 30 min of conversation in a warm room) and whether the phone actually reports WARM/HOT then. Samsung phones report thermal status through the same API, but the thresholds are the manufacturer's.
- Phone temperature itself was not measured (the emulator has none).
