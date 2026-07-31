#!/usr/bin/env python3
"""Build an image-free override index from a verified asset mapping report."""

from __future__ import annotations

import argparse
import json
import struct
from collections import Counter
from pathlib import Path


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mapping", required=True, type=Path)
    parser.add_argument("--legacy-pack", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--summary-output", required=True, type=Path)
    parser.add_argument("--pack-id", required=True)
    parser.add_argument("--pack-name", required=True)
    parser.add_argument("--game-version", required=True)
    parser.add_argument("--game-apk-sha256", required=True)
    return parser.parse_args()


def png_dimensions(path: Path) -> tuple[int, int]:
    with path.open("rb") as stream:
        header = stream.read(24)
    if len(header) != 24 or not header.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError(f"Transparent source is not a PNG: {path}")
    return struct.unpack(">II", header[16:24])


def main() -> None:
    args = parse_args()
    report = json.loads(args.mapping.read_text(encoding="utf-8"))
    entries: list[dict[str, object]] = []
    counts: Counter[str] = Counter()

    for mapping in report["files"]:
        target = mapping["target_asset_path"]
        classification = mapping["content_classification"]
        if classification == "exact_current_official_content":
            source = mapping["canonical_source_asset_path"]
            if not source:
                raise ValueError(f"Missing canonical official source for {target}")
            entry = {
                "assetPath": target,
                "mode": "alias",
                "sourceAssetPath": source,
                "sourceSize": mapping["old_size"],
                "sourceSha256": mapping["old_sha256"],
            }
        elif classification == "fully_transparent_placeholder":
            old_file = args.legacy_pack / mapping["old_module_path"]
            width, height = png_dimensions(old_file)
            entry = {
                "assetPath": target,
                "mode": "transparent",
                "width": width,
                "height": height,
            }
        elif classification == "no_current_official_match":
            same_size = mapping["new_same_path_size"]
            same_sha256 = mapping["new_same_path_sha256"]
            if mapping["new_same_path_status"] == "missing" or not same_sha256:
                raise ValueError(f"Cannot passthrough missing current official asset: {target}")
            entry = {
                "assetPath": target,
                "mode": "passthrough",
                "sourceSize": same_size,
                "sourceSha256": same_sha256,
            }
        else:
            raise ValueError(f"Unsupported content classification {classification!r}")
        counts[str(entry["mode"])] += 1
        entries.append(entry)

    output = {
        "formatVersion": 2,
        "targetPackage": "moe.low.arc",
        "gameVersion": args.game_version,
        "gameApkSha256": args.game_apk_sha256.lower(),
        "packId": args.pack_id,
        "entries": entries,
    }
    summary = {
        "formatVersion": 2,
        "packId": args.pack_id,
        "packName": args.pack_name,
        "gameVersion": args.game_version,
        "gameApkSha256": args.game_apk_sha256.lower(),
        "entryCount": len(entries),
        "modeCounts": dict(sorted(counts.items())),
        "distributionPolicy": (
            "Index only. Official source bytes are read from the user's installed game and "
            "cached locally after SHA-256 verification; transparent images are generated on-device."
        ),
    }

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.summary_output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    args.summary_output.write_text(
        json.dumps(summary, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
