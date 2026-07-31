# Arc customize

[![Release](https://img.shields.io/github/v/release/Uncookedboots456/Arc_customize)](https://github.com/Uncookedboots456/Arc_customize/releases/latest)
[![Android](https://img.shields.io/badge/minSdk-23-3DDC84)](#兼容性--compatibility)
[![LSPosed](https://img.shields.io/badge/LSPosed-required-6C5CE7)](#快速开始--quick-start)

## 项目介绍 / Project Overview

Arc customize 是一个面向 Arcaea 官方 Android 客户端的 LSPosed/Xposed 运行时材质替换模块。它在 `moe.low.arc` 进程读取资源时按启用顺序应用材质包，不修改官方 APK、包名、签名、DEX、资源表或原生库。

Arc customize is an LSPosed/Xposed runtime material override module for the official Android version of Arcaea. It applies enabled material packs when the `moe.low.arc` process reads assets, without modifying the official APK, package name, signature, DEX, resource table, or native libraries.

0.6 起，内置材质包只分发索引和生成规则，不再在模块 APK 中携带游戏图片或封面。官方源文件从用户设备上已安装的游戏读取，经文件大小和 SHA-256 校验后在本机临时缓存；透明纹理由设备生成。

Starting with 0.6, the built-in pack distributes only indexes and generation rules—no game images or cover artwork are bundled in the module APK. Official source files are read from the user's installed game, verified by file size and SHA-256, and cached locally; transparent textures are generated on-device.

> 本项目是非官方社区项目，与 lowiro 或 Arcaea 官方无隶属或授权关系。使用者应遵守所在地区法律、游戏条款以及所导入第三方资源的许可。
>
> This is an unofficial community project and is not affiliated with or endorsed by lowiro or Arcaea. Users are responsible for complying with applicable laws, game terms, and the licenses of imported third-party assets.

## 主要功能 / Features

- 运行时材质覆盖，无需修改或重签官方游戏 APK。<br>
  Runtime material overrides without modifying or re-signing the official game APK.
- 可滚动的材质包覆盖栈，支持启用、禁用和调整优先级。<br>
  Ordered material-pack stack with enable, disable, and priority controls.
- 内置 `派尔姆猫_dark` 索引包，以及第三方 ZIP 材质包导入。<br>
  Built-in `派尔姆猫_dark` indexed pack and third-party ZIP pack import.
- 从游戏的下载内容目录或 APK/split assets 读取官方源，并逐项校验。<br>
  Resolves official sources from downloaded game content or APK/split assets with per-file verification.
- Java `AssetManager` 兜底与 Cocos 原生 `AAsset*` hook。<br>
  Java `AssetManager` fallback plus native Cocos `AAsset*` hooks.
- 中英文界面、深浅色主题、状态摘要和诊断信息复制。<br>
  Chinese/English UI, dark/light themes, status summary, and diagnostics copy.

## 兼容性 / Compatibility

| 项目 / Item | 当前状态 / Current status |
|---|---|
| 模块包名 / Module package | `dev.arc.assets` |
| 目标包名 / Target package | `moe.low.arc` |
| 当前版本 / Current version | `0.6 (7)` |
| 索引基线 / Index baseline | Arcaea `6.16.0c` |
| LSPosed 作用域 / Scope | Arcaea (`moe.low.arc`) |
| 模块最低 API / Module minSdk | Android API 23 |
| 已验证设备 / Validated device | Android 16 / API 36 |
| ABI | `arm64-v8a`, `armeabi-v7a` |
| 运行目录 / Runtime root | `/sdcard/Android/media/moe.low.arc/ArcDark/` |

索引基于 6.16.0c 官方底包生成。游戏更新后，如果官方资源内容或路径发生变化，相关索引项可能因哈希校验失败而被安全拒绝，需要重新生成兼容索引。

The index was generated from the official 6.16.0c package. After a game update, entries whose official content or path changed may be safely rejected by hash verification until a compatible index is generated.

## 快速开始 / Quick Start

1. 从 [Releases](https://github.com/Uncookedboots456/Arc_customize/releases/latest) 下载并安装 `Arc_customize.apk`。<br>
   Download and install `Arc_customize.apk` from [Releases](https://github.com/Uncookedboots456/Arc_customize/releases/latest).
2. 在 LSPosed 中启用 `Arc customize`，作用域只选择 Arcaea。<br>
   Enable `Arc customize` in LSPosed and select only Arcaea as its scope.
3. 打开 Arc customize，启用注入并选择需要的材质包。<br>
   Open Arc customize, enable injection, and select the desired material pack.
4. 点击“打开 Arcaea”应用当前配置；后续重启会复用已保存状态。<br>
   Tap **Open Arcaea** to apply the current configuration; later restarts reuse the saved state.

启用或调整材质包后应完整重启 Arcaea。若游戏仍在后台，请先强制停止再启动。

Fully restart Arcaea after enabling or reordering packs. If the game is still in the background, force-stop it before launching again.

## 内置材质包 / Built-in Pack

`pairumu_cat_dark` 在 UI 中显示为 `派尔姆猫_dark`，针对 Arcaea 6.16.0c 包含 296 条覆盖规则：

`pairumu_cat_dark`, displayed as `派尔姆猫_dark`, contains 296 override rules for Arcaea 6.16.0c:

| 模式 / Mode | 数量 / Count | 说明 / Description |
|---|---:|---|
| `alias` | 170 | 复用已安装游戏中经哈希验证的官方资源 / Reuse verified official assets from the installed game |
| `transparent` | 124 | 在设备上生成对应尺寸的透明 PNG / Generate correctly sized transparent PNGs on-device |
| `passthrough` | 2 | 使用相同路径下经验证的官方源 / Use the verified official source at the same path |

材质包封面同样从已安装游戏读取并校验后显示；模块 APK 中不包含该封面。

The material-pack cover is also loaded and verified from the installed game; it is not bundled in the module APK.

## 第三方材质包 / Third-party Packs

第三方包使用 ZIP 格式，根目录必须包含 `pack.json`，资源文件放在允许的 `assets/` 子目录中。可选的 `version`、`description`、`author` 和 `cover` 会显示在 UI 中。

Third-party packs use ZIP format. A root-level `pack.json` is required, and resource files belong in allowed `assets/` subdirectories. Optional `version`, `description`, `author`, and `cover` fields are displayed in the UI.

```text
pack.json
cover.png
assets/img/track.png
assets/img/note.png
assets/models/tap_l.png
```

```json
{
  "formatVersion": 1,
  "id": "sample_pack",
  "name": "Sample Pack",
  "version": "1.0",
  "description": "Example third-party material pack.",
  "author": "Author",
  "cover": "cover.png",
  "Change": {
    "Default Fonts": "fonts"
  }
}
```

默认允许的资源目录为 `audio`、`char`、`Default Fonts`、`img`、`layout`、`models`、`particle`、`songs`、`start_up` 和 `voice`。`Change` 用于把 ZIP 内的源目录映射到目标资源目录；未声明的目录使用同名映射。

The default allowed asset directories are `audio`, `char`, `Default Fonts`, `img`, `layout`, `models`, `particle`, `songs`, `start_up`, and `voice`. `Change` maps a source directory inside the ZIP to a target asset directory; undeclared directories use same-name mapping.

导入后的文件由目标进程保存到：

Imported files are stored by the target process under:

```text
/sdcard/Android/media/moe.low.arc/ArcDark/packs/<packId>/
```

第三方 ZIP 中的图片由资源包作者或使用者自行提供；Arc customize 不会替第三方资源执行版权授权判断。

Images in third-party ZIP files are supplied by the pack author or user. Arc customize does not determine or grant licenses for third-party content.

## 工作原理 / How It Works

启用顺序保存在 `active_pack_order` 中。模块从上到下查找覆盖项；没有命中或列表为空时不拦截，让游戏读取原始资源。控制文件保存在：

Enabled order is stored in `active_pack_order`. The module searches overrides from top to bottom; if no entry matches or the list is empty, the read is not intercepted and the game uses its original asset. The target-side control file is:

```text
/sdcard/Android/media/moe.low.arc/ArcDark/control.json
```

格式 2 索引支持：

Format-version-2 indexes support:

- `alias`：目标路径映射到另一条官方源路径 / map a target path to another official source path
- `transparent`：按记录尺寸生成全透明 PNG / generate a fully transparent PNG at the recorded dimensions
- `passthrough`：校验并使用目标同路径官方源 / verify and use the official source at the target path

所有官方源都必须同时匹配记录的文件大小和 SHA-256。目标进程优先检查 `files/cb/active` 下载内容，再检查 APK 与 split assets。

Every official source must match both the recorded file size and SHA-256. The target process checks downloaded content under `files/cb/active` and then APK/split assets.

## 构建 / Build

需要 Android SDK 35、NDK `27.2.12479018`、CMake `3.22.1` 和 JDK 17 或更高版本。项目使用已提交的 Gradle Wrapper。

Android SDK 35, NDK `27.2.12479018`, CMake `3.22.1`, and JDK 17 or newer are required. The checked-in Gradle Wrapper is used.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Build-Debug.ps1
```

或直接运行 / Or run Gradle directly:

```powershell
$env:JAVA_TOOL_OPTIONS = "--enable-native-access=ALL-UNNAMED"
.\gradlew.bat test lintDebug assembleDebug
```

发布签名可通过 Gradle properties、`local.properties` 或以下环境变量提供，切勿提交密钥库或密码：

Release signing may be supplied through Gradle properties, `local.properties`, or the following environment variables. Never commit keystores or passwords:

- `ARC_CUSTOMIZE_STORE_FILE`
- `ARC_CUSTOMIZE_STORE_PASSWORD`
- `ARC_CUSTOMIZE_KEY_ALIAS`
- `ARC_CUSTOMIZE_KEY_PASSWORD`

## 重建索引 / Rebuilding an Index

`scripts/Find-AssetHashMapping.py` 将本地旧材质包与解包后的官方资源逐项比较；`scripts/Build-IndexedOverridePack.py` 把验证报告转换为格式 2 索引。旧图片和官方 APK 只作为本地输入，不应复制到仓库或发布 APK。

`scripts/Find-AssetHashMapping.py` compares a locally held legacy pack with unpacked official assets. `scripts/Build-IndexedOverridePack.py` converts the verified report into a format-version-2 index. Legacy images and the official APK are local inputs only and must not be copied into the repository or release APK.

## 更新说明 / Changelog

### 0.6 — Indexed assets / 索引资源

- 将内置材质从图片载荷迁移为 `alias`、`transparent`、`passthrough` 索引规则。<br>
  Migrated the built-in material payload to `alias`, `transparent`, and `passthrough` index rules.
- 适配 Arcaea 6.16.0c；内置可选包包含 296 条覆盖，项目兼容索引包含 348 条规则。<br>
  Updated for Arcaea 6.16.0c with 296 built-in selectable overrides and 348 project compatibility rules.
- 支持从游戏下载内容与 APK/split assets 双来源解析官方文件，并执行大小与 SHA-256 校验。<br>
  Added dual-source resolution from downloaded game content and APK/split assets with size and SHA-256 verification.
- 透明纹理改为设备生成，材质包封面改为从已安装游戏读取。<br>
  Transparent textures are now generated on-device, and the pack cover is loaded from the installed game.
- 发布 APK 不再携带游戏图片，体积由 0.5.1 的约 116.7 MB 降至约 447 KB。<br>
  The release APK no longer bundles game images, reducing its size from about 116.7 MB in 0.5.1 to about 447 KB.
- Android 16 真机冷缓存验证通过，确认 296 个 native 覆盖注册并产生实际材质命中。<br>
  Cold-cache validation passed on an Android 16 device, confirming 296 native mappings and real material hits.

完整设备验证与资源映射报告位于 `docs/evidence/`。

Full device-validation and resource-mapping reports are available under `docs/evidence/`.

## 文档 / Documentation

- 技术路线 / Technical route: [`docs/current-technical-route-report.md`](docs/current-technical-route-report.md)
- 0.6 设备验证 / 0.6 device validation: [`docs/evidence/arc-customize-0.6-device-validation.md`](docs/evidence/arc-customize-0.6-device-validation.md)
- 6.16.0c 资源映射 / 6.16.0c resource mapping: [`docs/evidence/arcaea-6.16.0c-resource-mapping/README.md`](docs/evidence/arcaea-6.16.0c-resource-mapping/README.md)

## 权利说明 / Rights Notice

Arcaea、其名称、图像及其他游戏内容的权利归其各自权利人所有，且不包含在 0.6 发布 APK 中。

Arcaea, its name, artwork, and other game content belong to their respective rights holders and are not included in the 0.6 release APK.
