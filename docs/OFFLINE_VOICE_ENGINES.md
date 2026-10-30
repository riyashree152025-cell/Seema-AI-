# Offline voice engines (Piper, Kokoro-82M via sherpa-onnx)

Seema's AI brain stays online (Gemini / Groq / Cerebras). Her **voice** can be made on the phone:
a voice model is downloaded once (Settings → Voice Engine Manager) and then every reply is spoken
offline, with no voice-API cost.

## Engines

| Engine | Role | Model | Download | Licence |
|---|---|---|---|---|
| Kokoro-82M | natural, human-like | `kokoro-int8-multi-lang-v1_0` (speakers 31 hf_alpha ♀, 32 hf_beta ♀, 33/34 male) | 132 MB | Apache-2.0 |
| Piper | fast | `vits-piper-hi_IN-priyamvada-medium-int8` (♀) | 21 MB | CC BY-NC-SA 4.0 (non-commercial) |
| Android TTS | backup | phone's own voice | — | — |

Runtime: **sherpa-onnx 1.13.8** AAR (downloaded by Gradle, SHA-256 checked, not in git). 1.13.8 is
the last release that still bundles espeak-ng; 2.x removed it, and Piper/Kokoro need it to read Hindi.
ABIs shipped: arm64-v8a (phones) and x86_64 (emulator test).

Every catalog entry (`domain/voice/LocalVoice.kt`) was checked against the real archive:

| Archive | Bytes | SHA-256 |
|---|---|---|
| vits-piper-hi_IN-priyamvada-medium-int8.tar.bz2 | 21,097,319 | 2997c7bb…92f2 |
| kokoro-int8-multi-lang-v1_0.tar.bz2 | 132,353,410 | 72c34723…0653 |

## Measured (server CPU, 2 threads, Hindi text — not a phone)

| Voice | Load | RTF (synth ÷ audio) | First sentence | Median pitch |
|---|---|---|---|---|
| Piper priyamvada | 2.0 s | 0.27 | 0.39 s | 222 Hz (female) |
| Kokoro hf_alpha | 1.8 s | 1.41 | 3.6 s | 218 Hz (female) |
| Kokoro hf_beta | — | 1.49 | 4.0 s | 190 Hz (female) |

Kokoro was slower than real time on that CPU, so on many phones Automatic mode will pick Piper.
The real numbers for a phone come from Voice Engine Manager → **Test speed on this phone**
(cold/warm first sentence, RTF, CPU %, app memory, battery current).

## Automatic / Manual selection (`voiceEngineOrder`)

* **Automatic** (default): once a model is installed only on-phone engines are used — Kokoro first if
  its measured RTF ≤ 0.8 (or not yet measured), then Piper, then Android TTS. An engine that failed
  2× in a row is moved behind the others until it works again. Until a model is downloaded the
  voice chosen under Voice is kept, so nothing changes for existing users.
* **Manual**: the chosen engine first, then the offline engines, then Android TTS.

Every engine is wrapped in `MeasuredTtsProvider`: RTF, time per sentence, successes and failures
are stored (SharedPreferences `voice_engine_stats`) and shown in Voice Engine Manager.

Streaming TTS (speaking the first sentence while the rest of the answer arrives) and barge-in
already work and apply to the offline engines too (barge-in needs hands-free mode's echo canceller).

## Downloads

`VoiceModelManager`: starts only on tap (warns on mobile data), resumes with HTTP Range after a
broken connection (4 attempts), verifies SHA-256, extracts the .tar.bz2 safely (no path traversal,
links skipped) into app-private storage, writes `installed.txt` (version + sha). Update appears when
the catalog version or checksum changes. Delete frees the space and the native memory.

## Adding an engine

1. Add a `TtsEngine` value and a `LocalVoiceModel` entry (URL, exact size, SHA-256, files, speakers, licence).
2. If it is a new sherpa-onnx model type, add a `LocalModelKind` and its config in `SherpaTtsHost.engineFor`.
3. Register a `LocalTtsProvider` for it in `AppContainer` and decide its place in `voiceEngineOrder`.

## Limits (honest)

* No Bengali offline voice yet → Bengali replies use Android TTS / Sarvam.
* Hinglish in Latin script is read by espeak's Hindi rules; quality not verified. Voice replies are
  asked for in Devanagari, which is read correctly.
* The APK grows by ~30 MB per ABI (native runtime).
* Kokoro uses ~300+ MB RAM when loaded; it is released when the app goes to the background.
* Piper Priyamvada's licence is non-commercial.

## Proof

* Unit tests: `LocalVoiceTest`, `VoiceModelFilesTest`.
* Emulator (CI `smoke.yml`, RUN 7): `--es seema_voice_test PIPER|KOKORO` downloads the real model,
  speaks through the normal path and logs `VOICE_TEST … result=PASS` with RTF/CPU/memory.
* Real phone: must be measured by the user with "Test speed on this phone".
