# Next phase: offline voice, wake word and screen-off listening

**Status:** B (offline TTS) is implemented — see `docs/OFFLINE_VOICE_ENGINES.md` (Piper and Kokoro-82M via sherpa-onnx 1.13.8, Voice Engine Manager, Automatic/Manual selection). It still has to be measured on the user's phone. A (offline ASR), C (wake word) and D (screen-off listening) are **NOT STARTED**. This plan is kept so the work is not forgotten. It was deferred on purpose because each item needs real model files and testing on the user's Android 15 phone. A cloud build and an emulator cannot prove any of it.

## 1. What exists today (so the next phase builds on it, not around it)

| Piece | Current implementation | File |
|---|---|---|
| Speech-to-text | Android SpeechRecognizer (start beep muted), or Deepgram from in-app `AudioRecord` | `data/voice/VoiceInputManager.kt`, `data/voice/DeepgramVoiceInput.kt`, `SwitchingVoiceInput` |
| Text-to-speech | Pipeline with fallback: Sarvam, ElevenLabs, Cartesia, Deepgram Aura, Android TTS | `domain/voice/SpeechPipeline.kt`, `data/tts/*` |
| Wake while asleep | `MicWakeListener` (on-phone voice detector) + Deepgram check for "Seema" | `data/voice/MicWakeListener.kt`, `domain/voice/WakeWord.kt` |
| Barge-in | `MicBargeIn` + `EchoAwareVad`, only with the echo canceller and STT | `data/voice/MicBargeIn.kt` |
| Streaming replies | AI streams (SSE); the first sentence is spoken while the rest arrives | `AssistantEngine.handleUserMessage`, `domain/assistant/Streaming.kt` |
| Response times | Measured per reply; P50/P95 under Tools → Response time | `TurnLatency`, `ToolsScreen` |

New engines plug in behind the existing interfaces:
- `VoiceInput` (listening)
- `TtsProvider` (a new `TtsEngine` entry)
- `WakeListener`

The engine and UI do not need to be rewritten.

## 2. Planned work

### A. Offline Hindi ASR (Sherpa-ONNX)
- The sherpa-onnx 1.13.8 AAR is already in the app (for offline TTS); reuse `SherpaTtsHost`'s loading pattern and `VoiceModelManager` for downloads.
- Choose a model only after checking each candidate's license, size, Hindi/Hinglish coverage and streaming support. Benchmark on the phone: cold start, real-time factor, RAM and heat.
- Download only with the user's consent: show the size and progress, verify the SHA-256 checksum, and store the model in app-private storage.
- Implement as a `VoiceInput` named `SherpaVoiceInput`, so it fits `SwitchingVoiceInput`.
- Proof required: works in airplane mode on the phone.

### B. Offline TTS (Sherpa-ONNX + Piper voices) — DONE in code, phone measurement pending
- List only the voices whose model files and licenses are actually available. Do not hard-code names.
- Implement as a `TtsProvider` (`TtsEngine.OFFLINE_NEURAL`), placed before Android TTS in the fallback order.
- Add a preview button per voice, plus a speed control where the voice supports it.

### C. "Hey Seema" wake word
- Evaluate Sherpa-ONNX keyword spotting. An English or Chinese model may not hear "Seema" reliably with Indian accents, so measure false wakes and misses on the phone before relying on it.
- Use a single microphone owner: never two components recording at once.

### D. Screen-off listening (Android 14/15 rules)
- A foreground service with `foregroundServiceType="microphone"` and the matching permission.
- Started only by the user, with a persistent notification and a Stop button.
- Never auto-started from boot. Android forbids starting a microphone service from the background.
- Handle phone calls, Bluetooth headsets, audio focus, lock/unlock and process death.

## 3. What is needed from the user
- The phone (Android 15, 6–8 GB RAM) for benchmarks and the airplane-mode test.
- Consent to download models of about 100–500 MB in total.
- Willingness to test "Hey Seema" in a quiet and in a noisy room.

## 4. Release gate for this phase
- Every item must be measured on the real phone before it is called done: latency P50/P95, memory, heat, false wakes, and the airplane-mode test.
- Emulator results are not accepted as proof.
