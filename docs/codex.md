# Arc_dark 项目交接摘要

## 当前状态

- 工作目录：`C:\Users\comma\Documents\Arc_dark`
- 目标：把 Arcaea 素材改包迁移成 LSPosed 模块，不修改官方 `moe.low.arc` APK，不改签名，不改包名。
- 模块包名：`dev.arc.assets`
- 目标包名 / 推荐作用域：`moe.low.arc`
- 版本：`0.1.5 (5)`
- 覆盖素材数量：348
- 当前结论：MVP 架构已完成并经设备验证。

## 当前实现

- `dev.arc.assets` 有普通桌面入口 `MainActivity`。
- UI 提供注入开关、当前包状态、素材包列表、刷新、复制诊断、Open Arcaea。
- 素材包：
  - `<default>`：模块 APK 内置素材。
  - `test_pkg`：目标进程生成到 `/sdcard/Android/media/moe.low.arc/ArcDark/packs/test_pkg/` 的真实文件包。
- 控制文件：
  - UI 本地配置：`/data/user/0/dev.arc.assets/files/control.json`
  - 目标侧生效配置：`/sdcard/Android/media/moe.low.arc/ArcDark/control.json`
- UI 点击 `Open Arcaea` 时通过 intent extras 把开关和包 ID 传给目标进程；目标进程写入自己的 `ArcDark/control.json`，后续普通重启复用该状态。

## Hook 路线

- 模块只在 `moe.low.arc` 进程工作。
- 使用 `IXposedHookZygoteInit` 获取 `modulePath`。
- 不调用 `targetContext.createPackageContext("dev.arc.assets", ...)`，避免 Android 包可见性拦截。
- `AssetOverrideIndex` 从模块 APK 的 `assets/arc_overrides/index.json` 读取。
- 保留 Java `AssetManager.open/openFd` hook 作为兜底。
- native 库 `libarcdarkhook.so` 通过 xHook hook Cocos native asset API。
- 不 hook `System.load` 或 `System.loadLibrary`。
- native hook 安装后执行 `xhook_refresh()`，并保留延迟刷新。

## 已验证结果

- `test_pkg` 已生成，文件数 351：348 个素材文件加 `pack.json/index.json/summary.json`。
- 选择 `test_pkg` 后，native source sample 指向：
  `/storage/emulated/0/Android/media/moe.low.arc/ArcDark/packs/test_pkg/...`
- `test_pkg` 和 `<default>` 均能注册 348 个素材。
- 进入谱面加载后已确认 native asset hit，样例包括：
  - `img/track.png -> /storage/emulated/0/Android/media/moe.low.arc/ArcDark/packs/test_pkg/img/track.png`
  - `img/note.png -> /storage/emulated/0/Android/media/moe.low.arc/ArcDark/packs/test_pkg/img/note.png`
  - `models/tap_l.png -> /storage/emulated/0/Android/media/moe.low.arc/ArcDark/packs/test_pkg/models/tap_l.png`
- 关闭注入后，日志显示 `hooks not installed`，没有 native install 注册。
- 最终 UI 截图：`docs/evidence/arc-dark-final-ui.png`

## 构建

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\Build-Debug.ps1
```

构建产物在：

```text
app/build/outputs/apk/debug/app-debug.apk
```

`dist/` 不再保存固定 APK，后续需要发布产物时再单独生成。

## 后续建议

1. 在当前 MVP 上提交一次功能收尾提交。
2. 做 release/debug 日志分级，release 减少 LSPosed 日志噪音。
3. 扩展素材包管理时沿用 `packs/<packId>/` 结构。
4. 低版本 Android 权限适配后续再单独评估。
5. 不要回到 Java `System.load*` hook 路线。
