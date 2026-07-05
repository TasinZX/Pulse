<#
  Pulse Remote — Pulse's OWN in-app remote desktop server (no third-party software).

  Captures this PC's screen and streams JPEG frames over HTTP to the Pulse phone app,
  and injects the mouse/keyboard input the app sends back — so you get live view + full
  control entirely inside Pulse.

  Protocol (tiny HTTP on port 42990):
    GET  /info            -> {"w":<screenW>,"h":<screenH>}
    GET  /frame           -> current screen as JPEG
    POST /input  (JSON)   -> mouse/keyboard command (see Invoke-Input)

  Binding to all interfaces needs a one-time URL reservation + firewall rule; run
  Enable-RemoteControl.ps1 (admin) once. This script falls back to localhost if it
  can't bind publicly, so you can still test on the PC itself.
#>

[CmdletBinding()]
param(
    [int]$Port = 42990,
    [int]$MaxWidth = 1280,      # downscale frames to this width for bandwidth
    [int]$Quality = 45          # JPEG quality 1-100
)

Add-Type -AssemblyName System.Drawing

# --- Win32 input + DPI ------------------------------------------------------
Add-Type @"
using System;
using System.Runtime.InteropServices;
public class PulseWin32 {
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int X, int Y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint f, uint dx, uint dy, uint data, IntPtr extra);
    [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint f, IntPtr extra);
    [DllImport("user32.dll")] public static extern int GetSystemMetrics(int i);
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    public const uint LEFTDOWN=0x0002, LEFTUP=0x0004, RIGHTDOWN=0x0008, RIGHTUP=0x0010, WHEEL=0x0800;
    public const uint KEYUP=0x0002, UNICODE=0x0004;
}
"@

[PulseWin32]::SetProcessDPIAware() | Out-Null
$SCREEN_W = [PulseWin32]::GetSystemMetrics(0)
$SCREEN_H = [PulseWin32]::GetSystemMetrics(1)

# --- Screen capture ---------------------------------------------------------
$jpegCodec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() |
    Where-Object { $_.FormatID -eq [System.Drawing.Imaging.ImageFormat]::Jpeg.Guid }
$encParams = New-Object System.Drawing.Imaging.EncoderParameters(1)
$encParams.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter(
    [System.Drawing.Imaging.Encoder]::Quality, [long]$Quality)

function Get-FrameBytes {
    $bmp = New-Object System.Drawing.Bitmap $SCREEN_W, $SCREEN_H
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen(0, 0, 0, 0, $bmp.Size)
    $g.Dispose()

    $scale = [Math]::Min(1.0, $MaxWidth / [double]$SCREEN_W)
    $ow = [int]($SCREEN_W * $scale); $oh = [int]($SCREEN_H * $scale)
    $out = New-Object System.Drawing.Bitmap $ow, $oh
    $g2 = [System.Drawing.Graphics]::FromImage($out)
    $g2.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::Bilinear
    $g2.DrawImage($bmp, 0, 0, $ow, $oh)
    $g2.Dispose(); $bmp.Dispose()

    $ms = New-Object System.IO.MemoryStream
    $out.Save($ms, $jpegCodec, $encParams)
    $out.Dispose()
    $bytes = $ms.ToArray(); $ms.Dispose()
    return $bytes
}

# --- Input injection --------------------------------------------------------
function Invoke-Input($cmd) {
    switch ($cmd.t) {
        'm'      { [PulseWin32]::SetCursorPos([int]$cmd.x, [int]$cmd.y) }
        'tap'    { [PulseWin32]::SetCursorPos([int]$cmd.x, [int]$cmd.y)
                   [PulseWin32]::mouse_event([PulseWin32]::LEFTDOWN,0,0,0,[IntPtr]::Zero)
                   [PulseWin32]::mouse_event([PulseWin32]::LEFTUP,0,0,0,[IntPtr]::Zero) }
        'double' { [PulseWin32]::SetCursorPos([int]$cmd.x, [int]$cmd.y)
                   for ($i=0;$i -lt 2;$i++) {
                     [PulseWin32]::mouse_event([PulseWin32]::LEFTDOWN,0,0,0,[IntPtr]::Zero)
                     [PulseWin32]::mouse_event([PulseWin32]::LEFTUP,0,0,0,[IntPtr]::Zero) } }
        'rtap'   { [PulseWin32]::SetCursorPos([int]$cmd.x, [int]$cmd.y)
                   [PulseWin32]::mouse_event([PulseWin32]::RIGHTDOWN,0,0,0,[IntPtr]::Zero)
                   [PulseWin32]::mouse_event([PulseWin32]::RIGHTUP,0,0,0,[IntPtr]::Zero) }
        'ld'     { [PulseWin32]::mouse_event([PulseWin32]::LEFTDOWN,0,0,0,[IntPtr]::Zero) }
        'lu'     { [PulseWin32]::mouse_event([PulseWin32]::LEFTUP,0,0,0,[IntPtr]::Zero) }
        'click'  { [PulseWin32]::mouse_event([PulseWin32]::LEFTDOWN,0,0,0,[IntPtr]::Zero)
                   [PulseWin32]::mouse_event([PulseWin32]::LEFTUP,0,0,0,[IntPtr]::Zero) }
        'rclick' { [PulseWin32]::mouse_event([PulseWin32]::RIGHTDOWN,0,0,0,[IntPtr]::Zero)
                   [PulseWin32]::mouse_event([PulseWin32]::RIGHTUP,0,0,0,[IntPtr]::Zero) }
        'scroll' { [PulseWin32]::mouse_event([PulseWin32]::WHEEL,0,0,[uint32][int]$cmd.d,[IntPtr]::Zero) }
        'kd'     { [PulseWin32]::keybd_event([byte][int]$cmd.vk,0,0,[IntPtr]::Zero) }
        'ku'     { [PulseWin32]::keybd_event([byte][int]$cmd.vk,0,[PulseWin32]::KEYUP,[IntPtr]::Zero) }
        'key'    { $vk=[byte][int]$cmd.vk
                   [PulseWin32]::keybd_event($vk,0,0,[IntPtr]::Zero)
                   [PulseWin32]::keybd_event($vk,0,[PulseWin32]::KEYUP,[IntPtr]::Zero) }
        'text'   { foreach ($ch in [char[]]$cmd.s) {
                     $code=[byte]0
                     [PulseWin32]::keybd_event($code,[byte][int][char]$ch,[PulseWin32]::UNICODE,[IntPtr]::Zero)
                     [PulseWin32]::keybd_event($code,[byte][int][char]$ch,[PulseWin32]::UNICODE -bor [PulseWin32]::KEYUP,[IntPtr]::Zero) } }
    }
}

# --- HTTP server ------------------------------------------------------------
function Start-Listener($prefixes) {
    $l = New-Object System.Net.HttpListener
    foreach ($p in $prefixes) { $l.Prefixes.Add($p) }
    $l.Start()
    return $l
}

$listener = $null
foreach ($attempt in @(@("http://+:$Port/"), @("http://localhost:$Port/"))) {
    try { $listener = Start-Listener $attempt; Write-Host "Listening on $($attempt -join ', ')"; break }
    catch { Write-Host "Bind failed for $($attempt -join ',') ($($_.Exception.Message))" }
}
if (-not $listener) { throw "Could not start HTTP listener on port $Port." }

Write-Host "Pulse Remote ready. Screen ${SCREEN_W}x${SCREEN_H}. Ctrl+C to stop."

while ($listener.IsListening) {
    try {
        $ctx = $listener.GetContext()
        $req = $ctx.Request; $res = $ctx.Response
        $res.Headers.Add("Access-Control-Allow-Origin", "*")
        switch -Regex ($req.Url.AbsolutePath) {
            '^/info$' {
                $json = "{""w"":$SCREEN_W,""h"":$SCREEN_H}"
                $b = [System.Text.Encoding]::UTF8.GetBytes($json)
                $res.ContentType = "application/json"
                $res.OutputStream.Write($b, 0, $b.Length)
            }
            '^/frame$' {
                $b = Get-FrameBytes
                $res.ContentType = "image/jpeg"
                $res.ContentLength64 = $b.Length
                $res.OutputStream.Write($b, 0, $b.Length)
            }
            '^/input$' {
                $reader = New-Object System.IO.StreamReader($req.InputStream, $req.ContentEncoding)
                $body = $reader.ReadToEnd(); $reader.Close()
                try { Invoke-Input ($body | ConvertFrom-Json) } catch {}
                $res.StatusCode = 204
            }
            '^/shutdown$' {
                # ?dry=1 returns what it WOULD run, without shutting down (for testing).
                if ($req.QueryString["dry"]) {
                    $b = [System.Text.Encoding]::UTF8.GetBytes("would run: shutdown /s /t 0")
                    $res.ContentType = "text/plain"
                    $res.OutputStream.Write($b, 0, $b.Length)
                } else {
                    # Graceful shutdown; apps get a chance to save. /t 3 gives a tiny grace window.
                    Start-Process "shutdown.exe" -ArgumentList "/s", "/t", "3" -WindowStyle Hidden
                    $res.StatusCode = 200
                }
            }
            default { $res.StatusCode = 404 }
        }
        $res.OutputStream.Close()
    } catch {
        # keep serving even if a single request blows up
    }
}
