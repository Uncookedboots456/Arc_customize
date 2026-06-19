# 设备回归验证记录

验证日期：2026-06-18

## 环境

- 设备序列号：`HA1W3A8E`
- 设备型号：`OPD2413`
- 模块包：`dev.arc.assets`
- 模块版本：`0.2.0 (6)`
- 目标包：`moe.low.arc`
- 目标版本：`6.14.12`
- 截图证据：`docs/evidence/arc-dark-device-ui-2026-06-18.png`

## 验证结论

- 控制台基础功能可用：注入开关、覆盖栈显示、素材包上下移动、Open Arcaea 均正常。
- 目标侧控制文件可由 UI 通过 Open Arcaea 写入，最终状态已恢复为：

```json
{
  "injection_enabled": true,
  "active_pack_id": "sample_pack",
  "active_pack_order": [
    "sample_pack",
    "test_pkg"
  ]
}
```

- `sample_pack > test_pkg` 顺序生效：runtime 先加载 `sample_pack`，再加载 `test_pkg`，合成后仍注册 348 个素材。
- 顺序反转也生效：通过 UI 将顺序改成 `test_pkg > sample_pack` 后，runtime 先加载 `test_pkg`，native source sample 指向 `test_pkg`。
- 注入关闭路径正常：`injection=false` 时日志显示 `hooks not installed`，未执行 native install。

## 关键日志摘录

`sample_pack > test_pkg`：

```text
ArcDark: applied launch control injection=true, activeOrder=[sample_pack, test_pkg]
ArcDark: using imported layer /storage/emulated/0/Android/media/moe.low.arc/ArcDark/packs/sample_pack assets=3
ArcDark: using test layer /storage/emulated/0/Android/media/moe.low.arc/ArcDark/packs/test_pkg
ArcDark: provider initialized for [sample_pack, test_pkg]
ArcDark: native source sample assets/img/note.png -> /storage/emulated/0/Android/media/moe.low.arc/ArcDark/packs/sample_pack/assets/img/note.png
ArcDark: native install registered 348 assets
ArcDark: installed Java hooks for 348 asset overrides for moe.low.arc, order=[sample_pack, test_pkg], native=true
```

`test_pkg > sample_pack`：

```text
ArcDark: applied launch control injection=true, activeOrder=[test_pkg, sample_pack]
ArcDark: using test layer /storage/emulated/0/Android/media/moe.low.arc/ArcDark/packs/test_pkg
ArcDark: using imported layer /storage/emulated/0/Android/media/moe.low.arc/ArcDark/packs/sample_pack assets=3
ArcDark: provider initialized for [test_pkg, sample_pack]
ArcDark: native source sample assets/img/bg/1080/megarex_conflict.jpg -> /storage/emulated/0/Android/media/moe.low.arc/ArcDark/packs/test_pkg/img/bg/1080/megarex_conflict.jpg
ArcDark: native install registered 348 assets
ArcDark: installed Java hooks for 348 asset overrides for moe.low.arc, order=[test_pkg, sample_pack], native=true
```

注入关闭：

```text
ArcDark: applied launch control injection=false, activeOrder=[sample_pack, test_pkg]
ArcDark: control injection=false, activeOrder=[sample_pack, test_pkg]
ArcDark: injection disabled by control; hooks not installed
```

## 未覆盖项

- 本轮未进入谱面加载阶段验证具体 `ArcDarkNative: asset hit`。本次重点是覆盖栈顺序、控制台基础功能、runtime layer 合成和注入开关回归。
