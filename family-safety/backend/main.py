"""Family Safety backend (FastAPI): consent-based live location, SOS and safe zones.

Run:  FAMILY_ADMIN_KEY=<long random> SOS_WEBHOOK_URL=<optional> uvicorn main:app --host 0.0.0.0 --port 8000
Serve it only over HTTPS (a reverse proxy such as Caddy / nginx, or a tunnel); tokens travel in headers.

Roles:  member = a family phone that may share its location (companion app)
        viewer = Seema AI on the owner's phone; may read members' shared locations and get live events
"""
from __future__ import annotations

import asyncio
import json
import logging
import os
import time
import urllib.request
from typing import Optional

from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, UploadFile, WebSocket, WebSocketDisconnect
from pydantic import BaseModel, Field

import geofence
from models import LocationUpdate, PairRequest, SafeZoneIn, add_member, db, init_db, member_for_token, pair

log = logging.getLogger("family")
ADMIN_KEY = os.environ.get("FAMILY_ADMIN_KEY", "")
SOS_WEBHOOK_URL = os.environ.get("SOS_WEBHOOK_URL", "")
MAX_VOICE_NOTE_BYTES = 2 * 1024 * 1024
OFF_MESSAGE = "Location sharing is currently turned OFF by the user"

app = FastAPI(title="Seema Family Safety", version="1.0")


@app.on_event("startup")
def _startup() -> None:
    init_db()
    if len(ADMIN_KEY) < 24:
        log.warning("FAMILY_ADMIN_KEY is missing or short: admin endpoints are disabled")


# ---------------------------------------------------------------- auth

def _bearer(authorization: Optional[str]) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "Missing token")
    return authorization[7:].strip()


def current_member(authorization: Optional[str] = Header(default=None)):
    row = member_for_token(_bearer(authorization))
    if row is None:
        raise HTTPException(401, "Invalid token")
    return row


def require_admin(x_admin_key: Optional[str] = Header(default=None)) -> None:
    import secrets
    if len(ADMIN_KEY) < 24 or not x_admin_key or not secrets.compare_digest(x_admin_key, ADMIN_KEY):
        raise HTTPException(403, "Admin key required")


# ---------------------------------------------------------------- live events (WebSocket)

class Hub:
    """Connected viewers (Seema AI). Events go to every viewer of the family."""

    def __init__(self) -> None:
        self.viewers: set[WebSocket] = set()

    async def broadcast(self, event: dict) -> None:
        dead = []
        for ws in list(self.viewers):
            try:
                await ws.send_json(event)
            except Exception:
                dead.append(ws)
        for ws in dead:
            self.viewers.discard(ws)


hub = Hub()


@app.websocket("/ws/events")
async def events(ws: WebSocket) -> None:
    # Browsers / OkHttp cannot always set headers on WebSockets: the viewer token comes as ?token=.
    token = ws.query_params.get("token", "")
    row = member_for_token(token) if token else None
    if row is None or row["role"] != "viewer":
        await ws.close(code=4401)
        return
    await ws.accept()
    hub.viewers.add(ws)
    try:
        while True:
            await ws.receive_text()  # keep-alive pings from the client; nothing else is accepted
    except WebSocketDisconnect:
        pass
    finally:
        hub.viewers.discard(ws)


# ---------------------------------------------------------------- admin: family setup

class NewMember(BaseModel):
    user_id: str = Field(min_length=1, max_length=64, pattern=r"^[a-z0-9_\-]+$")
    display_name: str = Field(min_length=1, max_length=40)
    role: str = Field(pattern=r"^(member|viewer)$")


@app.post("/api/admin/members", dependencies=[Depends(require_admin)])
def create_member(m: NewMember) -> dict:
    try:
        code = add_member(m.user_id, m.display_name, m.role)
    except Exception:
        raise HTTPException(409, "user_id already exists")
    # The code is shown once; the person types it into their own app (that is their consent to join).
    return {"user_id": m.user_id, "pairing_code": code}


@app.post("/api/admin/zones", dependencies=[Depends(require_admin)])
def create_zone(z: SafeZoneIn) -> dict:
    with db() as conn:
        cur = conn.execute("INSERT INTO safe_zones (name, lat, lng, radius_m) VALUES (?,?,?,?)", (z.name, z.lat, z.lng, z.radius_m))
    return {"id": cur.lastrowid, **z.model_dump()}


@app.post("/api/pair")
def pair_device(req: PairRequest) -> dict:
    token = pair(req.user_id, req.code)
    if token is None:
        raise HTTPException(403, "Wrong or already used pairing code")
    return {"token": token}


# ---------------------------------------------------------------- location

@app.post("/api/location/update")
async def location_update(u: LocationUpdate, me=Depends(current_member)) -> dict:
    if me["role"] != "member" or me["user_id"] != u.user_id:
        raise HTTPException(403, "A phone can only update its own location")
    now = time.time()
    with db() as conn:
        conn.execute(
            "INSERT OR REPLACE INTO sharing_state (user_id, is_active, changed_at) VALUES (?,?,?)",
            (u.user_id, int(u.is_sharing_active), now),
        )
        if not u.is_sharing_active:
            # OFF: the last position is deleted at once, nothing is stored.
            conn.execute("DELETE FROM latest_location WHERE user_id=?", (u.user_id,))
    if not u.is_sharing_active:
        geofence.forget_presence(u.user_id)
        await hub.broadcast({"type": "sharing", "user_id": u.user_id, "active": False})
        return {"stored": False, "message": OFF_MESSAGE}
    if abs(now - u.timestamp) > 24 * 3600:
        raise HTTPException(400, "Timestamp is not plausible")
    with db() as conn:
        conn.execute(
            "INSERT OR REPLACE INTO latest_location (user_id, lat, lng, battery_pct, speed, timestamp, received_at) VALUES (?,?,?,?,?,?,?)",
            (u.user_id, u.lat, u.lng, u.battery_pct, u.speed, u.timestamp, now),
        )
    for event in geofence.zone_events(u.user_id, u.lat, u.lng):
        await hub.broadcast({**event, "name": me["display_name"]})
    low = geofence.battery_event(u.user_id, u.battery_pct)
    if low:
        await hub.broadcast({**low, "name": me["display_name"]})
    return {"stored": True}


@app.get("/api/location/{user_id}")
def location_get(user_id: str, me=Depends(current_member)) -> dict:
    if me["role"] != "viewer" and me["user_id"] != user_id:
        raise HTTPException(403, "Not allowed")
    with db() as conn:
        member = conn.execute("SELECT display_name FROM members WHERE user_id=? AND role='member'", (user_id,)).fetchone()
        if member is None:
            raise HTTPException(404, "No such family member")
        state = conn.execute("SELECT is_active FROM sharing_state WHERE user_id=?", (user_id,)).fetchone()
        loc = conn.execute("SELECT * FROM latest_location WHERE user_id=?", (user_id,)).fetchone()
    if not state or not state["is_active"] or loc is None:
        return {"user_id": user_id, "name": member["display_name"], "sharing": False, "message": OFF_MESSAGE}
    return {
        "user_id": user_id, "name": member["display_name"], "sharing": True,
        "lat": loc["lat"], "lng": loc["lng"], "battery_pct": loc["battery_pct"], "speed": loc["speed"],
        "timestamp": loc["timestamp"], "age_seconds": round(time.time() - loc["timestamp"]),
    }


@app.get("/api/members")
def members_list(me=Depends(current_member)) -> list[dict]:
    if me["role"] != "viewer":
        raise HTTPException(403, "Not allowed")
    with db() as conn:
        rows = conn.execute("SELECT user_id, display_name FROM members WHERE role='member'").fetchall()
    return [{"user_id": r["user_id"], "name": r["display_name"]} for r in rows]


# ---------------------------------------------------------------- SOS

def _post_webhook(payload: dict) -> None:
    if not SOS_WEBHOOK_URL.startswith("https://"):
        return
    req = urllib.request.Request(SOS_WEBHOOK_URL, data=json.dumps(payload).encode(), headers={"Content-Type": "application/json"})
    try:
        urllib.request.urlopen(req, timeout=10).close()
    except Exception as e:  # the WebSocket alert still went out
        log.error("SOS webhook failed: %s", type(e).__name__)


@app.post("/api/sos/trigger")
async def sos_trigger(
    user_id: str = Form(...),
    lat: Optional[float] = Form(default=None),
    lng: Optional[float] = Form(default=None),
    battery_pct: Optional[int] = Form(default=None),
    voice_note: Optional[UploadFile] = File(default=None),
    me=Depends(current_member),
) -> dict:
    # SOS is sent by the member on purpose (button or their own shake setting): it works even when
    # routine location sharing is OFF, and it carries only the position of that moment.
    if me["role"] != "member" or me["user_id"] != user_id:
        raise HTTPException(403, "A phone can only send its own SOS")
    note_path = None
    if voice_note is not None:
        data = await voice_note.read(MAX_VOICE_NOTE_BYTES + 1)
        if len(data) > MAX_VOICE_NOTE_BYTES:
            raise HTTPException(413, "Voice note too large (2 MB max)")
        os.makedirs("sos_notes", exist_ok=True)
        note_path = f"sos_notes/{user_id}_{int(time.time())}.m4a"
        with open(note_path, "wb") as f:
            f.write(data)
    event = {
        "type": "sos", "user_id": user_id, "name": me["display_name"], "lat": lat, "lng": lng,
        "battery_pct": battery_pct, "time": time.time(), "voice_note": note_path is not None,
    }
    await hub.broadcast(event)
    await asyncio.to_thread(_post_webhook, event)
    return {"sent": True, "viewers_notified": len(hub.viewers)}
