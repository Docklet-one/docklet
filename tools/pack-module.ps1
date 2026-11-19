<#
Pack a module directory into a .pkg (plain zip).

Why not Compress-Archive: Windows PowerShell 5.1 writes entry names with
backslashes ("avatars\robot-purple.png"). The shell rejects any entry that
contains a backslash (ModuleManager.extractPackage -> UNSAFE_ENTRY), so such
a package installs as "包内存在绝对路径" and never reaches the module list.

This packer writes forward slashes only, keeps an explicit entry for each
directory, and preserves file bytes exactly.

Usage:
  powershell -ExecutionPolicy Bypass -File tools/pack-module.ps1 `
      -Source apps/dsh-console -Out dist/dsh-console-0.3.53.pkg
#>
param(
    [Parameter(Mandatory = $true)][string]$Source,
    [Parameter(Mandatory = $true)][string]$Out
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
if ((Test-Path (Join-Path $repoRoot 'src\v3')) -and ($Source -like '*dsh-console*')) {
    & node (Join-Path $repoRoot 'tools\build-v3.mjs')
    if ($LASTEXITCODE -ne 0) { throw 'build-v3.mjs failed' }
}
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$src = (Resolve-Path $Source).Path.TrimEnd('\', '/')
if (-not (Test-Path $src -PathType Container)) {
    throw "not a directory: $src"
}
if (Test-Path $Out) { Remove-Item $Out -Force }
$outDir = Split-Path -Parent $Out
if ($outDir -and -not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }

function Get-Relative([string]$full) {
    return ($full.Substring($src.Length + 1) -replace '\\', '/')
}

$zip = [System.IO.Compression.ZipFile]::Open(
    $Out,
    [System.IO.Compression.ZipArchiveMode]::Create
)
try {
    foreach ($dir in (Get-ChildItem -Path $src -Recurse -Directory | Sort-Object FullName)) {
        $name = (Get-Relative $dir.FullName) + '/'
        [void]$zip.CreateEntry($name)
    }
    foreach ($file in (Get-ChildItem -Path $src -Recurse -File | Sort-Object FullName)) {
        $name = Get-Relative $file.FullName
        $entry = $zip.CreateEntry($name, [System.IO.Compression.CompressionLevel]::Optimal)
        $input = $file.OpenRead()
        $output = $entry.Open()
        try {
            $input.CopyTo($output)
        } finally {
            $output.Dispose()
            $input.Dispose()
        }
    }
} finally {
    $zip.Dispose()
}

$item = Get-Item $Out
$hash = (Get-FileHash $Out -Algorithm SHA256).Hash
Write-Output ("{0}  {1:N0} B" -f $item.Name, $item.Length)
Write-Output ("SHA256 {0}" -f $hash)
