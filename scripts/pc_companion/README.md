# SEEMA AI — Windows PC Companion & Unified Brain Guide

The **SEEMA AI PC Companion** provides a secure bridge between your Android phone and Windows PC:
1. **Local AI Bridge (Ollama):** Access models running locally on PC from your mobile device.
2. **Unified Brain Memory Sync:** Keep memories and conversation context synchronized between PC and Mobile.
3. **Authorized PC Actions:** Run safe PC commands (open URLs, launch apps) from Seema on your phone.

---

## 1. Quick Start

Run the companion server on your PC:
```cmd
scripts\pc_companion\start_companion.bat
```
Or via PowerShell:
```powershell
.\scripts\pc_companion\start_companion.ps1
```

By default, the server runs on port **11435**.

---

## 2. Security & Authentication

On first startup, the server automatically generates a cryptographically strong 256-bit random auth token and saves it in:
`%USERPROFILE%\.seema_ai\companion_config.json`

- **The raw token is NEVER printed in console logs or committed to git.**
- **Network Restriction:** The server strictly verifies client IP addresses and rejects any connection that does not originate from RFC1918 private subnets (`192.168.x.x`, `10.x.x.x`, `172.16-31.x.x`) or loopback (`127.0.0.1`).
- **Timing Defense:** Uses constant-time HMAC comparison for token validation.

To find your token to enter into SEEMA AI settings on your phone:
Open `%USERPROFILE%\.seema_ai\companion_config.json` in Notepad or run:
```powershell
(Get-Content "$env:USERPROFILE\.seema_ai\companion_config.json" | ConvertFrom-Json).auth_token
```

---

## 3. Connecting from Mobile Phone

### Option A: Via Wi-Fi (Same Network)
1. Find your PC's IP address (displayed on the companion console, e.g. `192.168.31.55`).
2. In SEEMA AI App → **Settings** → **AI Assistant** → **Ollama (PC Local)**:
   - Set URL: `http://192.168.31.55:11434`
3. In **PC Sync & Companion**:
   - Set Endpoint: `http://192.168.31.55:11435`
   - Set Token: Your private token from `companion_config.json`

### Option B: Via USB Cable (ADB Port Forwarding)
Run in PowerShell with phone connected via USB:
```cmd
adb reverse tcp:11434 tcp:11434
adb reverse tcp:11435 tcp:11435
```
Then on phone use `http://127.0.0.1:11434` for Ollama and `http://127.0.0.1:11435` for PC Sync!

---

## 4. Windows Firewall Configuration

If connecting over Wi-Fi, ensure Windows Defender Firewall allows incoming connections on port 11435 (and 11434 for Ollama):
```powershell
New-NetFirewallRule -DisplayName "SEEMA AI PC Companion" -Direction Inbound -LocalPort 11435 -Protocol TCP -Action Allow
New-NetFirewallRule -DisplayName "Ollama Local AI" -Direction Inbound -LocalPort 11434 -Protocol TCP -Action Allow
```

## 5. Ollama LAN Configuration
By default, Ollama only listens on `127.0.0.1`. To allow other devices on your local Wi-Fi to reach Ollama directly:
Set system environment variable:
`OLLAMA_HOST=0.0.0.0:11434`
and restart Ollama.
