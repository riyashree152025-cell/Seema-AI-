# SEEMA AI — Complete Project Audit

Audit date: 30 Oct 2026. Base inspected: `main` at `6a88c48` (v1.7.1). The checkpoint before any change in this round is that commit, which is on GitHub.

The audit was done against the source code and the CI emulator runs. No physical phone was available to the auditor. Every "runtime verified" below means **verified on an Android 34/35 emulator in CI**, not on a phone.

## Classification legend

| # | Meaning |
|---|---|
| 1 | Fully implemented and runtime verified (emulator) |
| 2 | Implemented but incomplete |
| 3 | Implemented but not connected to the actual AI system |
| 4 | Placeholder or demo only |
| 5 | Completely missing |
| 6 | Broken or producing errors |
| 7 | Not verifiable here (needs a real phone, credentials or audio) |

## Project facts

| Item | Value |
|---|---|
| Entry point | `MainActivity` → `RajuNavHost` (Home, Chat, Tools, Settings) |
| Package | `com.raju.aiassistant` (debug: `.debug`) |
| Language / UI | Kotlin 2.0.21, Jetpack Compose (BOM 2024.12.01), Material 3 |
| Build | AGP 8.7.3, Gradle 8.11.1, JDK 17 target (JDK 21 to run Gradle), minSdk 26, target/compile 35 |
| Architecture | Single `AppContainer` (manual DI). `AssistantEngine` is the central orchestrator. Ports/adapters live in `domain/assistant/Ports.kt`, `domain/voice`, `domain/ai`. Compose screens talk to ViewModels, and ViewModels talk to the engine. |
| Storage | Room DB (`raju_ai.db`), DataStore for settings, Android Keystore (AES-GCM) for API keys (`SecureKeyStore`) |
| AI state | `AssistantEngine.state: StateFlow<AssistantState>`, shared by Home (both cores), Chat and the waveform |
| Tests | JVM unit tests (`app/src/test`), CI emulator smoke test (`scripts/smoke.sh`) |

## Feature audit (state BEFORE this round → AFTER)

### AI engine and providers (Part 2)

| Feature | Before | After | Files |
|---|---|---|---|
| Central orchestration engine (conversation, prompt, tool routing, state) | 1 | 1 | `domain/assistant/AssistantEngine.kt`, `PromptBuilder.kt`, `AssistantAction.kt` |
| Conversation manager / context (history window, char budget) | 1 | 1 | `toTurns`, `ConversationRepository` |
| Intent/tool routing (calls, SMS, WhatsApp, timers, alarms, calendar, music, settings, khata, PNR, research) | 1 | 1 | `AssistantAction.kt`, `AndroidDeviceTools.kt` |
| Provider adapters (Gemini, Groq/OpenAI-compatible) | 1 | 1 | `data/ai/*` |
| Manual provider selection, health check (real "Test" request) | 1 | 1 | `SettingsViewModel.testSecret` |
| Automatic provider selection / fallback | 5 | **2**: another provider with a saved key answers when the chosen one fails, and the chat says so. There is no ranking by health score. | `AssistantEngine.fallbackReply` |
| Timeouts, retries | 1 | 1 | `HttpSupport.withRetry`, OkHttp timeouts |
| Cancellation (Stop) | 1 | 1 | `cancelWork`, `stopEverything` |
| Streaming responses | 5 | **5**: not implemented. Replies are short spoken sentences. Streaming would need SSE parsing for both providers. | — |
| Local / offline LLM | 5 | **5**: not implemented. No model was downloaded, loaded or benchmarked on a device, so none is claimed. | — |
| Online/offline indication | 1 | 1 | `ConnectivityObserver`, Offline state, Home chips |
| API keys in Keystore-backed storage, nothing hardcoded | 1 | 1 | `core/security/SecureKeyStore.kt` |

### Hindi + Bengali (Part 3)

| Feature | Before | After |
|---|---|---|
| Hindi text in/out | 1 | 1 |
| Bengali text in/out | 5 | **2**: prompt rule, script detection, Bengali phrases. Bengali replies from the AI need a real provider key, so this is 7 for live replies. |
| Automatic Hindi/Bengali detection | 5 | 1 (unit tested): Unicode script blocks, `domain/language/Language.kt` |
| Manual selection + persistence | 2 (Hindi/English) | 1: Auto / हिन्दी / বাংলা in Settings → Voice. Stored in DataStore; the old `en-IN` setting maps to Hindi. |
| Ask when uncertain | 5 | 1: evenly mixed Hindi+Bengali makes Seema ask which language the user wants |
| Language-aware STT | 2 | **2**: the recogniser gets `bn-IN`/`hi-IN` plus the other language as an extra. Bengali always uses the Android recogniser, because the Deepgram models used here do not support it. **7** for real speech. |
| Language-aware TTS | 2 | **2**: Bengali chunks are spoken as `bn-IN` (Sarvam, Android). English-only voices refuse Bengali instead of reading it wrongly. **7** for audible output. |
| English never substituted | 6 (English was a language option) | 1: English removed as a reply language. Hinglish counts as Hindi. |

### Voice (Part 4)

| Feature | Before | After |
|---|---|---|
| Replaceable STT / TTS interfaces | 1 | 1 (`VoiceInput`, `TtsProvider`, `Speaker`, `Transcriber`) |
| VAD / echo-aware barge-in | 1 | 1 (`EchoAwareVad`, `MicBargeIn`, `EndpointDetector`) |
| Female voice (Sarvam Bulbul female speakers, ElevenLabs/Cartesia female defaults) | 1 | 1 |
| Speed | 1 | 1 |
| Pitch | 5 | 1: setting added; it applies to the Android voice (cloud voices have no pitch control here) |
| Mute/unmute | 2 (Settings only) | 1: speaker button in Chat |
| Interrupt / Stop | 1 | 1 |
| Repeat response | 5 | 1: 🔊 Repeat under each reply, and `repeatLast()` |
| Push-to-talk, session, always-listening (explicit opt-in, app on screen only) | 1 | 1 |
| Mic permission status, voice engine status | 1 | 1 (Settings → Status; Home chips) |
| Offline Whisper / Marmalade / Sherpa / Piper / Bhashini models | 5 | **5**: not integrated. See the Implementation Report for why. |

### Memory (Part 5)

| Feature | Before | After |
|---|---|---|
| Conversation history (Room) | 1 | 1 |
| User-approved memories (only when the user asks, secrets refused) | 1 | 1 |
| View / edit / delete one / clear all with confirmation | 1 | 1 (Tools → Memories; Settings) |
| Search conversation history | 5 | 1: search in Chat |
| New conversation | 5 | 1 |
| Export / import memories | 5 | 1 (JSON; duplicates and secrets skipped) |
| Temporary session context | 1 | 1 (in-memory transcript window) |
| Project-specific memory (separate category) | 5 | **5**: not implemented. Memories are one list. |
| Imported knowledge kept separate | 5 | 1 (Knowledge Base tables) |

### Knowledge Base / RAG (Part 6)

| Feature | Before | After |
|---|---|---|
| Import PDF / DOCX / TXT / Markdown | 5 | 1 (unit tests for DOCX/TXT; PDF via PDFBox-Android). **7** for importing real files on a phone. |
| Extraction, chunking, index, search (BM25, on phone) | 5 | 1 (unit tested) |
| Context retrieval + source attribution `[Doc: name §n]` | 5 | 1 (engine test) |
| Remove document, rebuild index | 5 | 1 |
| Not called "training" | — | 1: the UI says "document search (RAG) — the AI model is not trained" |

### Animated cores and dashboard (Parts 7–9)

| Feature | Before | After |
|---|---|---|
| Legacy Arc Reactor (v1.6 original recovered from git `796d89e`): rotation, heartbeat, rings, particles, colour per state | 1 | 1 (emulator recording) |
| Modern 3D holographic core (Canvas 3D: wireframe sphere, tilted rings, particles, perspective) | 1 | 1 (emulator recording) |
| Only the selected core runs; animation paused when not visible | 1 | 1 |
| Settings selector with live preview, Apply, Reset, persistence | 1 | 1 |
| Clock with seconds (rolling digits) and date | 2 | 1 |
| Weather (Open-Meteo, coarse location on tap, loading/offline/no-location states) | 5 | 2: real API, unit tested. **7** for the live value on a phone. |
| Battery, charging, RAM, storage, CPU cores (CPU load shown as unavailable) | 1 | 1 |
| Network, AI provider, voice engine, mic, language status | 2 | 1 (Home chips) |
| Quick controls: Wi-Fi/Bluetooth status → settings, torch, camera, phone settings | 5 | 1 (emulator shows them; torch needs real hardware) |
| States IDLE, LISTENING, THINKING, PROCESSING, SPEAKING, ERROR, OFFLINE | 1 | 1 |
| WAITING_FOR_PERMISSION | 5 | 1 (`AssistantState.WaitingPermission`, amber on both cores) |
| SPEAKING only after audio starts | 6 (it was set before synthesis) | 1 (engine test `speakingIsShownOnlyOnceAudioPlays`) |
| Modern layout on small screens | 6 (the core collapsed on a 640 dp emulator screen) | 1: compact strip layout |

### Chat (Part 12)

| Feature | Before | After |
|---|---|---|
| Text + voice chat, history, Stop, Retry | 1 | 1 |
| Copy response | 2 (long-press select) | 1 (Copy button) |
| New conversation, clear, search | 2 | 1 |
| Streaming | 5 | 5 |

### Security, performance, system integration (Parts 11, 13, 14)

| Item | Result |
|---|---|
| Hardcoded secrets | None found (`grep` for key patterns). Keys only in Keystore. |
| Secrets in logs | Voice logs record stages and lengths only, never text. |
| Memory guard | `looksSensitive` blocks passwords, OTPs, card numbers and keys from memories (also on import). |
| Background microphone | None. The mic is used only while the app is on screen. |
| Permissions | Runtime ones are asked at first use. New: `ACCESS_COARSE_LOCATION` (weather, asked on tap), `ACCESS_WIFI_STATE`, `BLUETOOTH` (≤ API 30, status only). |
| Documents | Read through Android's document picker (no storage permission). Kept in the app's private DB. Only matching passages are sent with a question. |
| Performance | Frame-capped animation, idle/sleep, thermal protection (see `docs/POWER_OPTIMIZATION_REPORT.md`). Weather is cached for 30 min. Stats refresh every 15 s and quick-control status every 5 s, only while visible. |

## Dependency-aware plan (executed)

1. Language engine (pure) → engine and voice wiring → Settings.
2. Knowledge domain (chunk, rank) → Room v4 + migration → repository + PDFBox → engine/prompt → Settings UI.
3. States: Speaking-on-audio, WaitingPermission, Processing label → both core styles.
4. Fallback provider, chat controls, memory export/import.
5. Dashboard fixes found by the recording (compact layout, location providers).
6. Tests, CI build, emulator recording, reports.
