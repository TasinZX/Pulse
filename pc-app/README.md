# Pulse PC — native Windows companion app

A single, compiled tray app that powers all PC-side features for the Pulse phone app.
It **replaces the PowerShell scripts** in `pc-agent/` (discovery beacon + remote-desktop
server + command endpoints) with one program.

**Why native:** a compiled `.exe` isn't scanned by PowerShell's AMSI, so it doesn't hit
the antivirus false-positive that blocks the script agent — Remote Desktop and the
control commands "just run."

## What it does

- **Discovery beacon** (UDP 42999) so the phone finds this PC by name — no config.
- **Remote-desktop server** (HTTP 42990): screen capture (`/frame`, `/info`) + input
  injection (`/input` — mouse, keyboard, scroll).
- **Control commands** (`/command`): lock, sleep, reboot, shutdown, run apps, set clipboard.
- **Status / clipboard**: `/status` (idle seconds), `/clipboard` (read PC clipboard).
- **System tray** icon with the PC name/IP and a Quit option.
- **Auto-starts** at logon (registers itself under HKCU `...\Run`).

## Build & run

No SDK needed — Windows already has the C# compiler:

```powershell
cd pc-app
powershell -ExecutionPolicy Bypass -File .\build.ps1
.\PulsePC.exe
```

It appears in the system tray and starts serving immediately. It auto-starts on future
logons; to remove that, delete the `PulsePC` value under
`HKCU\Software\Microsoft\Windows\CurrentVersion\Run`.

## Notes

- Binds `http://+:42990/` (works with the existing URL reservation) and falls back to
  localhost if that isn't available.
- LAN-only, no cloud, no accounts. Requires the phone and PC on the same network.
- The old `pc-agent/*.ps1` scripts remain in the repo for reference but are superseded
  by this app.
