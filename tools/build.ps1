param(
    [switch]$Lint,
    [switch]$Clean
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$jdk = Join-Path $root ".tools\jdk17"
$gradle = Join-Path $root ".tools\gradle-8.2.1\bin\gradle.bat"
$project = Join-Path $root "shell"

if (-not (Test-Path -LiteralPath $jdk)) {
    throw "缺少项目 JDK：$jdk"
}
if (-not (Test-Path -LiteralPath $gradle)) {
    throw "缺少项目 Gradle：$gradle"
}

$env:JAVA_HOME = $jdk
$env:GRADLE_USER_HOME = Join-Path $root ".gradle-home"

if ($Clean) {
    & $gradle -p $project clean --console=plain --no-daemon --no-watch-fs
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

$targets = @(":core:test", ":app:assembleDebug")
if ($Lint) {
    $targets += ":app:lintDebug"
}

& $gradle -p $project @targets --console=plain --no-daemon --no-watch-fs
$code = $LASTEXITCODE

# Keep one APK per successful build so any version can be reinstalled.
if ($code -eq 0) {
    try {
        & (Join-Path $PSScriptRoot "snapshot.ps1") -Note "build" | Out-Host
    } catch {
        Write-Host ("snapshot failed: " + $_.Exception.Message) -ForegroundColor Yellow
    }
}

if ($code -ne 0) {
    Write-Host "构建失败，退出码 $code" -ForegroundColor Red
}
exit $code
