# Seema AI — Known Issues

1. **Nothing is phone-tested** in the recent builds. User-reported, not yet confirmed fixed: answering / cutting a
   ringing call by voice; screen control unreliable; speech sometimes not recognised.
2. PC companion: `open_url` shell injection (A1); plain http (A2).
3. Vision: no screenshot analysis, OCR, gallery picker, image comparison (see audit 5.x).
4. STT is batch, not streaming. Barge-in needs headphones.
5. Automation routines on screen do little or nothing (A6).
6. Wake-word sensitivity task still open.
7. Decisions waiting for the user: default PC IP in code, database encryption, splitting `AssistantEngine.kt`.
8. Android limits (will not be built): phone unlock, banking / UPI control, hearing the other side of a call.
9. During a phone call Android gives other apps' microphones silence, so "फ़ोन कट कर दो" cannot be heard once a
   call Seema placed is ringing / on. A "कॉल काटें" notification button (also on the lock screen) ends it instead.
10. Voice lock needs Seema as device admin (one tap on "Activate"). If neither that nor Accessibility is on,
    the first "फ़ोन लॉक करो" opens the Activate screen (only while Seema is on screen or Accessibility is running).
