"""Storage and request models for the Family Safety backend.

Privacy by design:
- Only the LATEST location of each member is kept (no history table). Turning sharing OFF erases it.
- A location is stored only when the member's own app says sharing is ON.
- Every phone has its own token (from a one-time pairing code); tokens are stored only as SHA-256 hashes.
"""
from __future__ import annotations

import hashlib
import os
import secrets
import sqlite3
import time
from contextlib import contextmanager
from typing import Iterator, Optional

from pydantic import BaseModel, Field

DB_PATH = os.environ.get("FAMILY_DB", "family_safety.db")

SCHEMA = """
CREATE TABLE IF NOT EXISTS members (
    user_id      TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    role         TEXT NOT NULL CHECK (role IN ('member', 'viewer')),
    token_hash   TEXT UNIQUE,
    pairing_code TEXT,
    created_at   REAL NOT NULL
);
CREATE TABLE IF NOT EXISTS latest_location (
    user_id     TEXT PRIMARY KEY REFERENCES members(user_id) ON DELETE CASCADE,
    lat         REAL NOT NULL,
    lng         REAL NOT NULL,
    battery_pct INTEGER,
    speed       REAL,
    timestamp   REAL NOT NULL,
    received_at REAL NOT NULL
);
CREATE TABLE IF NOT EXISTS sharing_state (
    user_id    TEXT PRIMARY KEY REFERENCES members(user_id) ON DELETE CASCADE,
    is_active  INTEGER NOT NULL,
    changed_at REAL NOT NULL
);
CREATE TABLE IF NOT EXISTS safe_zones (
    id        INTEGER PRIMARY KEY AUTOINCREMENT,
    name      TEXT NOT NULL,
    lat       REAL NOT NULL,
    lng       REAL NOT NULL,
    radius_m  REAL NOT NULL CHECK (radius_m BETWEEN 50 AND 50000)
);
CREATE TABLE IF NOT EXISTS zone_presence (
    user_id TEXT NOT NULL REFERENCES members(user_id) ON DELETE CASCADE,
    zone_id INTEGER NOT NULL REFERENCES safe_zones(id) ON DELETE CASCADE,
    inside  INTEGER NOT NULL,
    PRIMARY KEY (user_id, zone_id)
);
CREATE TABLE IF NOT EXISTS battery_alert (
    user_id TEXT PRIMARY KEY REFERENCES members(user_id) ON DELETE CASCADE,
    alerted INTEGER NOT NULL
);
"""


def hash_token(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


@contextmanager
def db() -> Iterator[sqlite3.Connection]:
    conn = sqlite3.connect(DB_PATH, timeout=10)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA foreign_keys = ON")
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


def init_db() -> None:
    with db() as conn:
        conn.executescript(SCHEMA)


def add_member(user_id: str, display_name: str, role: str) -> str:
    """Creates a member/viewer and returns a one-time pairing code (shown once to the admin)."""
    code = f"{secrets.randbelow(10**8):08d}"
    with db() as conn:
        conn.execute(
            "INSERT INTO members (user_id, display_name, role, pairing_code, created_at) VALUES (?,?,?,?,?)",
            (user_id, display_name, role, hash_token(code), time.time()),
        )
    return code


def pair(user_id: str, code: str) -> Optional[str]:
    """Exchanges a pairing code for a device token (once). Returns None when the code is wrong."""
    with db() as conn:
        row = conn.execute("SELECT pairing_code FROM members WHERE user_id=?", (user_id,)).fetchone()
        if row is None or row["pairing_code"] is None or not secrets.compare_digest(row["pairing_code"], hash_token(code)):
            return None
        token = secrets.token_urlsafe(32)
        conn.execute("UPDATE members SET token_hash=?, pairing_code=NULL WHERE user_id=?", (hash_token(token), user_id))
        return token


def member_for_token(token: str) -> Optional[sqlite3.Row]:
    with db() as conn:
        return conn.execute("SELECT * FROM members WHERE token_hash=?", (hash_token(token),)).fetchone()


# ---------------------------------------------------------------- request / response models

class LocationUpdate(BaseModel):
    user_id: str = Field(min_length=1, max_length=64)
    lat: float = Field(ge=-90, le=90)
    lng: float = Field(ge=-180, le=180)
    battery_pct: Optional[int] = Field(default=None, ge=0, le=100)
    speed: Optional[float] = Field(default=None, ge=0, le=150)  # m/s
    is_sharing_active: bool
    timestamp: float  # unix seconds, from the phone


class PairRequest(BaseModel):
    user_id: str = Field(min_length=1, max_length=64)
    code: str = Field(min_length=8, max_length=8)


class SafeZoneIn(BaseModel):
    name: str = Field(min_length=1, max_length=40)
    lat: float = Field(ge=-90, le=90)
    lng: float = Field(ge=-180, le=180)
    radius_m: float = Field(ge=50, le=50000)
