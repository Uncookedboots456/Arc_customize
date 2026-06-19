# Stack Order Material A/B Verification

Date: 2026-06-18

Device: `HA1W3A8E`

Target activity: `moe.low.arc/low.moe.AppActivity`

## Test Packs

- `stack_white_a`: white material A
- `stack_black_b`: black material B
- Both packs override:
  - `assets/img/note.png`
  - `assets/img/track.png`
  - `assets/models/tap_l.png`

Local generated packs:

- `dist/stack-order-test/stack_white_a`
- `dist/stack-order-test/stack_black_b`

Device runtime packs:

- `/sdcard/Android/media/moe.low.arc/ArcDark/packs/stack_white_a`
- `/sdcard/Android/media/moe.low.arc/ArcDark/packs/stack_black_b`

## White First

Control order:

```json
["stack_white_a", "stack_black_b"]
```

Evidence log: `logs/stack-order-white-first.log`

Key lines:

```text
ArcDark: control read from target media injection=true, activeOrder=[stack_white_a, stack_black_b]
ArcDark: using imported layer /storage/emulated/0/Android/media/moe.low.arc/ArcDark/packs/stack_white_a assets=3
ArcDark: using imported layer /storage/emulated/0/Android/media/moe.low.arc/ArcDark/packs/stack_black_b assets=3
ArcDark: native source sample assets/img/note.png -> /storage/emulated/0/Android/media/moe.low.arc/ArcDark/packs/stack_white_a/assets/img/note.png
ArcDark: native install registered 3 assets
ArcDark: installed Java hooks for 3 asset overrides for moe.low.arc, order=[stack_white_a, stack_black_b], native=true
```

Result: top layer selected `stack_white_a`.

## Black First

Control order:

```json
["stack_black_b", "stack_white_a"]
```

Evidence log: `logs/stack-order-black-first.log`

Key lines:

```text
ArcDark: control read from target media injection=true, activeOrder=[stack_black_b, stack_white_a]
ArcDark: using imported layer /storage/emulated/0/Android/media/moe.low.arc/ArcDark/packs/stack_black_b assets=3
ArcDark: using imported layer /storage/emulated/0/Android/media/moe.low.arc/ArcDark/packs/stack_white_a assets=3
ArcDark: native source sample assets/img/note.png -> /storage/emulated/0/Android/media/moe.low.arc/ArcDark/packs/stack_black_b/assets/img/note.png
ArcDark: native install registered 3 assets
ArcDark: installed Java hooks for 3 asset overrides for moe.low.arc, order=[stack_black_b, stack_white_a], native=true
```

Result: top layer selected `stack_black_b`.

## Negative Checks

Neither log contains:

- `unable to use imported layer`
- `checksum mismatch`
- `native install failed`
- `hooks not installed`

## Final State

The original target control file was restored from:

```text
dist/stack-order-test/control-before.json
```

Restored device control:

```json
{
  "injection_enabled": true,
  "active_pack_id": "test_pkg",
  "active_pack_order": [
    "test_pkg"
  ]
}
```

The temporary `stack_white_a` and `stack_black_b` runtime pack directories were removed from the device after log capture. Local generated copies remain under `dist/stack-order-test/` for inspection or repeat testing.

## ADB Rerun

The same order test was rerun through adb after the initial verification.

Additional logs:

- `logs/stack-order-white-first-adb-rerun.log`
- `logs/stack-order-black-first-adb-rerun.log`

Rerun result:

- `["stack_white_a", "stack_black_b"]` selected `/packs/stack_white_a/assets/img/note.png`.
- `["stack_black_b", "stack_white_a"]` selected `/packs/stack_black_b/assets/img/note.png`.
- Both runs registered `3 assets` and did not contain failure patterns.
- The original control file was restored again, and the temporary runtime pack directories were removed again.

Conclusion: the material override stack order is correct. Reversing `active_pack_order` reverses the selected top-layer source for the same asset path.
