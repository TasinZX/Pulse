<#
  Installs the Pulse Agent so it starts automatically (hidden) at logon.

  Uses the current user's Startup folder — no administrator rights, no UAC prompt.
  A tiny .vbs launcher starts PowerShell fully hidden (no console flash).

  Run:     powershell -ExecutionPolicy Bypass -File .\Install-PulseAgent.ps1
  Remove:  powershell -ExecutionPolicy Bypass -File .\Install-PulseAgent.ps1 -Uninstall
#>

[CmdletBinding()]
param([switch]$Uninstall)

$agentPath   = Join-Path $PSScriptRoot "PulseAgent.ps1"
$startupDir  = [Environment]::GetFolderPath('Startup')
$launcherVbs = Join-Path $startupDir "PulseAgent.vbs"

function Stop-Agent {
    Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -like '*PulseAgent.ps1*' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
}

if ($Uninstall) {
    Remove-Item $launcherVbs -Force -ErrorAction SilentlyContinue
    Stop-Agent
    Write-Host "Pulse Agent removed from startup and stopped."
    return
}

if (-not (Test-Path $agentPath)) { throw "PulseAgent.ps1 not found next to this script." }

# .vbs launcher: runs PowerShell with window hidden (0) and does not wait (False).
$vbs = @"
Set s = CreateObject("WScript.Shell")
s.Run "powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File ""$agentPath""", 0, False
"@
Set-Content -Path $launcherVbs -Value $vbs -Encoding ASCII

# Start it right now too (don't wait for next logon).
Stop-Agent
Start-Process "wscript.exe" -ArgumentList "`"$launcherVbs`""

Write-Host "Pulse Agent installed to startup and running."
Write-Host "  Launcher: $launcherVbs"
Write-Host "It will now start automatically every time you log in."
