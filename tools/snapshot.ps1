param(
    [string]$Note = ""
)

# Snapshots the built APK so any version can be reinstalled later.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$dist = Join-Path $root "dist"
$apk = Join-Path $dist "docklet-shell-0.1.0-debug.apk"
$backups = Join-Path $dist "backups"

if (-not (Test-Path -LiteralPath $apk)) {
    Write-Host "no apk to snapshot: $apk" -ForegroundColor Yellow
    exit 1
}

if (-not (Test-Path -LiteralPath $backups)) {
    New-Item -ItemType Directory -Path $backups | Out-Null
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$safeNote = ($Note -replace '[\\/:*?"<>|\s]+', '-').Trim('-')
if ([string]::IsNullOrWhiteSpace($safeNote)) {
    $safeNote = "build"
}
$name = "$stamp-$safeNote.apk"
$target = Join-Path $backups $name

Copy-Item -LiteralPath $apk -Destination $target -Force

$size = (Get-Item -LiteralPath $target).Length
$sha = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash

$index = Join-Path $backups "INDEX.txt"
$line = "{0}  {1,9} bytes  {2}  {3}" -f $stamp, $size, $sha.Substring(0, 16), $name
Add-Content -LiteralPath $index -Value $line -Encoding UTF8

Write-Host "snapshot: $target"
Write-Host ("size={0} sha256={1}" -f $size, $sha)
