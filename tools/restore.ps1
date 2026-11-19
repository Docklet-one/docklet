param(
    [string]$Name = "",
    [switch]$List
)

# Reinstalls a snapshotted APK (see tools/snapshot.ps1).
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$backups = Join-Path $root "dist\backups"
$adb = Join-Path $root ".tools\sdk\platform-tools\adb.exe"

if (-not (Test-Path -LiteralPath $backups)) {
    Write-Host "no backups yet: $backups" -ForegroundColor Yellow
    exit 1
}

$files = Get-ChildItem -LiteralPath $backups -Filter *.apk | Sort-Object Name

if ($List -or [string]::IsNullOrWhiteSpace($Name)) {
    Write-Host "snapshots in $backups"
    foreach ($f in $files) {
        Write-Host ("  {0}  {1,9} bytes  {2}" -f $f.LastWriteTime.ToString("yyyy-MM-dd HH:mm:ss"), $f.Length, $f.Name)
    }
    if ($List) { exit 0 }
}

if (-not [string]::IsNullOrWhiteSpace($Name)) {
    if ($Name -eq "latest") {
        $target = $files | Select-Object -Last 1
    } else {
        $target = $files | Where-Object { $_.Name -eq $Name -or $_.Name -like "$Name*" } | Select-Object -Last 1
    }
    if ($null -eq $target) {
        Write-Host "snapshot not found: $Name" -ForegroundColor Red
        exit 1
    }

    if (-not (Test-Path -LiteralPath $adb)) {
        Write-Host "adb not found: $adb" -ForegroundColor Red
        exit 1
    }

    $serial = (& $adb devices | Select-String "device$" | Select-Object -First 1)
    $serial = ($serial -split "\s+")[0]
    if ([string]::IsNullOrWhiteSpace($serial)) {
        Write-Host "no adb device connected" -ForegroundColor Red
        exit 1
    }

    Write-Host ("installing " + $target.Name + " to " + $serial)
    & $adb -s $serial install --no-streaming -r $target.FullName
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & $adb -s $serial shell am force-stop com.docklet.shell.debug
    & $adb -s $serial shell monkey -p com.docklet.shell.debug -c android.intent.category.LAUNCHER 1 | Out-Null
    Write-Host "restored and relaunched"
}
