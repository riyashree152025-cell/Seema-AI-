#!/usr/bin/env python3
"""
SEEMA AI — PC Companion & Unified Brain Sync Server
Runs on Windows PC to bridge Local AI (Ollama), synchronize memories with Android,
and execute authorized PC agent actions safely.

Zero external dependencies required (uses standard library http.server and urllib).
"""

import os
import sys
import json
import socket
import platform
import subprocess
import urllib.request
import urllib.error
from http.server import HTTPServer, BaseHTTPRequestHandler
from pathlib import Path

DEFAULT_PORT = 11435
OLLAMA_DEFAULT_URL = "http://127.0.0.1:11434"
DATA_DIR = Path.home() / ".seema_ai"
CONFIG_FILE = DATA_DIR / "companion_config.json"
MEMORIES_FILE = DATA_DIR / "pc_memories.json"
CONVERSATION_FILE = DATA_DIR / "pc_conversations.json"

def ensure_data_dir():
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    if not CONFIG_FILE.exists():
        default_config = {
            "auth_token": "seema_pc_secret_key_12345",
            "port": DEFAULT_PORT,
            "ollama_url": OLLAMA_DEFAULT_URL,
            "allowed_actions": ["open_url", "open_app", "media", "system_info"]
        }
        with open(CONFIG_FILE, "w", encoding="utf-8") as f:
            json.dump(default_config, f, indent=2)
    if not MEMORIES_FILE.exists():
        with open(MEMORIES_FILE, "w", encoding="utf-8") as f:
            json.dump([], f)
    if not CONVERSATION_FILE.exists():
        with open(CONVERSATION_FILE, "w", encoding="utf-8") as f:
            json.dump([], f)

def load_config():
    ensure_data_dir()
    with open(CONFIG_FILE, "r", encoding="utf-8") as f:
        return json.load(f)

def get_local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"

def check_ollama_status(ollama_url):
    try:
        req = urllib.request.Request(f"{ollama_url}/api/tags", headers={"User-Agent": "SeemaPCCompanion"})
        with urllib.request.urlopen(req, timeout=2) as resp:
            if resp.status == 200:
                data = json.loads(resp.read().decode("utf-8"))
                models = [m.get("name") for m in data.get("models", [])]
                return {"online": True, "models": models}
    except Exception as e:
        return {"online": False, "error": str(e), "models": []}
    return {"online": False, "models": []}

class SeemaCompanionHandler(BaseHTTPRequestHandler):
    config = {}

    def _send_json(self, status_code, data):
        response_bytes = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(response_bytes)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Seema-Token")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.end_headers()
        self.wfile.write(response_bytes)

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Seema-Token")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.end_headers()

    def _is_authenticated(self):
        expected_token = self.config.get("auth_token", "")
        if not expected_token:
            return True
        auth_header = self.headers.get("Authorization", "")
        if auth_header.startswith("Bearer "):
            token = auth_header[7:].strip()
            if token == expected_token:
                return True
        header_token = self.headers.get("X-Seema-Token", "").strip()
        return header_token == expected_token

    def do_GET(self):
        if not self._is_authenticated():
            return self._send_json(401, {"error": "Unauthorized", "message": "Invalid or missing companion auth token."})

        if self.path == "/api/v1/status" or self.path == "/status":
            ollama_info = check_ollama_status(self.config.get("ollama_url", OLLAMA_DEFAULT_URL))
            data = {
                "service": "SEEMA AI PC Companion",
                "version": "1.0.0",
                "hostname": socket.gethostname(),
                "os": f"{platform.system()} {platform.release()}",
                "ip": get_local_ip(),
                "port": self.config.get("port", DEFAULT_PORT),
                "ollama": ollama_info,
                "unified_brain_active": True
            }
            return self._send_json(200, data)

        elif self.path == "/api/v1/sync/memories" or self.path == "/sync/memories":
            try:
                with open(MEMORIES_FILE, "r", encoding="utf-8") as f:
                    memories = json.load(f)
                return self._send_json(200, {"memories": memories, "count": len(memories)})
            except Exception as e:
                return self._send_json(500, {"error": str(e)})

        elif self.path == "/api/v1/sync/conversations" or self.path == "/sync/conversations":
            try:
                with open(CONVERSATION_FILE, "r", encoding="utf-8") as f:
                    convs = json.load(f)
                return self._send_json(200, {"conversations": convs, "count": len(convs)})
            except Exception as e:
                return self._send_json(500, {"error": str(e)})

        elif self.path == "/api/v1/ollama/models" or self.path == "/ollama/models":
            ollama_url = self.config.get("ollama_url", OLLAMA_DEFAULT_URL)
            info = check_ollama_status(ollama_url)
            return self._send_json(200, info)

        else:
            return self._send_json(404, {"error": "Not Found", "path": self.path})

    def do_POST(self):
        if not self._is_authenticated():
            return self._send_json(401, {"error": "Unauthorized", "message": "Invalid or missing companion auth token."})

        content_len = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_len).decode("utf-8") if content_len > 0 else "{}"
        try:
            payload = json.loads(body)
        except Exception:
            return self._send_json(400, {"error": "Invalid JSON body"})

        if self.path == "/api/v1/sync/memories" or self.path == "/sync/memories":
            new_memories = payload.get("memories", [])
            with open(MEMORIES_FILE, "r", encoding="utf-8") as f:
                existing = json.load(f)
            existing_texts = {m.get("text") for m in existing if isinstance(m, dict)}
            added = 0
            for item in new_memories:
                text = item.get("text") if isinstance(item, dict) else str(item)
                if text and text not in existing_texts:
                    existing.append({"text": text, "source": "mobile", "synced_at": item.get("createdAt")})
                    existing_texts.add(text)
                    added += 1
            with open(MEMORIES_FILE, "w", encoding="utf-8") as f:
                json.dump(existing, f, indent=2, ensure_ascii=False)
            return self._send_json(200, {"success": True, "added": added, "total": len(existing)})

        elif self.path == "/api/v1/sync/conversations" or self.path == "/sync/conversations":
            new_messages = payload.get("messages", [])
            with open(CONVERSATION_FILE, "w", encoding="utf-8") as f:
                json.dump(new_messages, f, indent=2, ensure_ascii=False)
            return self._send_json(200, {"success": True, "saved": len(new_messages)})

        elif self.path == "/api/v1/actions/pc" or self.path == "/actions/pc":
            action = payload.get("action", "")
            params = payload.get("params", {})
            allowed = self.config.get("allowed_actions", [])

            if action not in allowed:
                return self._send_json(403, {"error": "Forbidden", "message": f"Action '{action}' is not in allowed_actions."})

            if action == "open_url":
                url = params.get("url", "")
                if url.startswith("http://") or url.startswith("https://"):
                    if platform.system() == "Windows":
                        os.system(f'start "" "{url}"')
                    elif platform.system() == "Darwin":
                        subprocess.run(["open", url])
                    else:
                        subprocess.run(["xdg-open", url])
                    return self._send_json(200, {"success": True, "action": "open_url", "target": url})
                else:
                    return self._send_json(400, {"error": "Invalid URL"})

            elif action == "open_app":
                app_name = params.get("name", "").lower()
                safe_apps = {
                    "notepad": "notepad.exe",
                    "calculator": "calc.exe",
                    "terminal": "cmd.exe",
                    "explorer": "explorer.exe"
                }
                cmd = safe_apps.get(app_name)
                if cmd and platform.system() == "Windows":
                    subprocess.Popen([cmd])
                    return self._send_json(200, {"success": True, "app": app_name})
                else:
                    return self._send_json(400, {"error": "App not in safe launch list"})

            elif action == "system_info":
                return self._send_json(200, {
                    "success": True,
                    "hostname": socket.gethostname(),
                    "os": platform.platform(),
                    "cpu_count": os.cpu_count()
                })

            else:
                return self._send_json(400, {"error": "Unknown action"})

        else:
            return self._send_json(404, {"error": "Not Found"})

def run_server():
    config = load_config()
    port = config.get("port", DEFAULT_PORT)
    SeemaCompanionHandler.config = config
    server_address = ("0.0.0.0", port)
    httpd = HTTPServer(server_address, SeemaCompanionHandler)
    ip = get_local_ip()
    print("=" * 65)
    print("  SEEMA AI — PC Companion & Unified Brain Server")
    print("=" * 65)
    print(f"  • Status: RUNNING on port {port}")
    print(f"  • PC IP Address: {ip}")
    print(f"  • Connect from Phone: http://{ip}:{port}")
    print(f"  • USB ADB Port Forward: adb reverse tcp:{port} tcp:{port}")
    print(f"  • Auth Token: {config.get('auth_token')}")
    print(f"  • Config File: {CONFIG_FILE}")
    print("=" * 65)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down PC Companion...")
        httpd.server_close()

if __name__ == "__main__":
    run_server()
