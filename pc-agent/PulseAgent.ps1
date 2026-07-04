<#
  Pulse Agent — LAN discovery beacon.

  Broadcasts this PC's identity (name, MAC, IP, subnet broadcast) over UDP so the
  Pulse phone app can auto-discover and pair with it — no manual MAC/IP entry.

  Design: the agent only BROADCASTS outward (UDP -> 255.255.255.255:42999 and the
  subnet broadcast). Outbound traffic needs no Windows Firewall rule, so setup stays
  zero-touch. The phone listens for these beacons while scanning.

  Runs quietly in the background; install via Install-PulseAgent.ps1 to auto-start
  at logon.
#>

[CmdletBinding()]
param(
    [int]$Port = 42999,
    [int]$IntervalSeconds = 3
)

$ErrorActionPreference = 'Continue'

function Get-PrimaryAdapterInfo {
    # Real LAN NICs only — exclude USB tethering (RNDIS), VPNs and virtual adapters,
    # any of which can appear "Up" and hijack the selection.
    $candidates = Get-NetAdapter -Physical | Where-Object {
        $_.Status -eq 'Up' -and
        $_.InterfaceDescription -notmatch 'Remote NDIS|Internet Sharing|Virtual|Hyper-V|Loopback|WARP|VPN|TAP|Bluetooth'
    }
    # Prefer an adapter that actually has a default gateway (the true LAN link).
    $withGw = $candidates | Where-Object {
        (Get-NetIPConfiguration -InterfaceIndex $_.ifIndex -ErrorAction SilentlyContinue).IPv4DefaultGateway
    }
    $pool = if ($withGw) { $withGw } else { $candidates }
    # Sort by NUMERIC link speed (LinkSpeed is a display string and sorts wrong).
    $adapter = $pool | Sort-Object -Property TransmitLinkSpeed -Descending | Select-Object -First 1
    if (-not $adapter) { return $null }

    $ipInfo = Get-NetIPAddress -InterfaceIndex $adapter.ifIndex -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notlike '169.254*' } | Select-Object -First 1
    if (-not $ipInfo) { return $null }

    # Subnet broadcast, one octet at a time (robust against PowerShell bitwise quirks).
    $ipOctets = ([System.Net.IPAddress]::Parse($ipInfo.IPAddress)).GetAddressBytes()
    $prefix = $ipInfo.PrefixLength
    $bitsLeft = $prefix
    $bc = for ($i = 0; $i -lt 4; $i++) {
        $take = [Math]::Min(8, [Math]::Max(0, $bitsLeft))
        $mask = if ($take -eq 0) { 0 } else { (0xFF -shl (8 - $take)) -band 0xFF }
        $bitsLeft -= 8
        $ipOctets[$i] -bor (0xFF - $mask)
    }

    [PSCustomObject]@{
        Name      = $env:COMPUTERNAME
        Mac       = ($adapter.MacAddress -replace '-', ':')
        Ip        = $ipInfo.IPAddress
        Broadcast = ($bc -join '.')
    }
}

$udp = New-Object System.Net.Sockets.UdpClient
$udp.EnableBroadcast = $true

Write-Host "Pulse Agent running. Broadcasting on UDP $Port every ${IntervalSeconds}s. Ctrl+C to stop."

while ($true) {
    $info = Get-PrimaryAdapterInfo
    if ($info) {
        $payload = "{""pulse"":""v1"",""name"":""$($info.Name)"",""mac"":""$($info.Mac)"",""ip"":""$($info.Ip)"",""broadcast"":""$($info.Broadcast)""}"
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($payload)
        foreach ($target in @('255.255.255.255', $info.Broadcast)) {
            try {
                $ep = New-Object System.Net.IPEndPoint([System.Net.IPAddress]::Parse($target), $Port)
                $udp.Send($bytes, $bytes.Length, $ep) | Out-Null
            } catch { }
        }
    }
    Start-Sleep -Seconds $IntervalSeconds
}
