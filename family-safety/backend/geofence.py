"""Safe zones (Home, Market, School …): entry / exit events and low-battery alerts.

Pure functions plus small DB helpers; events are only produced from locations the member chose to share.
"""
from __future__ import annotations

import math
from typing import Optional

from models import db

EARTH_RADIUS_M = 6_371_000.0
LOW_BATTERY_PCT = 15
BATTERY_RESET_PCT = 20  # alert again only after the phone was charged above this


def haversine_m(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = p2 - p1
    dl = math.radians(lng2 - lng1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * EARTH_RADIUS_M * math.asin(math.sqrt(a))


def zone_events(user_id: str, lat: float, lng: float) -> list[dict]:
    """Compares the new position with each zone's last known state; returns entry/exit events."""
    events: list[dict] = []
    with db() as conn:
        zones = conn.execute("SELECT * FROM safe_zones").fetchall()
        for z in zones:
            inside = haversine_m(lat, lng, z["lat"], z["lng"]) <= z["radius_m"]
            row = conn.execute(
                "SELECT inside FROM zone_presence WHERE user_id=? AND zone_id=?", (user_id, z["id"])
            ).fetchone()
            if row is None:
                # First fix: remember the state without an event (no "entered Home" on every restart).
                conn.execute("INSERT INTO zone_presence (user_id, zone_id, inside) VALUES (?,?,?)", (user_id, z["id"], int(inside)))
                continue
            if bool(row["inside"]) != inside:
                conn.execute("UPDATE zone_presence SET inside=? WHERE user_id=? AND zone_id=?", (int(inside), user_id, z["id"]))
                events.append({"type": "geofence", "event": "enter" if inside else "exit", "user_id": user_id, "zone": z["name"]})
    return events


def battery_event(user_id: str, battery_pct: Optional[int]) -> Optional[dict]:
    """One low-battery alert per discharge: below 15 %, then quiet until charged above 20 %."""
    if battery_pct is None:
        return None
    with db() as conn:
        row = conn.execute("SELECT alerted FROM battery_alert WHERE user_id=?", (user_id,)).fetchone()
        alerted = bool(row["alerted"]) if row else False
        if battery_pct < LOW_BATTERY_PCT and not alerted:
            conn.execute("INSERT OR REPLACE INTO battery_alert (user_id, alerted) VALUES (?,1)", (user_id,))
            return {"type": "low_battery", "user_id": user_id, "battery_pct": battery_pct}
        if battery_pct > BATTERY_RESET_PCT and alerted:
            conn.execute("INSERT OR REPLACE INTO battery_alert (user_id, alerted) VALUES (?,0)", (user_id,))
    return None


def forget_presence(user_id: str) -> None:
    """Sharing turned OFF: zone states are dropped too, so nothing about the member's position remains."""
    with db() as conn:
        conn.execute("DELETE FROM zone_presence WHERE user_id=?", (user_id,))
