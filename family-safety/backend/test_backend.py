"""Run: FAMILY_DB=:memory: is not shared across connections, so a temp file is used."""
import os, tempfile, time
os.environ["FAMILY_DB"] = tempfile.mktemp(suffix=".db")
os.environ["FAMILY_ADMIN_KEY"] = "a" * 32
from fastapi.testclient import TestClient
import main

def test_consent_flow():
    with TestClient(main.app) as c:
        admin = {"X-Admin-Key": "a" * 32}
        code_m = c.post("/api/admin/members", json={"user_id": "papa", "display_name": "Papa", "role": "member"}, headers=admin).json()["pairing_code"]
        code_v = c.post("/api/admin/members", json={"user_id": "seema", "display_name": "Seema", "role": "viewer"}, headers=admin).json()["pairing_code"]
        c.post("/api/admin/zones", json={"name": "Home", "lat": 22.57, "lng": 88.36, "radius_m": 200}, headers=admin)
        tm = c.post("/api/pair", json={"user_id": "papa", "code": code_m}).json()["token"]
        tv = c.post("/api/pair", json={"user_id": "seema", "code": code_v}).json()["token"]
        assert c.post("/api/pair", json={"user_id": "papa", "code": code_m}).status_code == 403  # one use
        M, V = {"Authorization": f"Bearer {tm}"}, {"Authorization": f"Bearer {tv}"}
        upd = lambda **k: c.post("/api/location/update", headers=M, json={"user_id": "papa", "lat": 22.57, "lng": 88.36, "battery_pct": 68, "speed": 0, "timestamp": time.time(), "is_sharing_active": True, **k})
        assert c.get("/api/location/papa", headers=V).json()["sharing"] is False
        assert upd().json()["stored"] is True
        r = c.get("/api/location/papa", headers=V).json()
        assert r["sharing"] and r["battery_pct"] == 68
        with c.websocket_connect(f"/ws/events?token={tv}") as ws:
            upd(lat=22.60)  # leaves Home
            assert ws.receive_json()["event"] == "exit"
            upd(lat=22.60, battery_pct=10)
            assert ws.receive_json()["type"] == "low_battery"
            assert c.post("/api/sos/trigger", headers=M, data={"user_id": "papa", "lat": "22.6", "lng": "88.36"}).json()["sent"]
            assert ws.receive_json()["type"] == "sos"
        assert upd(is_sharing_active=False).json()["stored"] is False
        assert c.get("/api/location/papa", headers=V).json()["message"] == main.OFF_MESSAGE
        # A member cannot read others or write for others; no token = 401.
        assert c.get("/api/location/papa").status_code == 401
        assert c.post("/api/location/update", headers=V, json={"user_id": "papa", "lat": 1, "lng": 1, "timestamp": time.time(), "is_sharing_active": True}).status_code == 403
