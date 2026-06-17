param(
    [string]$OutputZip = "$PSScriptRoot\..\arc-dark-sample-pack.zip"
)

$ErrorActionPreference = "Stop"

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

function Resolve-FullPath([string]$Path) {
    $executionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($Path)
}

function Add-ZipTextEntry(
    [System.IO.Compression.ZipArchive]$Archive,
    [string]$EntryName,
    [string]$Content
) {
    $entry = $Archive.CreateEntry($EntryName, [System.IO.Compression.CompressionLevel]::Optimal)
    $writer = New-Object System.IO.StreamWriter($entry.Open(), (New-Object System.Text.UTF8Encoding($false)))
    try {
        $writer.Write($Content)
    } finally {
        $writer.Dispose()
    }
}

function Add-ZipFileEntry(
    [System.IO.Compression.ZipArchive]$Archive,
    [string]$SourcePath,
    [string]$EntryName
) {
    if (-not (Test-Path -LiteralPath $SourcePath -PathType Leaf)) {
        throw "Missing sample asset: $SourcePath"
    }
    [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
        $Archive,
        $SourcePath,
        $EntryName,
        [System.IO.Compression.CompressionLevel]::Optimal
    ) | Out-Null
}

$workspaceRoot = Resolve-FullPath "$PSScriptRoot\.."
$outputFull = Resolve-FullPath $OutputZip
if (-not $outputFull.StartsWith($workspaceRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Output path is outside workspace: $outputFull"
}

if (Test-Path -LiteralPath $outputFull) {
    Remove-Item -LiteralPath $outputFull -Force
}

$packJson = [pscustomobject]@{
    formatVersion = 1
    id = "sample_pack"
    name = "Arc Dark Sample Pack"
    description = "Small import test pack generated from bundled Arc Dark assets."
} | ConvertTo-Json -Depth 4

$assetsRoot = Join-Path $workspaceRoot "app\src\main\assets"
$file = [System.IO.File]::Open($outputFull, [System.IO.FileMode]::CreateNew)
try {
    $archive = New-Object System.IO.Compression.ZipArchive(
        $file,
        [System.IO.Compression.ZipArchiveMode]::Create,
        $false
    )
    try {
        Add-ZipTextEntry $archive "pack.json" $packJson
        Add-ZipFileEntry $archive (Join-Path $assetsRoot "img\track.png") "assets/img/track.png"
        Add-ZipFileEntry $archive (Join-Path $assetsRoot "img\note.png") "assets/img/note.png"
        Add-ZipFileEntry $archive (Join-Path $assetsRoot "models\tap_l.png") "assets/models/tap_l.png"
    } finally {
        $archive.Dispose()
    }
} finally {
    $file.Dispose()
}

Write-Output "Generated $outputFull"
