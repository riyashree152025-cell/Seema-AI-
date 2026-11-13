"""Seema AI tool: "Papa kahan hain?" -> a short spoken answer from the family's shared location.

check_family_location(member_name, my_lat, my_lng) -> str     (function-calling tool)
FamilyEventListener(...).run(announce)                        (proactive SOS / geofence / low battery)

Config (environment):
  FAMILY_SERVER   https://your-server            (HTTPS only)
  FAMILY_TOKEN    the viewer token Seema got from /api/pair
Reverse geocoding uses OpenStreetMap Nominatim (1 request/second, generic User-Agent, results cached).
"""
from __future__ import annotations

import asyncio
import json
import math
import os
import time
import urllib.parse
import urllib.request
from typing import Awaitable, Callable, Optional

SERVER = os.environ.get("FAMILY_SERVER", "").rstrip("/")
TOKEN = os.environ.get("FAMILY_TOKEN", "")
USER_AGENT = "SeemaFamilySafety/1.0"
_geo_cache: dict[tuple[float, float], str] = {}
_last_geo_call = 0.0

# Tool definition for an LLM (OpenAI / Groq / Gemini style function calling).
TOOL_SPEC = {
    "name": "check_family_location",
    "description": "Where a family member is right now, if they chose to share their location. Use for 'Papa kahan hain', 'Didi ghar pahunchi?'.",
    "parameters": {
        "type": "object",
        "properties": {"member_name": {"type": "string", "description": "As the user said it: Papa, Mummy, Didi …"}},
        "required": ["member_name"],
    },
}


def _get(path: str) -> dict | list:
    if not SERVER.startswith("https://") or not TOKEN:
        raise RuntimeError("Family server is not set up (FAMILY_SERVER must be https, FAMILY_TOKEN set).")
    req = urllib.request.Request(f"{SERVER}{path}", headers={"Authorization": f"Bearer {TOKEN}"})
    with urllib.request.urlopen(req, timeout=10) as r:
        return json.loads(r.read().decode())


def _haversine_km(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    p1, p2 = math.radians(lat1), math.radians(lat2)
    a = math.sin((p2 - p1) / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(math.radians(lng2 - lng1) / 2) ** 2
    return 2 * 6371.0 * math.asin(math.sqrt(a))


def _area_name(lat: float, lng: float) -> Optional[str]:
    """Neighbourhood / town for a point (cached to ~100 m; Nominatim's 1 request/s rule kept)."""
    global _last_geo_call
    key = (round(lat, 3), round(lng, 3))
    if key in _geo_cache:
        return _geo_cache[key]
    wait = 1.0 - (time.time() - _last_geo_call)
    if wait > 0:
        time.sleep(wait)
    _last_geo_call = time.time()
    q = urllib.parse.urlencode({"lat": lat, "lon": lng, "format": "jsonv2", "zoom": 16, "accept-language": "hi"})
    try:
        req = urllib.request.Request(f"https://nominatim.openstreetmap.org/reverse?{q}", headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(req, timeout=8) as r:
            addr = json.loads(r.read().decode()).get("address", {})
    except Exception:
        return None
    name = next((addr[k] for k in ("suburb", "neighbourhood", "village", "town", "city_district", "city") if addr.get(k)), None)
    _geo_cache[key] = name
    return name


def _match_member(name: str, members: list[dict]) -> Optional[dict]:
    n = name.strip().lower()
    return next((m for m in members if m["name"].lower() == n), None) or next(
        (m for m in members if n in m["name"].lower() or m["name"].lower() in n), None
    )


def check_family_location(member_name: str, my_lat: Optional[float] = None, my_lng: Optional[float] = None) -> str:
    """The spoken answer (Hindi). Never guesses: no shared location -> says so."""
    try:
        members = _get("/api/members")
        m = _match_member(member_name, members)  # type: ignore[arg-type]
        if m is None:
            return f"{member_name} family list में नहीं हैं।"
        loc = _get(f"/api/location/{urllib.parse.quote(m['user_id'])}")
    except Exception as e:
        return f"अभी family server से बात नहीं हो पाई ({type(e).__name__})।"
    who = m["name"]
    if not loc.get("sharing"):  # type: ignore[union-attr]
        return f"{who} ने अभी location sharing बंद रखी है।"
    area = _area_name(loc["lat"], loc["lng"])  # type: ignore[index]
    parts = [f"{who} {area + ' के पास' if area else 'की location मिली'} हैं"]
    if my_lat is not None and my_lng is not None:
        km = _haversine_km(my_lat, my_lng, loc["lat"], loc["lng"])  # type: ignore[index]
        parts.append(f"आपसे लगभग {km:.1f} किलोमीटर दूर" if km >= 0.2 else "आपके बिल्कुल पास")
    text = ", ".join(parts) + "।"
    if loc.get("battery_pct") is not None:  # type: ignore[union-attr]
        text += f" उनके फ़ोन की बैटरी {loc['battery_pct']}% है।"  # type: ignore[index]
    age_min = int(loc.get("age_seconds", 0)) // 60  # type: ignore[union-attr]
    if age_min >= 30:
        text += f" (यह {age_min} मिनट पुरानी जानकारी है।)"
    return text


def describe_event(e: dict) -> Optional[str]:
    """A proactive announcement for one live event, or None for events not worth speaking."""
    who = e.get("name") or e.get("user_id", "")
    match e.get("type"):
        case "sos":
            where = f" Location: {e['lat']:.5f}, {e['lng']:.5f}." if e.get("lat") is not None else ""
            return f"⚠️ {who} ने SOS भेजा है!{where}" + (" Voice note भी है।" if e.get("voice_note") else "")
        case "geofence":
            return f"{who} {e['zone']} {'पहुँच गए' if e['event'] == 'enter' else 'से निकल गए'} हैं।"
        case "low_battery":
            return f"{who} के फ़ोन की बैटरी {e['battery_pct']}% रह गई है।"
        case "sharing" if e.get("active") is False:
            return f"{who} ने location sharing बंद कर दी है।"
    return None


class FamilyEventListener:
    """Keeps a WebSocket to the server and speaks events; reconnects with backoff (needs `websockets`)."""

    def __init__(self, announce: Callable[[str], Awaitable[None]]):
        self.announce = announce

    async def run(self) -> None:
        import websockets  # pip install websockets==13.1

        url = SERVER.replace("https://", "wss://", 1) + "/ws/events?" + urllib.parse.urlencode({"token": TOKEN})
        backoff = 2
        while True:
            try:
                async with websockets.connect(url, ping_interval=30) as ws:
                    backoff = 2
                    async for raw in ws:
                        text = describe_event(json.loads(raw))
                        if text:
                            await self.announce(text)
            except Exception:
                await asyncio.sleep(backoff)
                backoff = min(backoff * 2, 120)
