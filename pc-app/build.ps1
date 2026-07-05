<#
  Builds Pulse PC (PulsePC.exe) using the .NET Framework C# compiler that ships
  with Windows — no SDK, no downloads, no admin.

  Usage:  powershell -ExecutionPolicy Bypass -File .\build.ps1
  Then:   double-click PulsePC.exe (it lives in the tray and auto-starts at logon).
#>

$csc = Join-Path $env:WINDIR "Microsoft.NET\Framework64\v4.0.30319\csc.exe"
if (-not (Test-Path $csc)) { throw "C# compiler not found at $csc (needs .NET Framework 4.x, present on all Win10/11)." }

$out = Join-Path $PSScriptRoot "PulsePC.exe"
$src = Join-Path $PSScriptRoot "Program.cs"

& $csc /nologo /target:winexe /out:"$out" `
    /r:System.dll /r:System.Core.dll /r:System.Windows.Forms.dll /r:System.Drawing.dll /r:System.Web.Extensions.dll `
    "$src"

if ($LASTEXITCODE -eq 0) { Write-Host "Built: $out" -ForegroundColor Green }
else { Write-Host "Build failed." -ForegroundColor Red }
