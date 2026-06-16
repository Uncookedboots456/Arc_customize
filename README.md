# Arc Dark

LSPosed/Xposed module that keeps the official Arcaea package (`moe.low.arc`) intact and applies the `fixed.apk` asset changes at runtime.

## What Is Included

- Only changed or added APK entries below `assets/` are bundled.
- `AndroidManifest.xml`, dex files, `resources.arsc`, native libraries, and signatures from `fixed.apk` are intentionally ignored.
- Overrides are loaded from module assets through `AssetProvider`; `ExternalAssetProvider` is a disabled placeholder for a later external-materials mode.

## Regenerate Assets

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Generate-AssetOverrides.ps1
```

The script reads:

- `C:\Users\comma\Desktop\arc_dark\arcaea_6.14.12c.apk`
- `C:\Users\comma\Desktop\arc_dark\fixed.apk`

and writes generated metadata to `app/src/main/assets/arc_overrides` plus override files at the module asset root, such as `app/src/main/assets/img/...`.

## Build

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Build-Debug.ps1
```

The project expects Android SDK 35, NDK `27.2.12479018`, and CMake `3.22.1`. The build script sets `JAVA_TOOL_OPTIONS=--enable-native-access=ALL-UNNAMED` because AGP's Prefab path emits restricted native-access warnings under JDK 24.

## Runtime Notes

The module keeps the LSPosed scope at `moe.low.arc`. Java `AssetManager.open/openFd` hooks remain as a fallback, and `libarcdarkhook.so` hooks Cocos native calls to `AAssetManager_open`, `AAsset_read`, `AAsset_getLength`, and `AAsset_close`.
