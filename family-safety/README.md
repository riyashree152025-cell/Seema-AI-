# Seema Family Safety (consent-based)

Three parts: `backend/` (FastAPI), `companion-android/` (the app each family member installs themselves),
`seema_tool/` (Seema's "Papa kahan hain?" tool + live alerts).

## Consent rules built in
- The companion app is a normal app: visible icon, uninstallable, no device admin, no hiding.
- Location is sent only while the member's own **Share location** switch is ON, always with the
  notification "Sharing live location with family". OFF → the server deletes the last position at once.
- Only the latest position is kept (no history). Tokens are per phone and stored hashed.
- SOS is sent only after the member confirms (button or their own shake setting, while the app is open).

## Setup
1. Server (HTTPS only, behind Caddy/nginx or a tunnel):
   `pip install -r backend/requirements.txt` then
   `FAMILY_ADMIN_KEY=<32+ random chars> SOS_WEBHOOK_URL=<optional https> uvicorn main:app --port 8000`
2. Add people: `POST /api/admin/members` with header `X-Admin-Key` → each gets a one-time 8-digit code.
   Members: `{"user_id":"papa","display_name":"Papa","role":"member"}`; Seema: role `viewer`.
3. Each member types the server address, their ID and code into **their own** app (that is joining).
4. Seema: pair once as viewer, then set `FAMILY_SERVER` / `FAMILY_TOKEN` for `seema_tool`.
5. Safe zones: `POST /api/admin/zones {"name":"Home","lat":..,"lng":..,"radius_m":200}`.

## Tested
- `backend/test_backend.py`: pairing, consent ON/OFF, auth refusals, geofence exit, low battery, SOS over
  WebSocket — passes locally.
- The Android app and `seema_tool` are not built or run yet (no Android SDK here; no server online).

## Not done yet
- The Seema Android app does not call this yet: `family_tracker_tool.py` is the Python reference; the
  Kotlin port inside Seema (voice "Papa kahan hain", spoken alerts) is the next step.
- Optional SOS voice note: the server accepts it; the companion app does not record one yet.
