<#
  Enables Pulse's built-in remote desktop so your phone can reach it over the LAN.

  RUN THIS YOURSELF in an ELEVATED PowerShell — it opens an inbound port that lets a
  device on your network view and control this PC, which you should enable deliberately:

      powershell -ExecutionPolicy Bypass -File .\Enable-RemoteControl.ps1

  What it does (all scoped to your Private network only):
    - Reserves the URL http://+:42990/ so the (non-admin) Pulse server can listen on it
    - Adds an inbound firewall rule for TCP 42990 on the Private profile
    - Registers PulseRemote.ps1 to auto-start at logon and starts it now

  Undo:  powershell -ExecutionPolicy Bypass -File .\Enable-RemoteControl.ps1 -Disable
#>

[CmdletBinding()]
param([int]$Port = 42990, [switch]$Disable)

$ErrorActionPreference = 'Stop'

function Test-Admin {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    (New-Object Security.Principal.WindowsPrincipal($id)).IsInRole(
        [Security.Principal.WindowsBuiltInRole]::Administrator)
}
if (-not (Test-Admin)) {
    Write-Host "Run this in an elevated PowerShell (Administrator)." -ForegroundColor Red
    return
}

$remoteScript = Join-Path $PSScriptRoot "PulseRemote.ps1"
$startupVbs   = Join-Path ([Environment]::GetFolderPath('Startup')) "PulseRemote.vbs"
$ruleName     = "Pulse Remote Desktop"
$urlacl       = "http://+:$Port/"

function Stop-Remote {
    Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" -EA SilentlyContinue |
        Where-Object { $_.CommandLine -like '*PulseRemote.ps1*' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -EA SilentlyContinue }
}

if ($Disable) {
    Stop-Remote
    Remove-Item $startupVbs -Force -EA SilentlyContinue
    cmd /c "netsh http delete urlacl url=$urlacl" | Out-Null
    Get-NetFirewallRule -DisplayName $ruleName -EA SilentlyContinue | Remove-NetFirewallRule
    Write-Host "Pulse remote desktop disabled and removed." -ForegroundColor Yellow
    return
}

# 0. Classify the real LAN as Private so Private-scoped firewall rules apply.
#    (Home networks are often mislabeled "Public", which blocks inbound connections.)
try {
    Get-NetConnectionProfile | ForEach-Object {
        $desc = (Get-NetAdapter -InterfaceIndex $_.InterfaceIndex -EA SilentlyContinue).InterfaceDescription
        if ($desc -and $desc -notmatch 'Remote NDIS|Internet Sharing|Virtual|Hyper-V|WARP|VPN|TAP' -and
            $_.NetworkCategory -eq 'Public') {
            Set-NetConnectionProfile -InterfaceIndex $_.InterfaceIndex -NetworkCategory Private -EA Stop
            Write-Host "[ OK ] Set network '$($_.Name)' to Private." -ForegroundColor Green
        }
    }
} catch { Write-Host "[WARN] Could not change network category: $($_.Exception.Message)" -ForegroundColor Yellow }

# 1. URL reservation so the non-admin server process can bind to all interfaces.
cmd /c "netsh http delete urlacl url=$urlacl" 2>$null | Out-Null
cmd /c "netsh http add urlacl url=$urlacl user=`"$env:USERDOMAIN\$env:USERNAME`"" | Out-Null
Write-Host "[ OK ] URL reservation added for $urlacl" -ForegroundColor Green

# 2. Firewall — inbound TCP on Private networks only.
Get-NetFirewallRule -DisplayName $ruleName -EA SilentlyContinue | Remove-NetFirewallRule -EA SilentlyContinue
New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -Protocol TCP `
    -LocalPort $Port -Action Allow -Profile Private | Out-Null
Write-Host "[ OK ] Firewall rule added (TCP $Port, Private profile)" -ForegroundColor Green

# 3. Auto-start at logon (hidden), and start it now.
$vbs = @"
Set s = CreateObject("WScript.Shell")
s.Run "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""$remoteScript""", 0, False
"@
Set-Content -Path $startupVbs -Value $vbs -Encoding ASCII
Stop-Remote
Start-Process "wscript.exe" -ArgumentList "`"$startupVbs`""
Write-Host "[ OK ] Pulse Remote started and set to auto-start at logon." -ForegroundColor Green

Write-Host ""
Write-Host "  Pulse remote desktop is ready. Open Pulse on your phone and tap Remote Desktop." -ForegroundColor Magenta
