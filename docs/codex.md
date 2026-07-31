# Arc customize 项目交接摘要

## 当前状态

- 工作目录：仓库根目录
- 目标：把 Arcaea 素材改包迁移成 LSPosed 模块，不修改官方 `moe.low.arc` APK，不改签名，不改包名。
- 模块包名：`dev.arc.assets`
- 目标包名 / 推荐作用域：`moe.low.arc`
- 版本：`0.6 (7)`
- 覆盖素材数量：348
- 当前结论：MVP 架构已完成并经设备验证。

## 当前实现

- `dev.arc.assets` 有普通桌面入口 `MainActivity`。
- UI 标题为 `Arc customize`，提供注入开关、当前修改素材数量、当前覆盖栈状态、可滚动素材包列表、ZIP 导入、刷新、复制诊断、Open Arcaea。
- 素材包：
  - `difference`：项目侧 348 条格式 2 兼容索引，保存在 `Arc_dark/packs/difference/`，不含游戏图片，也不在 Android UI 中显示。
  - `pairumu_cat_dark`：模块 APK 内置可选索引包，UI 显示为 `派尔姆猫_dark`；Arcaea `6.16.0c` 配置包含 170 条官方别名、124 条透明图生成、2 条同路径直通。APK 不携带游戏图片或封面，UI 封面从已安装游戏读取并校验后显示。
  - 第三方 ZIP：UI 选择后通过 URI 授权交给目标进程解包到 `/sdcard/Android/media/moe.low.arc/ArcDark/packs/<packId>/`。
- 控制文件：
  - UI 本地配置：`/data/user/0/dev.arc.assets/files/control.json`
  - 目标侧生效配置：`/sdcard/Android/media/moe.low.arc/ArcDark/control.json`
- UI 点击 `Open Arcaea` 时通过 intent extras 把开关和 `active_pack_order` 传给目标进程；目标进程写入自己的 `ArcDark/control.json`，后续普通重启复用该状态。
- 覆盖栈从上到下查找素材，全部缺失或 `active_pack_order` 为空时不拦截，保留游戏原始素材；旧 `<default>/bundled` 行不再显示或作为默认启用层。`pairumu_cat_dark` 是保留内置 ID，第三方 ZIP 不可使用。

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

- 2026-07-31 在 Android 16 / API 36 测试机完成 0.6 冷缓存验证，目标为 split 安装的 Arcaea `6.16.0 (1209710)`。
- `pairumu_cat_dark` 已启用并进入覆盖栈；官方源可从游戏 `files/cb/active` 下载内容或 APK/split assets 取得，且必须通过大小与 SHA-256 校验。
- LSPosed 日志确认 `using built-in layer pairumu_cat_dark assets=296`、`provider initialized for [pairumu_cat_dark]`、`native install registered 296 assets`。
- native 日志确认实际命中 `img/track.png`、`img/note.png`、`img/note_hold.png`、`models/tap_l.png` 等读取请求。
- 冷启动生成 12 个官方源缓存和 33 个透明尺寸缓存；抽检官方文件哈希一致，1024 × 1024 透明 PNG 的 alpha 全为 0。
- UI 冷缓存读取官方 `img/default_jacket_256.jpg`，校验 47,667 字节及 SHA-256 后成功显示材质包封面，不再使用 `IMG` 占位。
- 点击已启用资源包的 `×` 可清空最后一个启用层，让目标回到原始资源。
- 关闭注入后，日志显示 `hooks not installed`，没有 native install 注册。

完整记录见 `docs/evidence/arc-customize-0.6-device-validation.md`。

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
3. 扩展素材包管理时沿用 `packs/<packId>/` 结构和 `active_pack_order` 覆盖栈。
4. 低版本 Android 权限适配后续再单独评估。
5. 不要回到 Java `System.load*` hook 路线。
6. release signing 从 Gradle properties、`local.properties` 或环境变量读取，不提交 keystore 或密码。
