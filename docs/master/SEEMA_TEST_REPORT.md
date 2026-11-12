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
| `aa5d210` (routines): build, unit tests, lint, release, key scan | PASS | CI run 43210666598 |
| `2f45f68` (SMS inbox): build, unit tests, lint, release, key scan | PASS | CI run 43215609511 |
| `409078c` (routine editor): build, unit tests, lint | PASS (release step cancelled by the next push) | CI run 43269148636 |
| `ac8ede9`: lint | FAIL — `Duration.toMinutesPart` needs Android 12 (min is 8); fixed in the next commit | CI run 43270493666 |
| JVM tests that run without Android (local) | PASS 507 / 507 | local, latest commit |
| Instrumented / UI tests | NOT TESTED (none exist) | — |
| Real phone: voice, wake word, calls, SMS, WhatsApp, screen control, alarms, camera | NOT TESTED | needs the user's phone |
| PC companion | NOT TESTED | never run in this environment |
| Battery / memory on the phone | NOT TESTED | emulator numbers only |
