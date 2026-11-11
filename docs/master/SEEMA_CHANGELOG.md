# Seema AI — Change Log (this branch, newest first)

| Commit | Change | Test |
|---|---|---|
| `5304006` | Ringing call: quick answer (0.6 s cut), keeps listening after a wrong hearing, more words | CallCommandTest, engine test; CI 544 tests |
| `0e16a5d`–`8848e31` | Premium look, larger type, home tiles; design fixes from emulator screenshots | CI 543 tests; smoke screenshots |
| `b4c1614` | Living Eye: no crash when the eye has no room (crash at launch on small screens) | Smoke test: no FATAL |
| `2858930` | Offline voice, AI udhaar / Tatkal / lock actions, 20 s follow-up, khata screen + undo, wake lock | Engine + ledger + network tests |
| `8ac968c` | Manual voice stays manual, Cartesia moods, YouTube app, audio focus, 6.30 time, overlapping questions | CI 539 tests |
| `4b5ac1c`, `ec6c828` | Follow-up without the wake word, background listening, mid-sentence pauses, screen lock | StreamingTest, engine tests |
| `76496cc` | Name matching at word starts, udhaar confirmation, http guard, Tatkal sound + screen | ContactMatchTest, LocalNetworkTest |
| `59d6d52` | Room v5: clients and ledger; Tatkal list / cancel | Schema test, LedgerTest |
| `e8d81a3` | Tatkal exact alarms | TatkalAlarmTest |
| `14a66fc` | Contact names: number words, honorifics, spelling | ContactMatchTest |
| `7a70136`, `6b85406` | STT ends on steady noise, wake checks never pause, 28 s Gemini | EndpointDetectorTest, WakeCheckGateTest |
