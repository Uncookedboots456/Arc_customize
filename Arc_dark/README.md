# Arc_dark resource packs

This project directory stores Arc customize material packs in the same shape used by runtime imports:

- `packs/<pack_id>/pack.json`
- `packs/<pack_id>/arc_overrides/index.json`
- optional cover or asset files referenced by `pack.json` and the override index

`packs/difference` is the current material difference pack generated from the module's bundled override index. Android runtime storage is unchanged; this directory is a project-side source layout.
