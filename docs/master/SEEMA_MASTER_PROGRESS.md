# Seema AI — Master Progress

Branch `claude/voice-fixes` · baseline of the original project: `main` at `b58c4d3` (untouched) · PR #30 open (not merged).
Status words: **Code Complete** (written, unit-tested on the JVM) · **Build Verified** (Android CI green) · **Phone Tested** (checked on the user's phone).

| Area | State |
|---|---|
| Voice: "Hey Seema" to start, sleep after silence, faster end of speech, live transcript, barge-in (speaker too), wake sensitivity | Code Complete · Build Verified · not Phone Tested |
| SMS: refund of an amount, inbox / credit / debit counts and totals | Code Complete · Build Verified · not Phone Tested |
| Life memory, heads-up briefing, 8 PM tomorrow alert | Code Complete · Build Verified · not Phone Tested |
| Routines: by voice, at a time, several commands in one sentence, editor on the Automation screen | Code Complete · Build Verified · not Phone Tested |
| Udhaar khata per person, Tatkal cockpit, ticket / bill photo scan | Code Complete · Build: see test report · not Phone Tested |
| Live camera, mood from words | Code Complete · Build: see test report · not Phone Tested |
| Encrypted backup / restore | Code Complete · Build Verified · not Phone Tested |
| Voice ID (recognise the user's voice) | NOT BUILT — see known issues |
| Offline AI, smart home, smartwatch | DROPPED — the user does not need them now |
| PC pairing | SKIPPED — the user said not to work on it |

## Next exact task
The user tests the APK on the phone; fix what the phone shows. Voice ID only after the voice path is phone-tested.

## Rules in force
`main` is never changed or merged · no PR unless asked · no releases published by CI · no secrets in code, logs or chat ·
confirmations before calls, messages and money entries · PIN / pattern never stored.
