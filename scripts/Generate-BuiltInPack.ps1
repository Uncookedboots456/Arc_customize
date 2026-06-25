param(
    [string]$OriginalApk = "C:\Users\comma\Desktop\arcaea_6.15.0c.apk",
    [string]$FixedApk = "C:\Users\comma\Desktop\base.apk.1",
    [string]$OutputRoot = "$PSScriptRoot\..\app\src\main\assets\packs\pairumu_cat_dark",
    [string]$PackId = "pairumu_cat_dark",
    [string]$PackName = "派尔姆猫_dark",
    [string]$PackVersion = "6.15.0c",
    [string]$CoverPath = "C:\Users\comma\Desktop\assets\img\default_jacket_256.jpg",
    [string]$Aapt2Path = ""
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.IO.Compression.FileSystem

function Resolve-FullPath([string]$Path) {
    $executionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Path)
}

function Find-Aapt2 {
    if ($Aapt2Path.Length -gt 0) {
        return Resolve-FullPath $Aapt2Path
    }

    $sdkRoot = $env:ANDROID_HOME
    if (-not $sdkRoot) {
        $sdkRoot = $env:ANDROID_SDK_ROOT
    }
    if (-not $sdkRoot) {
        $sdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    }

    $candidate = Get-ChildItem -Path (Join-Path $sdkRoot "build-tools") -Recurse -Filter "aapt2.exe" -ErrorAction SilentlyContinue |
        Sort-Object FullName -Descending |
        Select-Object -First 1
    if (-not $candidate) {
        throw "Unable to locate aapt2.exe. Pass -Aapt2Path explicitly."
    }
    return $candidate.FullName
}

function Read-Badging([string]$ApkPath) {
    $aapt = Find-Aapt2
    $line = & $aapt dump badging $ApkPath | Select-String -Pattern "^package:" | Select-Object -First 1
    if (-not $line) {
        throw "Unable to read package badging from $ApkPath"
    }
    $text = $line.ToString()
    if ($text -notmatch "^package:\s+name='([^']+)'\s+versionCode='[^']+'\s+versionName='([^']+)'") {
        throw "Unable to parse package badging: $text"
    }
    [pscustomobject]@{
        PackageName = $matches[1]
        VersionName = $matches[2]
    }
}

function Assert-ApkIdentity([string]$ApkPath, [string[]]$AllowedPackages, [string]$ExpectedVersion, [string]$Label) {
    $badging = Read-Badging $ApkPath
    if ($AllowedPackages -notcontains $badging.PackageName) {
        throw "$Label package mismatch: expected $($AllowedPackages -join ' or '), got $($badging.PackageName)"
    }
    if ($badging.VersionName -ne $ExpectedVersion) {
        throw "$Label version mismatch: expected $ExpectedVersion, got $($badging.VersionName)"
    }
    return $badging
}

function Get-Sha256HexFromStream([System.IO.Stream]$Stream) {
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        ($sha.ComputeHash($Stream) | ForEach-Object { $_.ToString("x2") }) -join ""
    } finally {
        $sha.Dispose()
    }
}

function Get-ZipAssetMap([string]$Path) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Path)
    $map = @{}
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName.StartsWith("assets/", [System.StringComparison]::Ordinal) -and $entry.Name.Length -gt 0) {
            $map[$entry.FullName] = $entry
        }
    }
    [pscustomobject]@{
        Zip = $zip
        Map = $map
    }
}

function Get-EntryHash([System.IO.Compression.ZipArchiveEntry]$Entry) {
    $stream = $Entry.Open()
    try {
        return Get-Sha256HexFromStream $stream
    } finally {
        $stream.Dispose()
    }
}

function Copy-ZipEntry([System.IO.Compression.ZipArchiveEntry]$Entry, [string]$Destination) {
    $dir = Split-Path -Parent $Destination
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $inputStream = $Entry.Open()
    try {
        $outputStream = [System.IO.File]::Open($Destination, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
        try {
            $inputStream.CopyTo($outputStream)
        } finally {
            $outputStream.Dispose()
        }
    } finally {
        $inputStream.Dispose()
    }
}

function ConvertTo-RelativeModulePath([string]$AssetPath) {
    $AssetPath.Substring("assets/".Length)
}

function Get-FileSha256([string]$Path) {
    (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

$originalFull = Resolve-FullPath $OriginalApk
$fixedFull = Resolve-FullPath $FixedApk
$outputFull = Resolve-FullPath $OutputRoot
$workspaceFull = Resolve-FullPath "$PSScriptRoot\.."
$assetsPackRoot = Resolve-FullPath "$PSScriptRoot\..\app\src\main\assets\packs"

if (-not $outputFull.StartsWith($assetsPackRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Output path is outside app/src/main/assets/packs: $outputFull"
}
if ($PackId -ne "pairumu_cat_dark") {
    throw "This script is scoped to the built-in pack id pairumu_cat_dark"
}
if (-not $outputFull.StartsWith($workspaceFull, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Output path is outside workspace: $outputFull"
}

$originalBadging = Assert-ApkIdentity $originalFull @("moe.low.arc") $PackVersion "Original APK"
$fixedBadging = Assert-ApkIdentity $fixedFull @("moe.low.arc", "moe.low.ard") $PackVersion "Fixed APK"

$originalAssets = Get-ZipAssetMap $originalFull
$fixedAssets = Get-ZipAssetMap $fixedFull

try {
    $changed = New-Object System.Collections.Generic.List[object]
    $same = 0
    $fixedOnly = 0

    foreach ($assetPath in ($fixedAssets.Map.Keys | Sort-Object)) {
        $fixedEntry = $fixedAssets.Map[$assetPath]
        $originalEntry = $originalAssets.Map[$assetPath]
        if ($null -eq $originalEntry) {
            $fixedOnly++
            continue
        }

        $originalHash = Get-EntryHash $originalEntry
        $fixedHash = Get-EntryHash $fixedEntry
        if ($originalHash -eq $fixedHash) {
            $same++
            continue
        }

        $modulePath = ConvertTo-RelativeModulePath $assetPath
        $relative = $modulePath -replace "/", [System.IO.Path]::DirectorySeparatorChar
        $outputPath = Join-Path $outputFull $relative
        $changed.Add([pscustomobject]@{
            AssetPath = $assetPath
            ModulePath = $modulePath
            OutputPath = $outputPath
            Size = [int64]$fixedEntry.Length
            Sha256 = $fixedHash
        })
    }

    if (Test-Path -LiteralPath $outputFull) {
        Remove-Item -LiteralPath $outputFull -Recurse -Force
    }
    New-Item -ItemType Directory -Force -Path (Join-Path $outputFull "arc_overrides") | Out-Null

    foreach ($entry in $changed) {
        Copy-ZipEntry $fixedAssets.Map[$entry.AssetPath] $entry.OutputPath
        $copiedHash = Get-FileSha256 $entry.OutputPath
        if ($copiedHash -ne $entry.Sha256) {
            throw "Copied hash mismatch for $($entry.AssetPath)"
        }
    }

    $coverFull = Resolve-FullPath $CoverPath
    if (Test-Path -LiteralPath $coverFull) {
        Copy-Item -LiteralPath $coverFull -Destination (Join-Path $outputFull "cover.jpg") -Force
    }

    $generatedAtUtc = [DateTime]::UtcNow.ToString("o")
    $packJson = [ordered]@{
        formatVersion = 1
        id = $PackId
        name = $PackName
        version = $PackVersion
        description = "Built-in 6.15.0c material differences generated from same-name changed assets."
        author = "Arc customize"
        cover = "cover.jpg"
        assetCount = $changed.Count
        generatedAtUtc = $generatedAtUtc
    }
    $index = [ordered]@{
        targetPackage = "moe.low.arc"
        source = "built_in_apk_diff"
        packId = $PackId
        generatedAtUtc = $generatedAtUtc
        entries = @($changed | ForEach-Object {
            [ordered]@{
                assetPath = $_.AssetPath
                modulePath = $_.ModulePath
                size = $_.Size
                sha256 = $_.Sha256
                materialize = $true
            }
        })
    }
    $summary = [ordered]@{
        source = "built_in_apk_diff"
        packId = $PackId
        originalApk = [ordered]@{
            fileName = Split-Path -Leaf $originalFull
            packageName = $originalBadging.PackageName
            versionName = $originalBadging.VersionName
            sha256 = Get-FileSha256 $originalFull
        }
        fixedApk = [ordered]@{
            fileName = Split-Path -Leaf $fixedFull
            packageName = $fixedBadging.PackageName
            versionName = $fixedBadging.VersionName
            sha256 = Get-FileSha256 $fixedFull
        }
        sameNameChangedAssets = $changed.Count
        sameNameUnchangedAssets = $same
        fixedOnlyAssetsExcluded = $fixedOnly
        includedPolicy = "Only same-name assets with different SHA-256 content are included. Assets only present in the fixed APK are excluded."
        generatedAtUtc = $generatedAtUtc
    }

    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText((Join-Path $outputFull "pack.json"), ($packJson | ConvertTo-Json -Depth 6), $utf8NoBom)
    [System.IO.File]::WriteAllText((Join-Path $outputFull "arc_overrides\index.json"), ($index | ConvertTo-Json -Depth 8), $utf8NoBom)
    [System.IO.File]::WriteAllText((Join-Path $outputFull "arc_overrides\summary.json"), ($summary | ConvertTo-Json -Depth 6), $utf8NoBom)

    Write-Output "Generated built-in pack '$PackId' with $($changed.Count) assets in $outputFull"
    Write-Output "Unchanged same-name assets: $same"
    Write-Output "Fixed-only assets excluded: $fixedOnly"
} finally {
    $originalAssets.Zip.Dispose()
    $fixedAssets.Zip.Dispose()
}
