# Seema AI — Repository Audit (Master Prompt, Step 1)

Audited at commit `5304006` on branch `claude/voice-fixes` (PR #30, not merged; `main` is at `b58c4d3`).
Method: source reading and searches of the whole `app/` tree, the manifest, Gradle files, CI workflows and the
PC companion; CI results of this branch. No phone was available: nothing here is "Phone Tested".
No production code was changed for this audit.

## 1. Project facts

| Item | Value |
|---|---|
| Language / UI | Kotlin, Jetpack Compose (Material 3), single activity |
| SDK | min 26, target 35, compile 35; version 1.8.0 (code 21) |
| Size | 167 Kotlin source files, 67 test files (544 JVM unit tests), 0 instrumented tests |
| Storage | Room v5 (chat, memories, action log, expenses, journeys, knowledge base, clients, ledger), DataStore settings, Android Keystore for API keys |
| AI providers | Gemini, Groq, Cerebras, OpenRouter, DeepSeek, Ollama (OpenAI-compatible) with fallback and a FAST/DEEP router |
| Voice | Deepgram STT (batch) or Android recogniser; TTS: Cartesia, Sarvam, ElevenLabs, Deepgram, Piper/Kokoro (sherpa-onnx, offline), Android TTS; offline "Hey Seema" (sherpa KWS) |
| Background | Microphone foreground service (wake word), WorkManager (briefing, PNR watch), exact alarms (Tatkal), notification listener, call screening, accessibility service |
| CI | `Android CI` (debug + release build, unit tests, lint, key scan, test APK) and `Emulator smoke test` (API 34/35, screenshots) |
| PC companion | `scripts/pc_companion/seema_pc_companion.py` (HTTP, bearer token, memory/conversation sync, allowed PC actions) |

## 2. Architecture

```
ui/ (Compose screens, ViewModels)
   │
di/AppContainer  ── wires everything (manual DI)
   │
domain/assistant/AssistantEngine  ── one turn: hear → LocalCommands (no AI) or AI provider → actions → speak
   ├─ domain/* (pure logic, unit-tested): LocalCommands, ContactMatch, Ledger, Tatkal, PNR, EndpointDetector, BrainRouter …
   ├─ data/ai (providers)      data/voice (MicHub, STT, wake)     data/tts (voices)
   ├─ data/tools/AndroidDeviceTools (calls, SMS, apps, alarms, camera)   accessibility/ (screen read / tap / type / WhatsApp)
   ├─ data/db (Room)           data/knowledge (PDF/DOCX/TXT search)      data/sync (PC companion client)
   └─ notifications/ (listener, call screening, Tatkal alarms, WorkManager)   voice/SeemaWakeService
```

## 3. Feature status

Legend: COMPLETE = implemented and build-verified with unit tests (still not phone-tested unless said);
PARTIAL; MISSING; BROKEN; UNVERIFIED = exists, cannot be verified without a phone.

### Vision (master prompt phase 3)
| # | Feature | Status | Evidence / gap |
|---|---|---|---|
| 5.1 | Live screen understanding | PARTIAL | Accessibility text of the screen is read and summarised; no visual (pixel) understanding |
| 5.2 | Screenshot AI analysis | MISSING | Screenshot is taken by the system (saved to gallery); the image is never given to a vision model |
| 5.3 | Live camera vision | PARTIAL | One photo on request (front/back) described by Gemini; no live preview / continuous mode |
| 5.4 | Object recognition | PARTIAL | Only through the cloud photo description; no local model |
| 5.5 | Scene recognition | PARTIAL | Same as 5.4 |
| 5.6 | Dedicated OCR | MISSING | No OCR library; scanned PDFs are not readable |
| 5.7 | Notification intelligence | PARTIAL | Announce / read on consent, last message; no summary, categories or Q&A |
| 5.8 | Gallery image analysis | MISSING | No Photo Picker |
| 5.9 | Visual question answering | PARTIAL | Only for a freshly taken photo, Gemini only |
| 5.10 | Image comparison | MISSING | — |

### Personal AI brain (phase 4)
| Feature | Status | Evidence / gap |
|---|---|---|
| Long-term memory (view, edit, delete) | COMPLETE | Room + Memory screen; sensitive text filter (`looksSensitive`) |
| Personal context in prompts | COMPLETE | Memory and business context added per request with limits |
| Smart learning from feedback | MISSING | — |
| Multi-provider, test, model list, fallback, rate-limit notes | COMPLETE | 6 providers; unit tests with MockWebServer |
| Hybrid routing FAST/DEEP | COMPLETE | `BrainRouter`; local commands need no AI |
| Local LLM on the phone | MISSING | Ollama on a PC only |
| Vision-capable model selection | PARTIAL | Vision goes to Gemini only; no capability table for other providers |

### Mobile control (phase 5)
| Feature | Status | Evidence / gap |
|---|---|---|
| Open apps, home, back, settings pages | COMPLETE | Unit-tested routes; phone-unverified |
| Contact search (fuzzy, numbers, honorifics, choice by voice) | COMPLETE | 20 unit tests |
| Calls (dial, answer, end, announce, reply by SMS) | UNVERIFIED | User reports answer / cut by voice failing; fixed in `5304006`, needs the phone |
| SMS | UNVERIFIED | Confirmed send path; needs the phone |
| Screen read / tap / scroll / type | UNVERIFIED | User reports it unreliable; no logs received yet |
| WhatsApp message / file / call | UNVERIFIED | Flow with recovery; depends on WhatsApp's screens |
| Clipboard | PARTIAL | Copy only; no "what is on my clipboard" |
| File search / open / organise | MISSING | — |
| Screen lock | COMPLETE | Accessibility global action; unlock is not possible on Android (by design) |

### Voice (phase 6)
| Feature | Status | Evidence / gap |
|---|---|---|
| Wake word "Hey Seema" (offline) | UNVERIFIED | Implemented; sensitivity task still open |
| Continuous conversation, VAD, endpointing | COMPLETE | Adaptive noise floor, mid-sentence wait; unit tests |
| Interrupt while speaking (barge-in) | PARTIAL | Headphones only |
| STT | PARTIAL | Batch (not streaming): about 1.5–2 s after the sentence |
| TTS, Hindi / Bengali, language switch | COMPLETE | Unit tests; voices phone-unverified |
| Offline commands | COMPLETE | Phone recogniser is used without internet |
| Voice authentication | MISSING | — |
| Microphone privacy (OFF switch releases the mic) | COMPLETE | Unit tests |

### Assistant and automation (phase 7)
| Feature | Status | Evidence / gap |
|---|---|---|
| Alarms, timers, Tatkal exact alarms (set, list, cancel) | COMPLETE | Unit tests; phone-unverified |
| Reminders (own, by date) | MISSING | Only the clock app's alarm / timer and Tatkal |
| Calendar | PARTIAL | Opens / inserts via intent in places; no reading of events |
| WhatsApp assistance | UNVERIFIED | See mobile control |
| Email | MISSING | — |
| Notification replies | MISSING | No `RemoteInput` |
| Daily briefing, weekly report | COMPLETE | WorkManager; unit tests |
| Location automation | MISSING | — |
| Routines | PARTIAL | Automation screen with switches; routines mostly do nothing yet |
| Multi-step tasks, progress, retry | PARTIAL | One sentence = one action (plus AI action lists); WhatsApp flow retries |

### Security and privacy (phase 8)
| Feature | Status | Evidence / gap |
|---|---|---|
| API keys in Android Keystore, no keys in APK or logs | COMPLETE | CI key scan |
| Confirmation before calls / messages / money entries | COMPLETE | Engine tests |
| Emergency stop (OFF) | COMPLETE | Unit tests |
| Activity history | COMPLETE | Action log screen |
| http only to the home network | COMPLETE | `CleartextGuard`, tests |
| Privacy centre (one page for camera, mic, screen, gallery, cloud upload) | PARTIAL | Setup Doctor shows permissions; no single control centre, no cloud-upload switch |
| App lock (biometric) | MISSING | — |
| Voice authentication | MISSING | — |
| Database encryption | MISSING | Awaiting the user's decision (data migration risk) |

### Advanced intelligence (phase 9)
| Feature | Status | Evidence / gap |
|---|---|---|
| PDF / DOCX / TXT knowledge search | COMPLETE | Unit tests |
| Excel | MISSING | — |
| Translation | PARTIAL | Through the AI only; no live two-way mode |
| Web research, live information, weather, mandi | COMPLETE | Tavily + Open-Meteo; unit tests |
| Navigation | PARTIAL | Opens Maps with a destination |
| Health | MISSING | — |
| Device troubleshooting | PARTIAL | Setup Doctor |
| Predictive assistance | MISSING | — |

### PC companion (phase 10)
| Feature | Status | Evidence / gap |
|---|---|---|
| Token auth, status, memory and conversation sync, Ollama model list | UNVERIFIED | Code exists on both sides; never run here |
| PC actions | PARTIAL | Allow-list; `open_url` only |
| Encryption | MISSING | Plain http on the LAN (token in a header) |
| Pairing, file transfer, remote troubleshooting | MISSING | — |

### Reliability, performance, UI (phases 11–13)
| Feature | Status | Evidence / gap |
|---|---|---|
| Provider fallback, timeouts, honest failure texts | COMPLETE | Unit tests |
| Self-diagnostics (Setup Doctor, mic and voice self-test) | COMPLETE | Phone-unverified |
| Task manager (several tasks, cancel, resume) | MISSING | One job at a time |
| Offline queue | MISSING | — |
| Power: idle / sleep animation, thermal | COMPLETE | Smoke test measurements |
| UI | COMPLETE | Living Eye + premium ink / gold look, verified on emulator screenshots (Pixel 6 size) |

## 4. Conflicts between the master prompt and what the user approved in this project

These are not changed without the user's word:
1. **Theme.** The prompt says "crystal red" and "heartbeat / pulsing arc". The user has since approved the Living Eye
   (photo-real iris) and asked for the premium look now in place. The UI is kept as approved.
2. **Branch.** The prompt asks for a new development branch. Work stays on `claude/voice-fixes` (the user's rule:
   `main` is never changed, no PR unless asked). Every change is recoverable through Git; the original state is `b58c4d3`.
3. **PIN / pattern unlock and in-call listening** stay out: Android does not allow them (prompt rule 13 agrees).

## 5. Bugs and risks found in this audit

| # | Severity | Finding |
|---|---|---|
| A1 | HIGH (security) | PC companion `open_url` runs `os.system('start "" "<url>"')`: a URL containing `"` and `&` can run a command on the PC. Needs `webbrowser.open` / no shell. Token-protected, LAN only. |
| A2 | HIGH | PC companion traffic (token, memories, conversations) is plain http. |
| A3 | MEDIUM | No instrumented (on-device) tests at all; every phone feature is unverified until the user tests it. |
| A4 | MEDIUM | `AssistantEngine.kt` is over 3,100 lines and `SettingsScreen.kt` over 2,500: change risk. |
| A5 | MEDIUM | STT is batch: 1.5–2 s extra per turn. |
| A6 | MEDIUM | Automation screen shows routines ("Good Morning", "Smart Home") that do little or nothing: looks working, is not. |
| A7 | LOW | Home dock used to claim "100% ONLINE" regardless of state (text changed; state is still not live). |
| A8 | LOW | 20+ stale `claude/*` remote branches. |
| A9 | OPEN | User decisions pending: PC default IP in code, database encryption, engine split. |

## 6. Permissions

INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, BLUETOOTH, RECORD_AUDIO, CAMERA, ACCESS_COARSE_LOCATION,
MODIFY_AUDIO_SETTINGS, READ_CONTACTS, READ_CALL_LOG, POST_NOTIFICATIONS, CALL_PHONE, ANSWER_PHONE_CALLS, SEND_SMS,
FOREGROUND_SERVICE(+MICROPHONE), WAKE_LOCK, SCHEDULE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED, SET_ALARM.
Special access: notification listener, accessibility service, call-screening role.
All are used by an existing feature; none found unused. READ_SMS, storage and fine location are not requested.

## 7. Prioritised plan (order of the master prompt, sized honestly)

| Step | Work | Size |
|---|---|---|
| 4 | Repairs: A1, A2 (https or pairing key), A6 (honest routines), call answer on the phone | small–medium |
| 5 | Vision: Photo Picker + image Q&A + compare (cloud vision), screenshot analysis via accessibility screenshot API, OCR (ML Kit, on-device) | large |
| 6 | Brain: vision capability table, feedback learning | medium |
| 7 | Mobile control: clipboard question, file search via the system picker | medium |
| 8 | Voice: streaming STT, barge-in without headphones | large |
| 9 | Automation: own reminders, calendar read, notification reply, real routines | large |
| 10 | Privacy centre, app lock | medium |
| 11 | Excel, live translation | medium |
| 12 | PC: pairing, encryption, file transfer | large |
| 13–15 | Task manager, performance pass, UI integration | medium |
| 16–20 | Integration, regression, security, release, delivery | medium |

Not possible on Android and therefore not planned: unlocking the phone, operating banking / UPI apps, hearing the
other side of a phone call, silent background screen capture.
