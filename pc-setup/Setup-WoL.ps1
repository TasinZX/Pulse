<#
.SYNOPSIS
    Pulse — Wake-on-LAN PC-side setup & status helper.

.DESCRIPTION
    One-time helper you run on the Windows PC you want to wake from your phone.
    It:
      1. Finds the active network adapter, its MAC address and IPv4 details.
      2. Enables the adapter's Wake-on-LAN power settings where possible
         (Wake on Magic Packet + "allow this device to wake the computer").
      3. Warns about Fast Startup (the #1 reason WoL "randomly" stops working).
      4. Prints a config block you type once into the Pulse phone app.

    Run it from an elevated PowerShell for the auto-configuration steps.
    Without admin it still reports everything — it just can't flip NIC settings.

.NOTES
    Safe to re-run. Nothing here is destructive.
#>

[CmdletBinding()]
param(
    # Skip the auto-enable steps and only report current state.
    [switch]$ReportOnly,
    # UDP port your phone app targets (default 9). Only used for the firewall hint.
    [int]$WakePort = 9
)

$ErrorActionPreference = 'Stop'

function Write-Head($t) {
    Write-Host ""
    Write-Host ("  " + $t) -ForegroundColor Magenta
    Write-Host ("  " + ("-" * $t.Length)) -ForegroundColor DarkMagenta
}
function Ok($t)   { Write-Host "  [ OK ]  $t"   -ForegroundColor Green }
function Warn($t) { Write-Host "  [WARN]  $t"   -ForegroundColor Yellow }
function Info($t) { Write-Host "  [INFO]  $t"   -ForegroundColor Gray }
function Fail($t) { Write-Host "  [FAIL]  $t"   -ForegroundColor Red }

function Test-Admin {
    $id = [Security.Principal.WindowsIdentity]::GetCurrent()
    $p  = New-Object Security.Principal.WindowsPrincipal($id)
    return $p.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

try { Clear-Host } catch { }
Write-Host ""
Write-Host "  ###   PULSE  --  Wake-on-LAN setup" -ForegroundColor Magenta
Write-Host "        PC-side helper" -ForegroundColor DarkGray

$isAdmin = Test-Admin
if (-not $isAdmin) {
    Warn "Not running as Administrator. I can REPORT everything but cannot"
    Warn "auto-enable NIC wake settings. Re-run in an elevated PowerShell to fix that."
}

# ---------------------------------------------------------------------------
# 1. Find the active adapter
# ---------------------------------------------------------------------------
Write-Head "1. Network adapter"

$adapter = Get-NetAdapter -Physical |
    Where-Object {
        $_.Status -eq 'Up' -and
        $_.InterfaceDescription -notmatch 'Remote NDIS|Internet Sharing|Virtual|Hyper-V|Loopback|WARP|VPN|TAP|Bluetooth'
    } |
    Sort-Object -Property TransmitLinkSpeed -Descending |
    Select-Object -First 1

if (-not $adapter) {
    Fail "No active physical network adapter found. Connect Ethernet/Wi-Fi and re-run."
    return
}

$mac = $adapter.MacAddress            # AA-BB-CC-DD-EE-FF
$macColon = $mac -replace '-', ':'
Ok "Adapter : $($adapter.Name)  ($($adapter.InterfaceDescription))"
Ok "MAC     : $macColon"

$ip = Get-NetIPAddress -InterfaceIndex $adapter.ifIndex -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -notlike '169.254*' } | Select-Object -First 1

if ($ip) {
    $prefix = $ip.PrefixLength
    Ok "IPv4    : $($ip.IPAddress)/$prefix"

    # Compute the directed broadcast address for this subnet, one octet at a time.
    $ipOctets = ([System.Net.IPAddress]::Parse($ip.IPAddress)).GetAddressBytes()  # network order
    $maskOctets = @(0, 0, 0, 0)
    $bitsLeft = $prefix
    for ($i = 0; $i -lt 4; $i++) {
        $take = [Math]::Min(8, [Math]::Max(0, $bitsLeft))
        $maskOctets[$i] = if ($take -eq 0) { 0 } else { (0xFF -shl (8 - $take)) -band 0xFF }
        $bitsLeft -= 8
    }
    $bcBytes = for ($i = 0; $i -lt 4; $i++) { $ipOctets[$i] -bor (0xFF - $maskOctets[$i]) }
    $broadcast = ($bcBytes -join '.')
    Ok "Subnet broadcast : $broadcast   (best target for the magic packet)"
} else {
    Warn "Could not read an IPv4 address for the adapter."
    $broadcast = "255.255.255.255"
}

$isWifi = $adapter.InterfaceDescription -match 'wi-?fi|wireless|802\.11'
if ($isWifi) {
    Warn "Active adapter is Wi-Fi. Many Wi-Fi cards do NOT support Wake-on-LAN,"
    Warn "or only from sleep (S3), not full shutdown. Ethernet is far more reliable."
} else {
    Ok "Wired adapter detected — ideal for Wake-on-LAN."
}

# ---------------------------------------------------------------------------
# 2. Enable Wake-on-LAN power settings
# ---------------------------------------------------------------------------
Write-Head "2. Wake-on-LAN power settings"

$pm = Get-NetAdapterPowerManagement -Name $adapter.Name -ErrorAction SilentlyContinue
if ($pm) {
    Info "WakeOnMagicPacket : $($pm.WakeOnMagicPacket)"
    Info "DeviceSleepOnDisconnect / power-save flags read from adapter."

    if (-not $ReportOnly -and $isAdmin) {
        try {
            $pm.WakeOnMagicPacket = 'Enabled'
            $pm.WakeOnPattern     = 'Disabled'   # avoid spurious wakes from network noise
            $pm | Set-NetAdapterPowerManagement -ErrorAction Stop | Out-Null
            Ok "Enabled 'Wake on Magic Packet' on $($adapter.Name)."
        } catch {
            Warn "Could not set adapter power management automatically: $($_.Exception.Message)"
            Warn "Set it by hand: Device Manager > adapter > Properties > Power Management"
            Warn "  + Advanced tab > 'Wake on Magic Packet' = Enabled."
        }
    } elseif ($ReportOnly) {
        Info "ReportOnly set — skipping auto-enable."
    }
} else {
    Warn "This adapter does not expose power-management settings via Windows."
    Warn "Enable WoL in the BIOS/UEFI ('Wake on LAN' / 'Power on by PCI-E') instead."
}

# Allow the device to wake the machine (powercfg).
if (-not $ReportOnly -and $isAdmin) {
    try {
        $pnp = Get-PnpDevice -Class Net -ErrorAction SilentlyContinue |
               Where-Object { $_.FriendlyName -eq $adapter.InterfaceDescription } |
               Select-Object -First 1
        if ($pnp) {
            $instanceId = $pnp.InstanceId
            powercfg /deviceenablewake "$instanceId" 2>$null
            Ok "Told Windows to allow this adapter to wake the PC."
        }
    } catch {
        Info "Could not toggle powercfg device-wake automatically (non-fatal)."
    }
}

# ---------------------------------------------------------------------------
# 3. Fast Startup check
# ---------------------------------------------------------------------------
Write-Head "3. Fast Startup (hidden WoL killer)"

$fastStartup = $null
try {
    $fastStartup = (Get-ItemProperty 'HKLM:\SYSTEM\CurrentControlSet\Control\Session Manager\Power' `
        -Name 'HiberbootEnabled' -ErrorAction Stop).HiberbootEnabled
} catch { }

if ($fastStartup -eq 1) {
    Warn "Fast Startup is ON. After a full shutdown the NIC often powers down"
    Warn "completely and won't listen for magic packets."
    if (-not $ReportOnly -and $isAdmin) {
        try {
            Set-ItemProperty 'HKLM:\SYSTEM\CurrentControlSet\Control\Session Manager\Power' `
                -Name 'HiberbootEnabled' -Value 0 -ErrorAction Stop
            Ok "Disabled Fast Startup. (Takes effect on next full shutdown.)"
        } catch {
            Warn "Couldn't disable it automatically. Control Panel > Power Options >"
            Warn "  'Choose what the power buttons do' > uncheck 'Turn on fast startup'."
        }
    }
} elseif ($fastStartup -eq 0) {
    Ok "Fast Startup is already OFF. Good."
} else {
    Info "Fast Startup state unknown on this system (may not apply)."
}

# ---------------------------------------------------------------------------
# 4. Firewall hint (only relevant if you later add a PC status listener)
# ---------------------------------------------------------------------------
Write-Head "4. Notes"
Info "Magic packets need no inbound firewall rule — the NIC handles them below the OS."
Info "You only need a firewall rule if you add the optional status listener on UDP/$WakePort."

# ---------------------------------------------------------------------------
# 5. Config block for the phone app
# ---------------------------------------------------------------------------
Write-Head "5. Enter this in the Pulse app (Setup > PC details)"

$hostName = $env:COMPUTERNAME
Write-Host ""
Write-Host "  ------------------------------------------" -ForegroundColor DarkMagenta
Write-Host "   PC name    : $hostName"                    -ForegroundColor White
Write-Host "   MAC        : $macColon"                    -ForegroundColor White
if ($ip) { Write-Host "   IP         : $($ip.IPAddress)" -ForegroundColor White }
Write-Host "   Broadcast  : $broadcast"                   -ForegroundColor White
Write-Host "   Wake port  : 9"                            -ForegroundColor White
Write-Host "  ------------------------------------------" -ForegroundColor DarkMagenta
Write-Host ""
Ok "Done. Do a full Shut Down (not Restart) to test that WoL wakes it from OFF."
Write-Host ""
