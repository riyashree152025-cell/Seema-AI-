# SEEMA AI — Implementation Report

Branch `claude/dashboard-weather-controls`, built on `main` `6a88c48` (v1.7.1). The architecture is unchanged: the same `AppContainer`, `AssistantEngine`, ports and screens. New work plugs into the existing interfaces.

## 1. What was already present
- The `AssistantEngine` orchestrator, with:
  - Gemini and Groq adapters
  - tool actions (call, SMS, WhatsApp, timers, alarms, calendar, music, settings, khata, PNR, web search, research)
  - memories and history in Room
  - keys in the Keystore
- The voice pipeline:
  - Android and Deepgram speech recognition
  - Sarvam, ElevenLabs, Cartesia, Deepgram and Android voices, with fallback between them
  - barge-in, wake word, push-to-talk, session and always-listening modes
- Both animated cores (the original v1.6 Arc Reactor and the 3D Holographic Core), a core selector in Settings, power and thermal management.

## 2. What was missing
- Bengali
- Knowledge Base / RAG
- Weather
- Quick controls
- The WAITING_FOR_PERMISSION state
- Provider fallback
- Chat: new conversation, search, copy button, repeat, mute
- Voice pitch
- Memory export/import
- Streaming, a local LLM, offline neural voices and STT models, and project-specific memory. These are still missing; see §6.

## 3. What was broken
- "Speaking" was shown before any audio played.
- English was offered as a reply language.
- On a small screen, the 3D dashboard pushed the 3D core to zero height. The first demo recording showed this.

## 4. What was recovered
The original Arc Reactor. `ArcReactor.kt` and `ReactorStyle.kt` match git `796d89e` (v1.6.0) exactly, apart from one new colour entry for the new WaitingPermission state.

## 5. What was implemented

| Area | What | Main files |
|---|---|---|
| Language | Hindi/Bengali script detection, Auto/manual choice, conversation continuity, "ask if mixed", Bengali phrases, STT extra languages, TTS per chunk (`bn-IN`), no English voice for Bengali | `domain/language/Language.kt`, `AssistantEngine.kt`, `PromptBuilder.kt`, `SpeechPipeline.kt`, `TtsManager.kt`, `TtsProviders.kt`, `AndroidTts.kt`, `VoiceInputManager.kt`, `AppContainer.kt` |
| Knowledge Base | Import PDF/DOCX/TXT/MD, chunking, BM25 search on the phone, passages with `[Doc: name §n]` in the prompt, remove, rebuild index, Room v3→v4 migration | `domain/knowledge/Knowledge.kt`, `data/knowledge/*`, `data/db/Entities.kt`, `RajuDatabase.kt`, `ui/settings/KnowledgeSettings.kt` |
| States | Speaking only when audio plays (`Speaker.playing`); WaitingPermission (mic denied, permission dialogs) on both cores; Executing shown as "Processing" | `AssistantState.kt`, `Ports.kt`, `ReactorStyle.kt`, `HoloCore.kt`, `HomeScreen.kt` |
| Providers | Auto fallback to another provider with a saved key, announced in the chat | `AssistantEngine.fallbackReply` |
| Chat | New conversation, search all saved chats, mute/unmute, Copy, 🔊 Repeat | `ChatScreen.kt`, `ChatViewModel.kt`, `ChatDao.kt`, `ConversationRepository.kt` |
| Voice | Pitch (Android voice), Bengali voice status, repeat last reply | `UserSettings.kt`, `SettingsRepository.kt`, `SettingsScreen.kt` |
| Memory | Export/import JSON (duplicates and secrets skipped) | `SettingsViewModel.kt`, `KnowledgeSettings.kt` |
| Dashboard | Real weather (Open-Meteo), quick controls (Wi-Fi, BT, torch, camera, settings), rolling clock digits, compact layout for small screens | `data/weather/Weather.kt`, `ModernHud.kt`, `HomeScreen.kt` |
| Proof | The emulator test records the running app (both cores, state reactions, live switch) and publishes it | `scripts/smoke.sh`, `.github/workflows/smoke.yml` |

## 6. Not implemented, and why
- **Local/offline LLM.** A useful on-device model (for example Gemma 2B through MediaPipe or llama.cpp) is 1–2 GB. It must be downloaded with the user's consent and benchmarked on the actual phone. None of that could be done or verified here, so nothing is claimed.
- **Offline neural STT/TTS** (Whisper, Sherpa/Piper, Bhashini models).
  - Each needs native libraries and 50–300 MB models per language.
  - Their Bengali and Hindi female voice quality has to be heard on a real phone.
  - The offline options in the app today are:
    - Android TTS, if the phone has the Hindi/Bengali voice data
    - Android speech recognition, if the offline language pack is installed
- **Streaming replies.** This needs SSE parsing in both adapters and incremental speech. It was postponed to keep this change reviewable. Replies are short, so the gain is small.
- **Project-specific memory.** Memories are one list. A category column and UI would be a schema change of their own.
- **Khata/PNR summaries in Bengali.** They are produced in Hindi.

## 7. Dependencies added
- `com.tom-roush:pdfbox-android:2.0.27.0`. This is the only way here to extract text from PDFs reliably on Android: Android's `PdfRenderer` renders images only. It adds about 3 MB. ProGuard keep rules were added.
- No other new libraries. Weather uses the existing OkHttp client and a free API that needs no key. DOCX is read with `java.util.zip`.

## 8. Permissions added
| Permission | Why | When asked |
|---|---|---|
| `ACCESS_COARSE_LOCATION` | Weather, at city level | When the weather tile is tapped |
| `ACCESS_WIFI_STATE` | Shows whether Wi-Fi is on or off | Never asked; granted at install |
| `BLUETOOTH` (API ≤ 30) | Shows whether Bluetooth is on or off | Never asked; granted at install |

## 9. What needs internet and what works offline
- **Needs internet:**
  - AI replies (Gemini, Groq)
  - web search and research
  - weather
  - cloud voices: Sarvam, ElevenLabs, Cartesia, Deepgram
- **Works offline:**
  - both animated cores, the clock and the phone status
  - khata and PNR diary
  - memories, chat history and the Knowledge Base on the phone. Searching documents works offline, but the answer still needs the AI.
  - the Android voice and the Android recogniser, when their offline language data is installed

  Without internet, Seema shows Offline and says that AI replies need internet.
