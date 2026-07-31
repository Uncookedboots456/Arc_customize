# Arc customize

LSPosed/Xposed module for applying Arc customize material overrides to the official Arcaea package (`moe.low.arc`) at runtime. The official APK, package name, signature, dex, resources, and native libraries are not modified.

## Current MVP

- Module package: `dev.arc.assets`
- Target package / LSPosed scope: `moe.low.arc`
- Version: `0.6`
- Indexed compatibility rules: 348
- Supported ABI: `arm64-v8a`, `armeabi-v7a`
- Runtime root: `/sdcard/Android/media/moe.low.arc/ArcDark/`
- Project-side current material pack: `Arc_dark/packs/difference/`
- Built-in selectable pack: `pairumu_cat_dark` (`派尔姆猫_dark`, 296 overrides)

The launcher UI is titled `Arc customize` and provides an injection switch, a current material difference count, a scrollable material pack stack, ZIP import, status summary, diagnostics copy, refresh, and Open Arcaea.

## Material Packs

- `difference` is the project-side 348-entry compatibility index under `Arc_dark/packs/difference/`. It contains no game images and is not shown as a selectable runtime pack.
- `pairumu_cat_dark` is the selectable index-only pack shown as `派尔姆猫_dark`. For Arcaea `6.16.0c` it contains 170 verified aliases to installed official assets, 124 generated transparent textures, and 2 same-path passthrough rules. No gameplay image or cover is bundled; the UI loads its verified cover from the installed game. ZIP imports cannot reuse this reserved ID.

Third-party packs are imported from ZIP files. A ZIP pack must contain root-level `pack.json` plus files under allowed `assets/` folders. Optional `version`, `description`, `author`, and root-level `cover` metadata are shown in the UI. `formatVersion` is the import format version; `version` is free-form pack display metadata.

```text
pack.json
cover.png
assets/img/track.png
assets/img/note.png
assets/models/tap_l.png
```

Example `pack.json`:

```json
{
  "formatVersion": 1,
  "id": "sample_pack",
  "name": "Arc Dark Sample Pack",
  "version": "1.0",
  "description": "Example third-party material pack.",
  "author": "Arc Dark",
  "cover": "cover.png",
  "Change": {
    "Default Fonts": "fonts"
  }
}
```

The default allowed `assets/` folders are `audio`, `char`, `Default Fonts`, `img`, `layout`, `models`, `particle`, `songs`, `start_up`, and `voice`. `Change` maps a ZIP source folder under `assets/` to the target asset folder; folders not listed in `Change` use their own folder name.

The module extracts imported ZIP packs in the target process to:

```text
/sdcard/Android/media/moe.low.arc/ArcDark/packs/<packId>/
```

The active configuration is an ordered override stack stored as `active_pack_order`. Resource lookup checks enabled packs from top to bottom. If the list is empty, or no enabled pack contains a requested asset, the hook does not intercept it and Arcaea uses its original asset. The old `<default>` bundled row is no longer displayed or used as the default active layer.

The UI stores its local control file under `dev.arc.assets`. Pressing Open Arcaea sends the current control state to the target process, which persists the active state to:

```text
/sdcard/Android/media/moe.low.arc/ArcDark/control.json
```

Later Arcaea restarts reuse that target-side control file.

## Build

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Build-Debug.ps1
```

The project expects Android SDK 35, NDK `27.2.12479018`, and CMake `3.22.1`. The build script uses the checked-in Gradle Wrapper and sets `JAVA_TOOL_OPTIONS=--enable-native-access=ALL-UNNAMED` because AGP's Prefab path emits restricted native-access warnings under newer JDKs.

Equivalent direct Gradle commands:

```powershell
.\gradlew.bat test assembleRelease
.\gradlew.bat :app:assembleDebug
```

## Release Signing

Release signing is optional and read from Gradle properties, `local.properties`, or environment variables. Do not commit keystores or passwords.

```properties
arcCustomizeStoreFile=C:\\path\\to\\release.keystore
arcCustomizeStorePassword=...
arcCustomizeKeyAlias=...
arcCustomizeKeyPassword=...
```

Equivalent environment variables are `ARC_CUSTOMIZE_STORE_FILE`, `ARC_CUSTOMIZE_STORE_PASSWORD`, `ARC_CUSTOMIZE_KEY_ALIAS`, and `ARC_CUSTOMIZE_KEY_PASSWORD`.

## Rebuild an Index

`scripts/Find-AssetHashMapping.py` compares a locally held legacy pack with unpacked official assets. `scripts/Build-IndexedOverridePack.py` converts the verified report into format-version-2 `alias`, `transparent`, and `passthrough` entries. The legacy images and official APK remain local inputs and must not be copied into the repository or release APK.

## Runtime Notes

The module keeps the LSPosed scope at `moe.low.arc`. Indexed official sources and the built-in pack cover are read from the user's installed game, verified by size and SHA-256, and cached only on that device; transparent textures are generated on-device. Java `AssetManager.open/openFd` hooks remain as a fallback, and `libarcdarkhook.so` hooks Cocos native calls to `AAssetManager_open`, `AAsset_read`, `AAsset_getLength`, and `AAsset_close`.

Do not restore `System.load` or `System.loadLibrary` hooks; the current route uses delayed `xhook_refresh()` instead.

More detail is in `docs/current-technical-route-report.md`.
