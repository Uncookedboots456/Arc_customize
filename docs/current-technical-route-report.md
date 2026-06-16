# Arc Dark 当前技术路线与失败复盘

## 结论摘要

当前模块未正常生效，至少存在一个已确认的配置错误和若干运行时风险。

最明确的问题是 **LSPosed 推荐作用域声明方式错误**。当前 APK 内存在 `assets/xposed_scope`，内容为 `moe.low.arc`，但 LSPosed 官方 Wiki 描述的推荐作用域机制是 `AndroidManifest.xml` 中的 `meta-data android:name="xposedscope"`，可通过 `android:value` 或 `android:resource` 声明目标包名。当前 manifest 没有 `xposedscope`，因此“推荐作用域看不见”是符合现状的。

这也会影响材质替换测试：如果 LSPosed 没有手动勾选 `moe.low.arc`，模块不会注入 Arcaea 进程，Java hook 和 native hook 都不会运行，表现就是修改完全不生效。

参考：

- LSPosed Module Scope Wiki: https://github.com/LSPosed/LSPosed/wiki/Module-Scope
- LSPosed How to use it: https://github.com/LSPosed/LSPosed/wiki/How-to-use-it

## 当前工程状态

模块工程位置：

`C:\Users\comma\Documents\Arc_dark`

当前构建产物：

`C:\Users\comma\Documents\Arc_dark\app\build\outputs\apk\debug\app-debug.apk`

当前 APK 静态状态：

- APK 包名：`dev.arc.assets`
- LSPosed legacy 入口：`assets/xposed_init = dev.arc.assets.ArcDarkModule`
- 当前 scope 文件：`assets/xposed_scope = moe.low.arc`
- manifest 中 Xposed legacy metadata：
  - `xposedmodule=true`
  - `xposeddescription=@string/xposed_description`
  - `xposedminversion=93`
- manifest 中缺失：`xposedscope`
- 内置素材覆盖项：348 个
- native 库：
  - `lib/arm64-v8a/libarcdarkhook.so`
  - `lib/armeabi-v7a/libarcdarkhook.so`
  - 两个 ABI 都包含 `libc++_shared.so`
- `extractNativeLibs=true`，因此模块 native lib 会被解包，`System.load()` 有可用路径。

## 失败经过

### 1. 改包分析阶段

原始输入：

- 官方原包：`arcaea_6.14.12c.apk`
- 改包：`fixed.apk`

发现：

- 官方原包包名是 `moe.low.arc`，版本 `6.14.12c`。
- `fixed.apk` 包名是 `moe.low.ard`，版本 `6.14.11c`。
- fixed 不是同版本同包名的纯素材包，而是跨版本、改包名、重签名产物。
- 两包差异集中在 `assets/`，但也存在 `AndroidManifest.xml`、`classes2.dex`、`resources.arsc`、`lib/*.so` 差异。

第一版计划选择只迁移素材，不复制改包名、dex、resources、so 差异。

### 2. Java-only 版本

第一版模块做了这些事：

- 保持官方目标包 `moe.low.arc`
- 内置 348 个素材覆盖项
- hook Java `AssetManager.open(String)`、`AssetManager.open(String, int)`、`AssetManager.openFd(String)`
- 尝试 `AssetManager.addAssetPath(modulePath)` 让模块 assets 被目标进程看到

失败原因分析：

- Arcaea 使用 Cocos2d-x。
- 静态分析 `libcocos2dcpp.so` 发现它导入 `AAssetManager_open`、`AAsset_read`、`AAsset_getLength`、`AAsset_close`。
- Cocos native 层可以直接通过 Android native asset API 读取 APK assets，绕过 Java `AssetManager.open` hook。
- `addAssetPath()` 是追加资源路径，不是覆盖原 APK assets；同路径素材通常仍优先命中原包。

因此 Java-only 方案不足以替换 Cocos native 读取的材质。

### 3. Native hook MVP 版本

第二版模块新增了 native hook：

- 新增 `NativeBridge`
- 新增 `libarcdarkhook.so`
- 通过 xHook 注册 PLT hook
- hook 目标：
  - `AAssetManager_open`
  - `AAsset_read`
  - `AAsset_getLength`
  - `AAsset_close`
- 启动时把 348 个内置素材落盘到目标 app cache
- 命中路径时返回 fake `AAsset*`，后续 `read/getLength/close` 识别 fake pointer 并读取落盘素材
- 未命中路径回退原始 native API

构建验证通过：

- `:app:assembleDebug` 成功
- APK 包名仍为 `dev.arc.assets`
- APK 内包含两套 ABI native lib
- APK 签名 v1/v2 验证通过

设备测试结果：

- 模块仍无法正常运行
- 修改不生效
- 推荐作用域看不见

其中“推荐作用域看不见”已有明确根因：使用了错误/非官方的推荐作用域声明位置。

## 成因分析

### P0：推荐作用域声明错误

当前工程使用：

```text
app/src/main/assets/xposed_scope
```

但 LSPosed 官方推荐作用域声明方式是 manifest metadata：

```xml
<meta-data
    android:name="xposedscope"
    android:value="moe.low.arc" />
```

或者：

```xml
<meta-data
    android:name="xposedscope"
    android:resource="@array/scope" />
```

当前 APK 没有 `xposedscope`，因此 LSPosed Manager 不显示推荐作用域是预期结果。

影响：

- 用户必须手动选择 `moe.low.arc`。
- 如果没有手动选择，模块不会进入 Arcaea 进程。
- 模块不进入进程时，所有 hook 都不会运行，材质替换必然不生效。

### P1：当前测试无法证明 native hook 已进入 Arcaea 进程

报告中没有看到设备侧 LSPosed/Xposed 日志，例如：

- `ArcDark: loaded native hook library`
- `ArcDark: native install registered 348 assets`
- `ArcDark: native refresh(...) result=...`
- `ArcDarkNative: asset hit #...`

如果这些日志不存在，优先判断不是 native hook 逻辑错，而是模块没有被 LSPosed 注入到 `moe.low.arc`。

### P1：legacy Xposed metadata 与 LSPosed scope metadata 混用

当前模块入口仍使用 legacy Xposed 机制：

- `assets/xposed_init`
- manifest `xposedmodule`
- 实现 `IXposedHookLoadPackage`

这条路线仍可用，但推荐作用域不能只依赖 `assets/xposed_scope`。需要补 manifest `xposedscope`。

如果后续迁移到现代 libxposed API，则入口和 scope 文件位置会变成另一套机制，例如 `META-INF/xposed/...`，不能和当前 legacy 写法混用。

### P2：native hook 仍有运行时不确定性

即使 scope 修好并手动/自动注入成功，native hook 仍有这些风险：

- `xhook_register` 当前只匹配 `.*libcocos2dcpp\.so$`，如果设备上的 linker 路径命名不符合该正则，hook 可能没有打到目标库。
- 当前只 hook `System.loadLibrary("cocos2dcpp")` 后刷新。如果游戏使用 `System.load()`、其他加载路径，或者 `libcocos2dcpp.so` 在模块初始化前已加载，刷新时机可能不稳定。
- 当前 fake `AAsset*` 方案只覆盖了静态分析发现的四个函数。原包 `libcocos2dcpp.so` 符号表目前只导入 `AAssetManager_open/read/getLength/close`，所以 MVP 设计是合理的；但如果不同版本或设备路径调用更多 `AAsset_*` API，需要补 hook。
- 当前没有 hook `AAssetManager_openDir`，因此 60 个新增素材不会出现在目录枚举里。已有同路径替换的 288 个素材理论上不受影响。
- `fixed.apk` 是 `6.14.11c`，官方包是 `6.14.12c`。跨版本素材不保证一定会被当前官方版本请求。

### P2：路径命中需要设备日志确认

当前索引同时注册：

- `assets/img/...`
- `img/...`

这覆盖了主要路径形态。但如果 Cocos 请求路径存在其他前缀、大小写、资源包内部虚拟路径，当前匹配不会命中。需要通过设备日志里 `asset hit` 或额外 path-probe 日志确认真实请求路径。

## 当前技术路线评价

当前方向仍然合理，但需要先修正 LSPosed 基础元数据，否则所有运行时分析都不可靠。

路线优点：

- 不修改官方 APK。
- 保留官方包名和签名链。
- 素材只在运行时替换。
- Java hook 和 native hook 分层，覆盖了 Cocos 主要 asset 读取方式。

路线问题：

- 推荐作用域配置错误，导致用户侧无法看到推荐范围。
- 目前没有设备日志闭环，无法确认模块是否注入、native 库是否加载、xHook 是否命中。
- native hook 只覆盖 assets API，不覆盖文件系统、OBB、目录枚举和 fixed 中的非素材逻辑差异。
- 素材来源跨版本，可能导致“测试的那个画面不请求被覆盖路径”。

## 建议的下一步

### 立即修复

1. 在 manifest 中新增：

```xml
<meta-data
    android:name="xposedscope"
    android:value="moe.low.arc" />
```

2. 保留 `assets/xposed_scope` 也可以，但不要把它当作 LSPosed 推荐作用域的可靠来源。

3. 重建 APK 后确认：

```powershell
aapt dump xmltree app-debug.apk AndroidManifest.xml
```

必须能看到 `xposedscope`。

### 下一轮设备测试必须收集

启动 Arcaea 后抓取 LSPosed/Xposed 日志，确认是否出现：

```text
ArcDark: installed 348 asset overrides for moe.low.arc
ArcDark: loaded native hook library ...
ArcDark: native install registered 348 assets
ArcDark: native refresh(initial) result=...
ArcDarkNative: asset hit #...
```

判断方式：

- 没有 `ArcDark:`：模块未注入，优先查 scope/启用状态。
- 有 `ArcDark:` 但没有 native install：native 库加载或 materialize 失败。
- 有 native install 但没有 `asset hit`：hook 未命中目标 so，或路径不匹配。
- 有 `asset hit` 但画面不变：命中素材不是当前画面使用的材质，或渲染层使用了其他缓存/资源路径。

### 后续增强

- 增加 native path-probe：在未命中的 `AAssetManager_open` 前若干次调用中记录请求路径，但要限量避免刷日志。
- 补 hook `AAssetManager_openDir`，解决新增素材目录枚举不可见问题。
- 增加版本锁定提示：当前素材基于 `fixed.apk` 的 `6.14.11c`，目标官方包是 `6.14.12c`。
- 如果 LSPosed 版本较新且 legacy API 兼容性不稳定，再考虑迁移现代 libxposed API，而不是继续混用 legacy 入口和现代 scope 文件。

