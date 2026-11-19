param(
    [string]$Version = "1.0.1"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$source = Join-Path $root "apps\demo"
$stage = Join-Path $root "dist\.demo-update-src"
$zip = Join-Path $root "dist\demo-$Version.zip"
$target = Join-Path $root "dist\demo-$Version.pkg"

if (Test-Path -LiteralPath $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
Copy-Item -LiteralPath $source -Destination $stage -Recurse

$manifest = Join-Path $stage "module.json"
$text = [IO.File]::ReadAllText($manifest)
$text = $text -replace '"version"\s*:\s*"[^"]+"', "`"version`": `"$Version`""
[IO.File]::WriteAllText($manifest, $text, (New-Object System.Text.UTF8Encoding($false)))

if (Test-Path -LiteralPath $zip) { Remove-Item -LiteralPath $zip -Force }
Compress-Archive -Path (Join-Path $stage "*") -DestinationPath $zip
Move-Item -LiteralPath $zip -Destination $target -Force
Remove-Item -LiteralPath $stage -Recurse -Force

Write-Host "wrote $target"
