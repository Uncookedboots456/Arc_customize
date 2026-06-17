# MVP 验证摘要

验证日期：2026-06-17

## 结论

Arc Dark MVP 架构已完成。桌面 UI、注入开关、素材包切换、目标侧文件包、native 文件注入和关闭注入回归均已通过当前设备验证。

## 保留证据

- 最终 UI 截图：`docs/evidence/arc-dark-final-ui.png`

## 关键验证点

- UI 横屏下无明显重叠，显示注入开关、`<default>`、`test_pkg`、刷新、复制诊断、Open Arcaea。
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
