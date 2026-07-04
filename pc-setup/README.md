# Pulse — PC Setup Helper

A one-time helper that configures Windows for Wake-on-LAN and reports your PC's network
details.

## Run

Elevated PowerShell (needed to change adapter settings):

```powershell
powershell -ExecutionPolicy Bypass -File .\Setup-WoL.ps1
```

It will:
1. Find your primary wired adapter and print its **MAC**, **IP**, and **subnet broadcast**.
2. Enable **Wake on Magic Packet** and allow the device to wake the PC.
3. Disable **Fast Startup** (a common cause of WoL silently failing).
4. Warn about BIOS prerequisites it can't change (`ErP` / `Power On By PCIE`).

Inspect current state without changing anything:

```powershell
powershell -ExecutionPolicy Bypass -File .\Setup-WoL.ps1 -ReportOnly
```

> Note: some settings (BIOS Wake-on-LAN, ErP/Deep Sleep) live in your motherboard firmware
> and must be enabled there manually — Windows cannot change them.
