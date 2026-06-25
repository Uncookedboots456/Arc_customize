$ErrorActionPreference = "Stop"

$workspaceRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$gradle = Join-Path $workspaceRoot "gradlew.bat"

if (-not (Test-Path -LiteralPath $gradle -PathType Leaf)) {
    throw "Missing Gradle Wrapper: $gradle"
}

if (-not $env:ANDROID_HOME -and $env:ANDROID_SDK_ROOT) {
    $env:ANDROID_HOME = $env:ANDROID_SDK_ROOT
}
if (-not $env:ANDROID_SDK_ROOT -and $env:ANDROID_HOME) {
    $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
}
$env:JAVA_TOOL_OPTIONS = "--enable-native-access=ALL-UNNAMED"

& $gradle --no-daemon :app:assembleDebug
