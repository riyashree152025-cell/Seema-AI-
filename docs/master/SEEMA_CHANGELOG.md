# Seema AI — Change Log (this branch, newest first)

| Commit | Change | Test |
|---|---|---|
| `b514247` | Live camera ("live camera chalu karo": 2 min, a fresh photo per question, "camera band karo" ends); mood from the user's words adds one instruction line for that answer only | LiveCameraTest, MoodHintTest, 2 engine tests |
| `ac8ede9` | Vision: ticket photo → PNR diary, bill photo → expense; values checked (10-digit PNR, real date, ₹1–10 lakh) and confirmed by the user before saving | DocumentScanTest |
| `256d2cd` | Udhaar khata: every entry of a person, remove any one after a confirm, copy the account; Tatkal cockpit: AC 10:00 / non-AC 11:00 countdown on Tools, "tatkal kab khulega" by voice | LedgerStatementTest, TatkalWindowTest |
| `7c26c0b` | "Hey Seema" sensitivity (low / normal / high) for the offline detector | WakeSensitivityTest |
| `409078c` | Automation screen: real routines (add, edit, switch, run, delete; refused steps named) instead of placeholder cards | RoutinesTest (editor checks); CI compile + lint |
| `2f45f68` | SMS inbox questions without an amount ("credit wale kitne message") answered on the phone; AI told the app reads the inbox | InboxCheckTest, engine test; CI run 43215609511 |
| `aa5d210` | Routines by voice / at a time; several commands in one sentence | RoutinesTest, engine tests; CI run 43210666598 |
| `eeb7ca3`, `18c32b6` | Life memory: "X ke baare mein sab batao", heads-up briefing, 8 PM tomorrow alert | LifeMemoryTest |
| `55c7f2b` and before | Wake-to-start, SMS refund check, encrypted backup, faster speech end, barge-in on the speaker, Phase 2 security | CI run 43207190602 |
| (next) | Booking note: "booking note banao" → Seema asks from/to, class (AC asks 3A or 2A), date, passenger name and birth year, then writes a note (text, saved or shared as a file). Passenger ages are this year minus the birth year. Note only: no ticket is booked | BookingNoteTest (pure), engine booking test |
| (next) | RailKit (api.railkit.in, key in Settings): PNR, trains between stations, live status, seats, fare — spoken from the service's answer only; waiting-ticket chart watch; Tatkal passenger age card (voice or ID photo) with copy buttons | RailKitTest, PassengerAgesTest, engine tests |
| (next) | Calls: name only (no digits read), "1 नंबर / 2 नंबर" choice, spoken choice dials at once, "लगाओ" = yes, speaker on, "कॉल काटें" notification button; voice lock opens the one-time Activate screen | ContactMatchTest, engine call tests |
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
