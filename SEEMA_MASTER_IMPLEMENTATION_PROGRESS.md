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
- CI results: see the latest run on the branch (recorded below after it finishes).

## Next action
1. Verify CI on `claude/multi-brain-budget`.
2. Phase 4: primary/secondary provider + speed mode in AI & Brain; Phase 1 items (foreground mic service, offline wake word) need real-device testing.
