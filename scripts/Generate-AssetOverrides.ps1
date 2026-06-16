param(
    [string]$OriginalApk = "C:\Users\comma\Desktop\arc_dark\arcaea_6.14.12c.apk",
    [string]$FixedApk = "C:\Users\comma\Desktop\arc_dark\fixed.apk",
    [string]$OutputRoot = "$PSScriptRoot\..\app\src\main\assets"
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.IO.Compression.FileSystem

function Resolve-FullPath([string]$Path) {
    $executionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Path)
}

function Get-ZipMap([string]$Path) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $map = @{}
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName.EndsWith("/")) {
                continue
            }
            $map[$entry.FullName] = [pscustomobject]@{
                Name = $entry.FullName
                Length = [int64]$entry.Length
                CompressedLength = [int64]$entry.CompressedLength
            }
        }
        return $map
    } finally {
        $zip.Dispose()
    }
}

function Get-Sha256Hex([byte[]]$Bytes) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        ($sha.ComputeHash($Bytes) | ForEach-Object { $_.ToString("x2") }) -join ""
    } finally {
        $sha.Dispose()
    }
}

function ConvertTo-JsonLiteral([string]$Value) {
    [System.Text.Json.JsonSerializer]::Serialize($Value)
}

$outputFull = Resolve-FullPath $OutputRoot
$workspaceFull = Resolve-FullPath "$PSScriptRoot\.."
if (-not $outputFull.StartsWith($workspaceFull, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Output path is outside workspace: $outputFull"
}

$metadataRoot = Join-Path $outputFull "arc_overrides"
foreach ($generatedPath in @($metadataRoot, (Join-Path $outputFull "img"), (Join-Path $outputFull "models"))) {
    $generatedFull = Resolve-FullPath $generatedPath
    if (-not $generatedFull.StartsWith($outputFull, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Generated path is outside assets root: $generatedFull"
    }
    if (Test-Path -LiteralPath $generatedFull) {
        Remove-Item -LiteralPath $generatedFull -Recurse -Force
    }
}
New-Item -ItemType Directory -Force -Path $metadataRoot | Out-Null

$original = Get-ZipMap $OriginalApk
$fixed = Get-ZipMap $FixedApk
$changed = New-Object System.Collections.Generic.List[string]

foreach ($name in $fixed.Keys) {
    if (-not $name.StartsWith("assets/", [System.StringComparison]::Ordinal)) {
        continue
    }

    $fixedEntry = $fixed[$name]
    $originalEntry = $original[$name]
    if ($null -eq $originalEntry -or
        $fixedEntry.Length -ne $originalEntry.Length -or
        $fixedEntry.CompressedLength -ne $originalEntry.CompressedLength) {
        $changed.Add($name)
    }
}

$fixedZip = [System.IO.Compression.ZipFile]::OpenRead($FixedApk)
try {
    $entries = New-Object System.Collections.Generic.List[object]
    foreach ($name in ($changed | Sort-Object)) {
        $entry = $fixedZip.GetEntry($name)
        if ($null -eq $entry) {
            throw "Missing fixed entry: $name"
        }

        $modulePath = $name -replace "^assets/", ""
        $relative = $modulePath -replace "/", [System.IO.Path]::DirectorySeparatorChar
        $outputPath = Join-Path $outputFull $relative
        $outputDir = Split-Path -Parent $outputPath
        New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

        $stream = $entry.Open()
        $memory = New-Object System.IO.MemoryStream
        try {
            $stream.CopyTo($memory)
        } finally {
            $stream.Dispose()
        }

        $bytes = $memory.ToArray()
        [System.IO.File]::WriteAllBytes($outputPath, $bytes)

        $entries.Add([pscustomobject]@{
            assetPath = $name
            modulePath = $modulePath
            size = [int64]$bytes.Length
            sha256 = Get-Sha256Hex $bytes
            materialize = $true
        })
    }
} finally {
    $fixedZip.Dispose()
}

$indexPath = Join-Path $metadataRoot "index.json"
$summaryPath = Join-Path $metadataRoot "summary.json"

$index = [pscustomobject]@{
    targetPackage = "moe.low.arc"
    originalApk = Split-Path -Leaf $OriginalApk
    fixedApk = Split-Path -Leaf $FixedApk
    generatedAtUtc = [DateTime]::UtcNow.ToString("o")
    entries = $entries
}
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($indexPath, ($index | ConvertTo-Json -Depth 5), $utf8NoBom)

$summary = [pscustomobject]@{
    originalApk = @{
        path = $OriginalApk
        sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $OriginalApk).Hash.ToLowerInvariant()
    }
    fixedApk = @{
        path = $FixedApk
        sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $FixedApk).Hash.ToLowerInvariant()
    }
    includedAssets = $entries.Count
    excludedPolicy = "Only added or changed ZIP entries under assets/ are included. Manifest, dex, resources, native libraries, and META-INF signatures are excluded."
}
[System.IO.File]::WriteAllText($summaryPath, ($summary | ConvertTo-Json -Depth 5), $utf8NoBom)

Write-Output "Generated $($entries.Count) asset overrides in $outputFull"
