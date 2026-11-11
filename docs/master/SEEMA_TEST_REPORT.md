# Seema AI — Test Report

| Test | Result | Reference |
|---|---|---|
| Kotlin compile, debug APK | PASS | Android CI run 42632211737 (`0e16a5d`) |
| Release APK (R8) | PASS | same run |
| JVM unit tests | PASS 543 / 543 | same run |
| Lint | PASS | same run |
| No API keys in the APKs | PASS | same run |
| Emulator launch, API 34 and 35 | PASS (no crash; screenshots of Home, AI Brain, Automation, Settings) | Smoke run 42632211764 |
| `5304006` (ringing call fix): build, 544 / 544 unit tests, lint, key scan | PASS | CI run 42940832043 |
| Instrumented / UI tests | NOT TESTED (none exist) | — |
| Real phone: voice, wake word, calls, SMS, WhatsApp, screen control, alarms, camera | NOT TESTED | needs the user's phone |
| PC companion | NOT TESTED | never run in this environment |
| Battery / memory on the phone | NOT TESTED | emulator numbers only |
