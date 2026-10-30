# Power Optimization Report — Seema AI

Target: Samsung Galaxy A14, Android 15. Branch `claude/power-optimization`.

## Where the power went (found in the code)
| Cause | Effect | Fix |
|---|---|---|
| Home animation ran at 30 fps whenever the screen was on, also when nobody used it | constant CPU/GPU work → warm phone | Smart power: 12 fps after 30 s without touch/activity, **no frames at all** after 2 min; any touch or assistant event restores full animation |
| Hands-free listening was ON by default | microphone + speech recogniser restarting every few seconds while the app was open | Voice modes; default **Push-to-talk** (mic only on tap). *Session* ends by itself after the timeout; *Always listening* only when chosen (and only while the app is open) |
| Phone heat was ignored | the app kept full visuals while Android was already throttling | Thermal protection (PowerManager thermal status listener, event-driven) |
| AI model list was downloaded before **every** request when no model was chosen | one extra network call per question | Model found once per provider/key, then reused |
| Chat transcript grew in memory without limit | memory grows over long use | Last 300 messages in memory, full history stays in the database |
| Retries waited 1 s linearly | — | Exponential backoff (1 s, 2 s, 4 s) for network/server errors only |
| Android TTS stayed bound to the TTS service forever; share files were never deleted | memory held in background, cache growth | Released when Android trims the app in the background; share files older than a day are deleted (only Seema's own share folder) |

Not present in this app (so nothing to optimise): 3D avatar, Termux, F-Droid tools, KDE Connect, xAI Grok, file sync, a separate automation engine.
Already on demand before this change: AI providers (created per request), TTS/STT engines (lazy), web search, wake detector (only in sleep), barge-in microphone (only while Seema speaks).

## Background behaviour (checked)
- No foreground service, no wake locks, no polling. The only background work is two WorkManager jobs (morning briefing ~7 AM, Sunday report), both switchable (Settings → Performance & Battery → Background automation) and computed on the phone without any AI call.
- The microphone is never used after the app leaves the screen (it is released on `ON_STOP`).

## Measured before / after (emulator)
Same script (`scripts/smoke.sh`), same emulator (Android 15 / API 35, x86_64, software GPU), app freshly installed, no touch unless stated. Numbers are **frames rendered by the app in 20 seconds** (`dumpsys gfxinfo`) and **app memory** (`dumpsys meminfo`, TOTAL PSS).

| State | Before (main 4224d69) | After (056d3d4) |
|---|---|---|
| Active (after a touch) | 602 frames | 601 frames (unchanged, by design) |
| Idle (~45–65 s after launch, no touch) | 600 frames | **241 frames** (−60 %) |
| Sleep (>120 s, no touch) | 602 frames | **0 frames** |
| Simulated thermal SEVERE (`cmd thermalservice override-status 3`) | 601 frames | **150 frames** (−75 %) |
| Simulated thermal CRITICAL (status 4) | 602 frames | **1 frame** (still picture) |
| Memory (PSS), idle | 122.3 MB | 119.3 MB |
| Memory (PSS), after sleep | 129.6 MB | 123.2 MB |

Runs: before = GitHub Actions run 40997604778 (job "smoke (35)"), after = run 40997602953 (job "smoke (35)"). Both also ran on API 34 without crashes.

### What these numbers do and do not show
- Frames rendered is a direct measure of drawing work; fewer frames = less CPU/GPU time. The emulator renders in software, so **absolute** timings and battery/heat cannot be read from it.
- Battery drain and temperature were **not** measured; no battery-saving percentage is claimed.
- Memory differences of a few MB are within normal run-to-run variation.

## Still to measure on the Galaxy A14 (real device)
See `RESOURCE_USAGE_REPORT.md` → "How to measure on the phone". Required: 30 min active use and a separate idle period, before and after, with Android Studio Profiler (Energy/CPU/Memory) or Perfetto, and `dumpsys batterystats`.
