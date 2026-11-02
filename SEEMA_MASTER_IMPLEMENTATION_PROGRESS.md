# SEEMA Master Implementation — progress

Repository: `riyashree152025-cell/Seema-AI-` (the prompt named `Seema_Jarvis`; that repository is not available to this session and the user chose Seema-AI-).
Branch: `claude/multi-brain-budget` (built on `claude/brain-voice-polish`; `main` untouched, no PR/merge/release).

## Baseline (verified in code, Nov 2026)
Already working before this phase (see git history and tests):
- AI: Gemini, Groq, Cerebras (real APIs), streaming replies, automatic fallback to another provider with a saved key, Test connection, model lists from the provider, Test active AI, real per-provider status.
- Keys: Android-Keystore AES-GCM vault (`SecureKeyStore`), redaction of key-like strings in errors, CI scan of the APK for keys.
- Voice: continuous conversation / always-listening, sleep + "Seema" wake (needs Deepgram), barge-in with echo canceller, offline Piper/Kokoro voices, Android TTS, Sarvam/ElevenLabs/Cartesia/Deepgram.
- Phone tools: calls (with confirmation), SMS, WhatsApp, apps, music, timers, calendar, notifications/call announcements.

Not implemented (honest list): offline wake word, screen-off listening (foreground mic service), Groq Whisper STT, Gemini TTS, Bluetooth-specific handling, primary/secondary provider order, speed modes, smart per-task model routing.

## Phase 2–3 (this branch): more providers + free-first $5 budget
Done:
- Providers SambaNova, NVIDIA NIM, OpenRouter, Cloudflare Workers AI through the existing OpenAI-compatible adapter (no second router). Cloudflare also needs the account ID (separate encrypted field).
- Model prices: OpenRouter's published `pricing` is read from its model list; free = price 0 or `:free`.
- Cost classes: FREE, FREE_TIER (Gemini, Groq, Cerebras, SambaNova, NVIDIA, Cloudflare), PAID (published price), UNKNOWN (never used — fail closed).
- Budget guard around every AI request (`GuardedAiProvider`): Free-only mode (default ON), monthly limit (default $5, only 80 % used), estimated tokens/cost per provider/model per month (resets on the 1st), blocked requests fall back to a free provider via the existing fallback.
- Cool-down after failures (429: 60 s, quota: 30 min, bad key: 10 min or until a new key is saved, server: 20 s) — no retry storms.
- Default model is free-first. Settings → AI & Brain → "AI budget (cost safety)" panel.

Endpoints: SambaNova `https://api.sambanova.ai/v1` and Cloudflare `…/accounts/{id}/ai/v1` confirmed from official docs (web search); NVIDIA `https://integrate.api.nvidia.com/v1` from secondary sources; OpenRouter `https://openrouter.ai/api/v1` from prior knowledge; Cloudflare model list `…/ai/models/search` from prior knowledge. The provider hosts and docs are blocked from this build machine, so none was called for real.

Not verified (needs the user's keys on the phone): every new provider's real connection, model list and replies; actual provider charges.

## Tests
- `BudgetGuardTest` (decisions, fail-closed, free-only, limit with margin, free-first default, price parsing, guard blocking/counting/cool-down, month reset).
- CI run 41434214851 on commit 1d933c7: build ✅, 274 unit tests / 0 failed ✅, lint ✅, release R8 ✅, APK key scan ✅ (no key-like strings).

## Phase 1 — Live voice (this branch, Nov 2026)
Audit: hands-free, continuous conversation, sleep + "Seema" wake (Deepgram) and barge-in already existed; the mic was released whenever the app left the screen (`ON_STOP`); no foreground service; no offline wake word; no call/headset handling.

Done (reusing the existing engine; no second engine or service):
- **Screen-off listening**: `VoiceSessionService` (foreground service, type microphone) keeps the existing hands-free session when the screen turns off. Opt-in setting "Keep listening when the screen is off"; started only while the app is on screen; ongoing notification with live state ("Microphone ON — …") and Stop; stops itself when hands-free ends or the option is turned off; not sticky. Manifest: FOREGROUND_SERVICE(_MICROPHONE).
- **Offline wake word "Hey Seema"**: `KwsWakeListener` (sherpa-onnx keyword spotter, English GigaSpeech 3.3M int8, 17.6 MB, Apache-2.0, SHA-256 checked) behind the existing `WakeListener`; `SwitchingWakeListener` uses it in sleep mode when downloaded and "Wake only when called by name" is on, else the old detector. Only our phrases are used (the archive's default keywords such as "HEY SIRI" are not). Downloaded by the existing `VoiceModelManager` (now generic over `DownloadableModel`).
  - Measured here on synthetic speech (Kokoro English voices): 4/6 "Hey Seema" detected, 0/6 false wakes (threshold 0.1, boost 2.0); archive test recordings: "LIGHT UP" found, 0 false "Hey Seema". Real-voice accuracy NOT verified.
- **After the wake word** the existing flow continues: greeting, listening, answer, listening again (unit-tested).
- **Calls / headsets**: `AudioInterruptions` — ringing: Seema's reply and mic stop (caller announcement keeps playing); answered: all her sound stops; call over: hands-free resumes if it was on. Headphones/Bluetooth disconnect (BECOMING_NOISY): she stops talking. No new permission (audio mode on Android 12+, call state below). VoIP calls are not detected.
- **Privacy**: notification + Android's green mic dot while the service runs; Home "Mic ON"; offline wake word records/sends nothing.

Not changed / limits:
- **Barge-in** stays as before: works with a Deepgram key in hands-free (echo canceller + transcription). Without them her own voice would stop her (found on a phone earlier), so no offline barge-in was added.
- Bluetooth headset **microphone** (SCO) routing is not added: the phone's mic is used; playback follows Android's routing.
- Google's speech recogniser with the screen locked depends on the phone; Deepgram listening records in the app and is expected to work. Not verified on a real phone.
- Some phone makers kill background apps despite a foreground service (battery settings).

Tests: engine (offline wake → greeting → answer → listening; call pause/resume; tap-to-talk unaffected), call-mode mapping, keywords/catalog, notification text. Emulator RUN 8 in `scripts/smoke.sh`: real wake-model download + native spotter on test recordings; service + notification with the app open, after HOME and with the screen off.

## Next action
1. Real-phone check of Phase 1 (wake word accuracy, screen-off with your recogniser, calls, Bluetooth).
2. Phase 4: primary/secondary provider + speed mode in AI & Brain.
