# Build the Docklet shell APK only.
# Usage (from this folder):
#   powershell -ExecutionPolicy Bypass -File .\build-apk.ps1
#   pwsh -File .\build-apk.ps1
# Steps: npm run build:app -> npx cap sync android -> gradlew assembleDebug
#        -> copy to .\docklet-debug.apk
# This repo does not build, package, or publish directory apps.

$ErrorActionPreference = 'Continue'
$root = $PSScriptRoot
$workspace = Split-Path $root -Parent
$tools = Join-Path $workspace '.tools'
if (-not (Test-Path $tools)) {
  $tools = 'C:\Users\95891\Documents\deepseek-harness\default-workspace\.tools'
}

$jdk = Get-ChildItem $tools -Directory -Filter 'jdk*' -ErrorAction SilentlyContinue |
  Sort-Object Name -Descending | Select-Object -First 1
if ($jdk) {
  $env:JAVA_HOME = $jdk.FullName
  $env:PATH = "$($env:JAVA_HOME)\bin;$env:PATH"
}
$sdk = Join-Path $tools 'sdk'
if (Test-Path $sdk) {
  $env:ANDROID_HOME = $sdk
  $env:ANDROID_SDK_ROOT = $sdk
}
$gradleHome = Join-Path $tools 'gradle-home'
if (Test-Path $gradleHome) { $env:GRADLE_USER_HOME = $gradleHome }

$tmp = Join-Path $root '.tmp'
$gradleTmp = Join-Path $tmp 'gradle-tmp'
New-Item -ItemType Directory -Force $gradleTmp | Out-Null
$env:TEMP = $gradleTmp
$env:TMP = $gradleTmp
if ($env:JAVA_HOME) { $env:JAVA_TOOL_OPTIONS = "-Djava.io.tmpdir=$gradleTmp" }

function Fail($step, $log) {
  Write-Host "FAILED: $step" -ForegroundColor Red
  if (Test-Path $log) {
    Get-Content $log -Tail 30 | ForEach-Object { Write-Host "  $_" }
  }
  exit 1
}

Push-Location $root
try {
  $webLog = Join-Path $tmp 'build-app.log'
  npm run build:app *> $webLog
  if ($LASTEXITCODE -ne 0) { Fail 'npm run build:app' $webLog }

  $copyLog = Join-Path $tmp 'cap-sync.log'
  npx cap sync android *> $copyLog
  if ($LASTEXITCODE -ne 0) { Fail 'npx cap sync android' $copyLog }

  # Shell APK never bundles directory apps.
  foreach ($rel in 'android\app\src\main\assets\apps', 'android\app\src\main\assets\public\apps') {
    $dir = Join-Path $root $rel
    if (Test-Path $dir) { Remove-Item $dir -Recurse -Force }
  }

  $gradleLog = Join-Path $tmp 'gradle.log'
  Push-Location (Join-Path $root 'android')
  .\gradlew.bat assembleDebug --offline --no-daemon *> $gradleLog
  $code = $LASTEXITCODE
  Pop-Location
  if ($code -ne 0) { Fail 'gradlew assembleDebug' $gradleLog }

  $apk = Join-Path $root 'android\app\build\outputs\apk\debug\app-debug.apk'
  $out = Join-Path $root 'docklet-debug.apk'
  Copy-Item $apk $out -Force

  # Optional local release keystore; never commit it.
  $ksFile = Join-Path $root 'android\docklet-release.jks'
  if (Test-Path -LiteralPath $ksFile) {
    $buildTools = Get-ChildItem (Join-Path $sdk 'build-tools') -Directory -ErrorAction SilentlyContinue |
      Sort-Object Name -Descending | Select-Object -First 1
    $apksigner = if ($buildTools) { Join-Path $buildTools.FullName 'apksigner.bat' } else { $null }
    if ($apksigner -and (Test-Path $apksigner)) {
      $signed = Join-Path $root 'docklet-signed.apk'
      & $apksigner sign --in $out --out $signed --ks $ksFile --ks-key-alias docklet --ks-pass pass:docklet-release --key-pass pass:docklet-release *> (Join-Path $tmp 'apksigner.log')
      if ($LASTEXITCODE -eq 0 -and (Test-Path $signed)) {
        Copy-Item $signed $out -Force
        Remove-Item $signed -Force -ErrorAction SilentlyContinue
      }
    }
  }

  $sizeMb = [math]::Round((Get-Item $out).Length / 1MB, 2)
  $buildId = Get-Date -Format 'yyyyMMdd-HHmm'
  $bundle = Get-ChildItem (Join-Path $root 'dist\assets') -Filter 'index-*.js' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
  if ($bundle) {
    $hit = Select-String -Path $bundle.FullName -Pattern '20\d{6}-\d{4}' -AllMatches | Select-Object -First 1
    if ($hit) { $buildId = $hit.Matches[0].Value }
  }

  Write-Host "OK  APK $sizeMb MB  build $buildId"
  Write-Host "    $out"
} finally {
  Pop-Location
}
