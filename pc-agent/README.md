# Pulse — PC Agent

A tiny background beacon that lets the Pulse phone app **discover this PC by name** — no
manual MAC/IP entry.

It broadcasts a small JSON message (name, MAC, IP, subnet broadcast) over UDP `42999`
every few seconds. **Outbound only** — it opens no inbound ports and makes no internet
connections.

## Install (no admin required)

```powershell
powershell -ExecutionPolicy Bypass -File .\Install-PulseAgent.ps1
```

This copies a hidden launcher into your Startup folder so the agent auto-starts at logon,
and starts it immediately.

## Remove

```powershell
powershell -ExecutionPolicy Bypass -File .\Install-PulseAgent.ps1 -Uninstall
```

## Run once (foreground, for testing)

```powershell
powershell -ExecutionPolicy Bypass -File .\PulseAgent.ps1
```

The agent deliberately excludes USB-tethering (RNDIS), VPN, and virtual adapters, and picks
the real LAN NIC by numeric link speed.
