# Arc customize 0.6 device validation

Validation date: 2026-07-31

## Build under test

- Module package: `dev.arc.assets`
- Version: `0.6 (7)`
- APK size: 447,220 bytes
- APK SHA-256: `dfe6c08d182aa991ed7dc64fd25b75f2efca89311251c46380765cbb8b493f61`
- Signer certificate SHA-256: `e1a7f883f8db7eff3e6a37fce84ed41092094de513d69919976dd6a1e0574825`
- `test` and `assembleDebug`: passed
- Packaged game images: 0

## Device and target

- Device model: OPD2413
- Android: 16 (API 36)
- Target package: `moe.low.arc`
- Installed target version: `6.16.0 (1209710)`, split APK installation
- Index source baseline: official Arcaea `6.16.0c`, APK SHA-256 `4a1fb6011369c7289c4c6cd33b5f3fec47cbef4c86a0ba0b9f3a7a6896d74512`

## Cold-cache validation

The target-only cache directory `/data/user/0/moe.low.arc/cache/arc_index_assets` was removed before launch. On the next cold start:

- `pairumu_cat_dark` passed preflight and loaded 296 indexed overrides.
- Native installation registered 296 assets and 592 lookup keys.
- The device generated 45 cache files: 12 verified official sources and 33 transparent PNG dimensions.
- Official sample `official-e639103922c29b25905399f887318ef079d06c0ca95d8cf0f9cf73f84e39402d` reproduced the expected SHA-256 exactly.
- Generated `transparent-1024x1024.png` decoded as RGBA PNG, size 1024 × 1024, alpha extrema `(0, 0)`, with an empty alpha bounding box.
- After deleting the module's cover cache, the UI loaded `img/default_jacket_256.jpg` from the installed game's split APK, verified 47,667 bytes and SHA-256 `f8c48cce52c51ed2f0017cd8ef5972933c34c17a274d8722073ec62df9f132fc`, and displayed it instead of the `IMG` fallback.

Official sources were resolved from the target's downloaded content at `files/cb/active` or its installed APK/splits. Every source was accepted only after matching the size and SHA-256 stored in the index.

## Runtime evidence

LSPosed/runtime logs recorded:

```text
ArcDark: using built-in layer pairumu_cat_dark assets=296
ArcDark: provider initialized for [pairumu_cat_dark]
ArcDark: native install registered 296 assets
ArcDarkNative: nativeInstall mapped 296 assets (592 lookup keys), refresh=0
```

The running game then produced native override hits for, among others:

```text
img/track.png
img/track_extralane_light.png
img/note.png
img/note_hold.png
img/note_hold_hi.png
models/tap_l.png
```

This confirms the material layer was not merely enabled in the UI: indexed files were materialized, registered with the native hook, and returned to actual game asset reads.
