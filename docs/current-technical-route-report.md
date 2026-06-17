# Arc Dark MVP 当前技术路线

## 结论摘要

Arc Dark 的 MVP 架构已经完成并通过设备验证。当前路线保持官方 Arcaea 包 `moe.low.arc` 不变，通过 LSPosed 注入目标进程，在运行时替换 348 个素材。

当前已验证能力：

- 桌面入口和控制台式 UI 可用。
- 注入开关可控制是否安装 hook。
- 素材包列表为可滚动覆盖栈，包含 `<default>`、`test_pkg` 和导入的第三方 ZIP 包。
- `<default>` 使用模块 APK 内置素材。
- `test_pkg` 会复制为目标进程可读的真实文件包。
- 第三方 ZIP 包由 UI 选择后通过 URI 授权交给目标进程解包到 `packs/<packId>/`。
- 生效顺序由 `active_pack_order` 控制，从上到下查找；全部缺失时保留游戏原始素材。
- native map 可以指向 `packs/test_pkg` 下的文件。
- 进入谱面加载后已确认 `ArcDarkNative: asset hit` 指向 `packs/test_pkg`。
- 关闭注入后不会注册 native map，也不会安装 Java/native asset hook。

## 当前工程状态

- 模块包名：`dev.arc.assets`
- 目标包名 / LSPosed 作用域：`moe.low.arc`
- 版本：`0.2.0 (6)`
- native ABI：`arm64-v8a`, `armeabi-v7a`
- 覆盖素材数量：348
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
      arc_overrides/index.json
      arc_overrides/summary.json
      assets/...
```

控制流：

1. UI 保存本地配置到 `dev.arc.assets/files/control.json`。
2. 用户点击 `Open Arcaea`。
3. UI 通过启动 intent extras 传递 `injection_enabled` 和 `active_pack_id`。
4. `ArcDarkModule` 在目标 Activity `onCreate` 前读取 extras。
5. 目标进程写入 `/sdcard/Android/media/moe.low.arc/ArcDark/control.json`。
6. 后续普通重启 Arcaea 时，模块直接读取目标侧 `control.json`。

这个设计避免了 `dev.arc.assets` 私有目录跨包读取失败，也不需要 `READ_EXTERNAL_STORAGE`、`WRITE_EXTERNAL_STORAGE` 或 `MANAGE_EXTERNAL_STORAGE`。

第三方 ZIP 导入流：

1. UI 使用 `ACTION_OPEN_DOCUMENT` 选择 ZIP。
2. UI 校验根级 `pack.json`，并把 ZIP `Uri` 通过启动 intent、`ClipData` 和 `FLAG_GRANT_READ_URI_PERMISSION` 传给 Arcaea。
3. 目标进程在安装 hook 前读取该 `Uri`，只接受根级 `pack.json` 和 `assets/...` 普通文件。
4. 目标进程写入 `packs/<packId>.tmp-import/`，生成 `arc_overrides/index.json` 和 `summary.json`。
5. 校验完成后原子替换 `packs/<packId>/`，并把 `<packId>` 放到 `active_pack_order` 顶部。

## 素材包与 Hook 路线

`<default>` 是模块 APK 内置素材包。目标进程启动时直接从模块 APK 读取索引和素材，必要时 materialize 到目标进程可用路径。

`test_pkg` 是文件素材包。首次需要时，目标进程从模块 APK 内置素材复制生成完整目录。已验证文件数为 351，即 348 个素材文件加 `pack.json`、`index.json`、`summary.json`。

第三方素材包是部分覆盖包。每个包拥有自己的 `arc_overrides/index.json`，条目来自 ZIP 内 `assets/...` 文件。激活时模块按 `active_pack_order` 合成最终 native map：同一个 `assetPath` 只采用最高优先级层；没有任何启用层命中的路径不进入 map，继续由游戏原始资源处理。`<default>` 只是可选层，不作为隐式兜底。

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

最后一轮设备验证结果（2026-06-17）：

- `test_pkg` 已生成在 `/sdcard/Android/media/moe.low.arc/ArcDark/packs/test_pkg/`。
- `find .../test_pkg -type f | wc -l` 返回 `351`。
- 选择 `test_pkg` 并启动 Arcaea 后，日志出现：
  - `ArcDark: using file pack /storage/emulated/0/Android/media/moe.low.arc/ArcDark/packs/test_pkg`
  - `ArcDark: native source sample ... -> /storage/emulated/0/Android/media/moe.low.arc/ArcDark/packs/test_pkg/...`
  - `ArcDark: native install registered 348 assets`
- 从标题页进入主菜单，点击开始游戏并进入当前曲目加载后，日志确认真实素材命中：
  - `ArcDarkNative: asset hit #1: img/track.png -> /storage/emulated/0/Android/media/moe.low.arc/ArcDark/packs/test_pkg/img/track.png`
  - `ArcDarkNative: asset hit #5: img/note.png -> /storage/emulated/0/Android/media/moe.low.arc/ArcDark/packs/test_pkg/img/note.png`
  - `ArcDarkNative: asset hit #11: models/tap_l.png -> /storage/emulated/0/Android/media/moe.low.arc/ArcDark/packs/test_pkg/models/tap_l.png`
- 关闭注入并启动 Arcaea 后，日志出现：
  - `ArcDark: injection disabled by control; hooks not installed`
  - 没有 native install 注册日志。
- 最终 UI 截图保存在 `docs/evidence/arc-dark-final-ui.png`。

## 构建与回归

构建：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Build-Debug.ps1
```

核心回归检查：

- 安装 debug APK 后能解析并启动 `dev.arc.assets/.MainActivity`。
- UI 中显示注入开关、`<default>`、`test_pkg`、刷新、复制诊断、Open Arcaea。
- `Open Arcaea` 能把 UI 当前配置应用到目标侧 `ArcDark/control.json`。
- `<default>` 启用时注册 348 个素材。
- `test_pkg` 启用时 native source sample 指向 `packs/test_pkg`。
- 进入谱面加载时至少出现 `img/track.png`、`img/note.png`、`models/tap_l.png` 的 native asset hit。
- 注入关闭时不安装 hook。

## 后续开发注意事项

- 低版本 Android 适配暂缓，当前优先现代 Android 和现有测试设备。
- 不要恢复 `System.load` / `System.loadLibrary` hook；这条路线曾引入 native 库加载回归。
- 外部素材包扩展应复用当前 `packs/<packId>/` 结构和目标侧 `control.json`，避免跨包私有目录读取。
- `logs/` 和 `dist/` 是本地产物目录，默认忽略；需要证据时只整理精简摘要到 `docs/evidence/`。
