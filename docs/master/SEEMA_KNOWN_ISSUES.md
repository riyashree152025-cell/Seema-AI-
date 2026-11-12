# Seema AI — Known Issues

1. **Nothing is phone-tested** in the recent builds. User-reported, not yet confirmed fixed: answering / cutting a
   ringing call by voice; screen control unreliable; speech sometimes not recognised.
2. PC companion: `open_url` shell injection (A1); plain http (A2).
3. Vision: OCR and object recognition go to the cloud vision model (no on-device model). Ticket / bill scan
   only copies printed values; the user must check them in the confirm dialog.
4. STT streams to Deepgram when online (batch fallback). Barge-in on the loudspeaker is a test setting.
5. Routine steps must be phone commands Seema understands on the phone; others are refused in the editor.
6. Wake sensitivity applies to the offline detector only, from the next time listening starts.
7. Decisions waiting for the user: default PC IP in code, database encryption, splitting `AssistantEngine.kt`.
8. Android limits (will not be built): phone unlock, banking / UPI control, hearing the other side of a call.
9. During a phone call Android gives other apps' microphones silence, so "फ़ोन कट कर दो" cannot be heard once a
   call Seema placed is ringing / on. A "कॉल काटें" notification button (also on the lock screen) ends it instead.
10. Voice lock needs Seema as device admin (one tap on "Activate"). If neither that nor Accessibility is on,
    the first "फ़ोन लॉक करो" opens the Activate screen (only while Seema is on screen or Accessibility is running).

11. RailKit: the answer shapes are taken from RailKit's own SDK README (npm `railkit` 6.0.3), not yet from a live
    call with the user's key — first real PNR / train answers need a phone test. Direct API needs the Advance plan.
    The chart watch checks a waiting PNR every 30 minutes on the journey day and the day before; each check
    counts against the month's RailKit quota.

12. Voice ID is not built. sherpa-onnx 1.13.8 has SpeakerEmbeddingExtractor and a ~30 MB model downloads fine,
    but checking the speaker needs the recorded audio inside the live voice path; building that without a phone
    test risks breaking listening. To do after the voice path is phone-tested.
13. Mood is read from the words only (not voice tone or face) and only changes that one answer.
14. Live camera takes one photo per question (back camera unless "front"); each photo goes to the vision model.
15. Tatkal times (AC 10:00, non-AC 11:00, one day before the journey) are the railway rule as of 2026; if IRCTC
    changes them the countdown is wrong until the app is updated.
16. Bank SMS formats differ by bank: a bank whose credit / debit words or amount format are not recognised is
    missed by the inbox and refund checks.
