# Voice pipeline audit (A–Z) — findings and fixes

Read end to end: AssistantEngine (listen loop, wake, turn end, streaming, fallbacks), DeepgramVoiceInput /
DeepgramLive / EndpointDetector / SharedSpeechCapture / MicHub, VoiceInputManager (Android recogniser),
Kws/Mic wake listeners, MicBargeIn / ConversationAudio, SpeechPipeline / TtsManager / AndroidTts / AudioPlayer,
OpenAiCompatible / Gemini providers, BrainRouter, IntentRouter, PromptBuilder, LocalCommands.

| # | Pillar | Root cause found | Fix | Commit |
|---|---|---|---|---|
| 1 | Input | Live stream sent the quiet mic audio unraised | StreamGain per frame | 6aee43e |
| 2 | Input | Any headphones (Bluetooth too) forced call-style audio: choppy voice, headset mic | wired/USB only | 6aee43e |
| 3 | Input | 1.2 s "Done" pause before the mic reopened after every command | 0.2 s in voice | 6aee43e |
| 4 | Input | Voice threshold 150 × 2.5 noise missed normal speech; 4.5 s wait restarted mid-speech | 110 × 2.2, 8 s | 081f205, a2e7abc |
| 5 | Input | With another app on screen she slept after every answer, even inside a conversation she was woken into | conversation continues until the quiet timer | this commit |
| 6 | Input | "Seema?" (name only) path: no wake session, job not cancellable | startWakeSession, job | this commit |
| 7 | STT | Deepgram "multi" wrote Hindi as Spanish / Telugu | nova-3:hi default, old choice reset | 081f205 |
| 8 | STT | Other Indian scripts not normalised (also in wake and barge-in text) | toDevanagari everywhere | c71b929, 6aee43e |
| 9 | STT | Unsure live transcript ended the turn | re-check on the whole raised recording | 6aee43e |
| 10 | STT | Key terms sent in Hindi mode could break the live link | only en / multi | 6aee43e |
| 11 | STT | Android recogniser: 3.5 s end-of-speech + 3 s minimum length | 1.5 s / 1.2 s, no minimum | this commit |
| 12 | Intent | Fixed phrase rules: every new wording failed | IntentRouter (short task request, ~1/5 of chat prompt) | bb81e1c |
| 13 | Intent | Number words, spoken class codes, "What's up" | hindiNumbersToDigits, spokenClean, fixCommonMishearings | 3b89022, deb00e8 |
| 14 | Intent | Notes / PNR / reminder list missing from the task list | added | this commit |
| 15 | Routing | Qwen3 hidden reasoning before every answer | reasoning_effort none | c71b929 |
| 16 | Routing | Silent provider waited 15 s (deep 28 s) before the backup | 8 s / 20 s | this commit |
| 17 | Routing | Each backup AI could take 45 s | 15 s each | this commit |
| 18 | TTS | A hanging voice service kept her silent up to 90 s (HTTP call timeout) | 8 s + 25 ms/letter per chunk, then next voice | this commit |
| 19 | TTS | First sentence spoken early, then a synthesis gap before the rest | wait 1.2 s for the whole reply first | this commit |
| 20 | TTS | Loudspeaker barge-in on by default: stopped at her own echo | off, old value ignored | c71b929 |

## Checked and found sound
- Mic released before the next recogniser (stop() stops the recorder at once); no double recorders (MicHub lease).
- Fast-failing "no speech" loop backs off and stops after several (MAX_FAST_NO_SPEECH).
- Markdown, URLs, citations, emoji stripped before speaking; ₹ / % / °C spoken as words.
- AndroidTts synthesis has its own timeout; a voice that returns silence is replaced.
- Empty AI reply → another AI; repeated text collapsed; model retired (404) → another model.

## Known limits (not code bugs)
- Home / back over other apps needs Accessibility (Android rule).
- Groq free plan ≈ 7,000 input tokens / minute; the fallback AI answers when it is used up.
- Nothing above is phone-tested yet.
