$ErrorActionPreference = "Stop"

$sdk = "C:\Users\comma\AppData\Local\Android\Sdk"
$gradle = "C:\Users\comma\.gradle\wrapper\dists\gradle-8.14-bin\38aieal9i53h9rfe7vjup95b9\gradle-8.14\bin\gradle.bat"

$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:JAVA_TOOL_OPTIONS = "--enable-native-access=ALL-UNNAMED"

& $gradle --no-daemon :app:assembleDebug
