# MVP 验证摘要

验证日期：2026-06-19

## 结论

Arc customize MVP 架构已完成。桌面 UI、注入开关、素材包切换、目标侧文件包、native 文件注入、关闭注入和清空已启用素材包回归均已通过当前设备验证。

## 保留证据

- 最终 UI 截图：`docs/evidence/arc-dark-final-ui.png`
- 2026-06-18 设备回归记录：`docs/evidence/device-regression-2026-06-18.md`
- 2026-06-18 设备 UI 截图：`docs/evidence/arc-dark-device-ui-2026-06-18.png`
- 2026-06-19 项目侧当前修改素材包：`Arc_dark/packs/difference/`

## 关键验证点

- UI 横屏下无明显重叠，显示 `Arc customize`、注入开关、当前修改素材数量、`test_pkg`、刷新、复制诊断、Open Arcaea。
- UI 不再显示 `bundled` 或 `<default>` 资源包项目。
- `test_pkg` 生成位置：`/sdcard/Android/media/moe.low.arc/ArcDark/packs/test_pkg/`
- `test_pkg` 文件数：351。
- `test_pkg` 注入日志包含：
  - `ArcDark: using file pack /storage/emulated/0/Android/media/moe.low.arc/ArcDark/packs/test_pkg`
  - `ArcDark: native source sample ... -> /storage/emulated/0/Android/media/moe.low.arc/ArcDark/packs/test_pkg/...`
  - `ArcDark: native install registered 348 assets`
- 进入谱面加载阶段后，native asset hit 已跑通，命中路径包含：
  - `ArcDarkNative: asset hit #1: img/track.png -> /storage/emulated/0/Android/media/moe.low.arc/ArcDark/packs/test_pkg/img/track.png`
  - `ArcDarkNative: asset hit #5: img/note.png -> /storage/emulated/0/Android/media/moe.low.arc/ArcDark/packs/test_pkg/img/note.png`
  - `ArcDarkNative: asset hit #11: models/tap_l.png -> /storage/emulated/0/Android/media/moe.low.arc/ArcDark/packs/test_pkg/models/tap_l.png`
- 关闭注入日志包含：
  - `ArcDark: injection disabled by control; hooks not installed`
- 测试结束后设备状态为启用注入并选择 `test_pkg`。
- 2026-06-19 验证 `test_pkg` 可点击 `×` 禁用，禁用后 `active_pack_order=[]` 且顶层回到原始资源；测试结束后已恢复 `active_pack_order=["test_pkg"]`。
- `Arc_dark/packs/difference` 已整理成标准资源包形式，348 个 index 条目文件均存在且大小匹配。

## 2026-06-18 回归补充

- 测试设备：`HA1W3A8E` / `OPD2413`。
- 模块已覆盖安装到 `0.2.0 (6)`，目标包为 `moe.low.arc 6.14.12`。
- UI 基础状态正常：注入开关、目标安装状态、348 个 bundled override、素材包列表、上下移动和 Open Arcaea 均可用。
- 覆盖栈顺序已验证：
  - `sample_pack > test_pkg` 时，runtime 先加载 `sample_pack`，native source sample 指向 `packs/sample_pack/assets/img/note.png`，最终注册 348 个素材。
  - `test_pkg > sample_pack` 时，runtime 先加载 `test_pkg`，native source sample 指向 `packs/test_pkg/...`，最终注册 348 个素材。
- 注入关闭路径已验证：`injection=false` 时日志显示 `injection disabled by control; hooks not installed`。
- 测试结束后已恢复目标侧状态为启用注入，覆盖栈为 `sample_pack > test_pkg`。
