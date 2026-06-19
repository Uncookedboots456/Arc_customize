# Arc customize 当前技术路线

## 结论摘要

Arc customize 的 MVP 架构已经完成并通过设备验证。当前路线保持官方 Arcaea 包 `moe.low.arc` 不变，通过 LSPosed 注入目标进程，在运行时替换 348 个素材。

当前已验证能力：

- 桌面入口和控制台式 UI 可用。
- 注入开关可控制是否安装 hook。
- 素材包列表为可滚动覆盖栈，包含 `test_pkg` 和导入的第三方 ZIP 包；旧 `<default>/bundled` 行不再显示。
- UI 用 `当前修改素材 / Difference` 展示当前 348 个替换素材数量。
- `test_pkg` 会复制为目标进程可读的真实文件包。
- 第三方 ZIP 包由 UI 选择后通过 URI 授权交给目标进程解包到 `packs/<packId>/`。
- 生效顺序由 `active_pack_order` 控制，从上到下查找；全部缺失时保留游戏原始素材。
- `active_pack_order` 可为空，表示不启用任何素材包并使用游戏原始资源。
- native map 可以指向 `packs/test_pkg` 下的文件。
- 进入谱面加载后已确认 `ArcDarkNative: asset hit` 指向 `packs/test_pkg`。
- 关闭注入后不会注册 native map，也不会安装 Java/native asset hook。

## 当前工程状态

- 模块包名：`dev.arc.assets`
- 目标包名 / LSPosed 作用域：`moe.low.arc`
- 版本：`0.2.1 (7)`
- native ABI：`arm64-v8a`, `armeabi-v7a`
- 覆盖素材数量：348
- 应用可见名称：`Arc customize`
- 构建脚本：`scripts/Build-Debug.ps1`
- 推荐作用域声明：`AndroidManifest.xml` 中的 `xposedscope=moe.low.arc`
- 入口：legacy Xposed `assets/xposed_init = dev.arc.assets.ArcDarkModule`

## 运行架构

UI 只运行在 `dev.arc.assets` 进程中，用于查看状态、切换注入开关、选择素材包、复制诊断信息和启动 Arcaea。

运行目录由目标进程负责读写：

```text
/sdcard/Android/media/moe.low.arc/ArcDark/
```

目录结构：

```text
ArcDark/
  control.json
  packs/
    test_pkg/
      pack.json
      arc_overrides/index.json
      arc_overrides/summary.json
      img/...
      models/...
    <packId>/
      pack.json
      cover.png
      arc_overrides/index.json
      arc_overrides/summary.json
      assets/...
```

控制流：

1. UI 保存本地配置到 `dev.arc.assets/files/control.json`。
2. 用户点击 `Open Arcaea`。
3. UI 通过启动 intent extras 传递 `injection_enabled`、`active_pack_id` 和 `active_pack_order`。
4. `ArcDarkModule` 在目标 Activity `onCreate` 前读取 extras。
5. 目标进程写入 `/sdcard/Android/media/moe.low.arc/ArcDark/control.json`。
6. 后续普通重启 Arcaea 时，模块直接读取目标侧 `control.json`。

这个设计避免了 `dev.arc.assets` 私有目录跨包读取失败，也不需要 `READ_EXTERNAL_STORAGE`、`WRITE_EXTERNAL_STORAGE` 或 `MANAGE_EXTERNAL_STORAGE`。

第三方 ZIP 导入流：

1. UI 使用 `ACTION_OPEN_DOCUMENT` 选择 ZIP。
2. UI 校验根级 `pack.json`，并把 ZIP `Uri` 通过启动 intent、`ClipData` 和 `FLAG_GRANT_READ_URI_PERMISSION` 传给 Arcaea。
3. 目标进程在安装 hook 前读取该 `Uri`，只接受根级 `pack.json`、可选根级封面文件和安全的 `assets/...` 普通文件。
4. 目标进程写入 `packs/<packId>.tmp-import/`，生成 `arc_overrides/index.json` 和 `summary.json`。
5. 校验完成后原子替换 `packs/<packId>/`，并把 `<packId>` 放到 `active_pack_order` 顶部。

## 素材包与 Hook 路线

`difference` 是项目侧当前修改素材包，保存在仓库 `Arc_dark/packs/difference/`，包含 `pack.json`、`arc_overrides/index.json`、`img/` 和 `models/`。它用于把当前 348 个替换素材整理为标准资源包形式，不作为 Android UI 中的 selectable runtime pack。

`test_pkg` 是文件素材包。首次需要时，目标进程从模块 APK 内置素材复制生成完整目录。已验证文件数为 351，即 348 个素材文件加 `pack.json`、`index.json`、`summary.json`。

第三方素材包是部分覆盖包。每个包拥有自己的 `arc_overrides/index.json`，条目来自 ZIP 内允许目录下的 `assets/...` 文件。`pack.json` 可包含 `version`、`description`、`author`、`cover` 和 `Change`；`formatVersion` 只用于导入格式兼容判断，`version` 是 UI 显示用的自定义素材包版本。`Change` 把 ZIP 源文件夹映射到目标素材文件夹，未声明的默认文件夹按同名目录导入。激活时模块按 `active_pack_order` 合成最终 native map：同一个 `assetPath` 只采用最高优先级层；没有任何启用层命中的路径不进入 map，继续由游戏原始资源处理。

Hook 路线：

- 保留 Java `AssetManager.open(String)`、`open(String,int)`、`openFd(String)` 兜底。
- native 库 `libarcdarkhook.so` 使用 xHook hook Cocos native asset API：
  - `AAssetManager_open`
  - `AAsset_read`
  - `AAsset_getLength`
  - `AAsset_close`
- 不 hook `System.load` 或 `System.loadLibrary`。
- native hook 安装后执行 `xhook_refresh()`，并保留延迟刷新：initial、250ms、1000ms、3000ms、7000ms。

## 验证证据

最后一轮设备验证结果（2026-06-19）：

- `scripts/Build-Debug.ps1`、`:app:testDebugUnitTest`、`:app:assembleRelease` 均通过。
- debug APK 已安装到设备 `HA1W3A8E` 并启动 `dev.arc.assets/.MainActivity`。
- UI 显示 `Arc customize`、`当前修改素材 348`，运行路径仍为 `/storage/emulated/0/Android/media/moe.low.arc/ArcDark`。
- UI 不再出现 `bundled` 或 `<default>` 资源包项目。
- `test_pkg` 可启用，已启用项只显示上移 `↑` 与禁用 `×`，没有下移按钮。
- 点击 `×` 后 `active_pack_order` 变为空，顶层回到“原始资源”；随后已恢复设备状态为 `test_pkg` 已启用。
- 项目侧 `Arc_dark/packs/difference/arc_overrides/index.json` 与 APK 内 index 均为 348 条，且 `difference` 包内文件全部存在且大小匹配。

## 构建与回归

构建：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Build-Debug.ps1
```

核心回归检查：

- 安装 debug APK 后能解析并启动 `dev.arc.assets/.MainActivity`。
- UI 中显示注入开关、当前修改素材数量、`test_pkg`、刷新、复制诊断、Open Arcaea。
- `Open Arcaea` 能把 UI 当前配置应用到目标侧 `ArcDark/control.json`。
- `test_pkg` 启用时 native source sample 指向 `packs/test_pkg`。
- 进入谱面加载时至少出现 `img/track.png`、`img/note.png`、`models/tap_l.png` 的 native asset hit。
- 注入关闭时不安装 hook。
- 禁用最后一个启用素材包后，`active_pack_order` 应为空且目标回到原始资源。

Release 签名通过 Gradle properties、`local.properties` 或环境变量配置：`arcCustomizeStoreFile` / `ARC_CUSTOMIZE_STORE_FILE`、`arcCustomizeStorePassword` / `ARC_CUSTOMIZE_STORE_PASSWORD`、`arcCustomizeKeyAlias` / `ARC_CUSTOMIZE_KEY_ALIAS`、`arcCustomizeKeyPassword` / `ARC_CUSTOMIZE_KEY_PASSWORD`。未配置时 debug 构建不受影响。

## 后续开发注意事项

- 低版本 Android 适配暂缓，当前优先现代 Android 和现有测试设备。
- 不要恢复 `System.load` / `System.loadLibrary` hook；这条路线曾引入 native 库加载回归。
- 外部素材包扩展应复用当前 `packs/<packId>/` 结构和目标侧 `control.json`，避免跨包私有目录读取。
- `logs/` 和 `dist/` 是本地产物目录，默认忽略；需要证据时只整理精简摘要到 `docs/evidence/`。
