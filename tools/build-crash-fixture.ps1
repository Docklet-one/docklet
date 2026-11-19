param(
    [string]$Version = "1.0.0"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$source = Join-Path (Join-Path $PSScriptRoot "fixtures") "crashy"
$outDir = Join-Path $root "dist\fixtures"
$zip = Join-Path $outDir "crashy-$Version.zip"
$target = Join-Path $outDir "crashy-$Version.pkg"

New-Item -ItemType Directory -Force -Path $outDir | Out-Null
if (Test-Path -LiteralPath $zip) { Remove-Item -LiteralPath $zip -Force }
if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Force }

Compress-Archive -Path (Join-Path $source "*") -DestinationPath $zip
Move-Item -LiteralPath $zip -Destination $target
Write-Host "wrote $target"
