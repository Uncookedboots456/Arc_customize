# Arc customize verification - 2026-06-19

## Scope

- Remove selectable `bundled` / `<default>` runtime pack.
- Show `当前修改素材 / Difference` as the current override count.
- Keep Android runtime root unchanged at `/storage/emulated/0/Android/media/moe.low.arc/ArcDark`.
- Add project-side standard pack layout under `Arc_dark/packs/difference`.
- Add optional release signing configuration.

## Results

- `:app:testDebugUnitTest` passed.
- `scripts/Build-Debug.ps1` passed.
- `:app:assembleRelease` passed without signing variables configured.
- `git diff --check` passed.
- Installed `app/build/outputs/apk/debug/app-debug.apk` on device `HA1W3A8E`.
- UI showed `Arc customize`, `Arcaea 材质修改`, `当前修改素材 348`, and runtime path `/storage/emulated/0/Android/media/moe.low.arc/ArcDark`.
- UI did not show `bundled` or `<default>` as a material pack row.
- Enabling `test_pkg` moved it into the enabled section with only `↑` and `×` controls.
- Clicking `×` disabled `test_pkg`; `active_pack_order` became empty and the top layer returned to original assets.
- Final device state was restored to:

```json
{
  "injection_enabled": true,
  "active_pack_id": "test_pkg",
  "active_pack_order": ["test_pkg"]
}
```

## Difference Pack

`Arc_dark/packs/difference` contains:

- `pack.json`
- `arc_overrides/index.json`
- `img/...`
- `models/...`

The difference index contains 348 entries. Every referenced file exists under the pack directory with the expected file size.
