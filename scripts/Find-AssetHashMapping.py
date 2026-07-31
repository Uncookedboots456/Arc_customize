#!/usr/bin/env python3
"""Compare an existing override pack with unpacked official APK assets.

The report keeps the same-path comparison separate from a global SHA-256
lookup.  The latter reveals cases where many target paths contain a copy of
one selected official asset.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import json
import struct
import zlib
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def png_is_fully_transparent(path: Path) -> bool:
    """Return true for supported 8-bit, non-interlaced alpha PNGs with alpha=0."""
    data = path.read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        return False
    offset = 8
    width = height = bit_depth = color_type = interlace = None
    compressed = bytearray()
    while offset + 12 <= len(data):
        length = struct.unpack(">I", data[offset : offset + 4])[0]
        chunk_type = data[offset + 4 : offset + 8]
        payload = data[offset + 8 : offset + 8 + length]
        offset += length + 12
        if chunk_type == b"IHDR":
            width, height, bit_depth, color_type, _, _, interlace = struct.unpack(
                ">IIBBBBB", payload
            )
        elif chunk_type == b"IDAT":
            compressed.extend(payload)
        elif chunk_type == b"IEND":
            break
    if not width or not height or bit_depth != 8 or interlace != 0:
        return False
    bytes_per_pixel = {4: 2, 6: 4}.get(color_type)
    if bytes_per_pixel is None:
        return False

    raw = zlib.decompress(bytes(compressed))
    stride = width * bytes_per_pixel
    previous = bytearray(stride)
    position = 0

    def paeth(left: int, above: int, upper_left: int) -> int:
        estimate = left + above - upper_left
        left_distance = abs(estimate - left)
        above_distance = abs(estimate - above)
        upper_left_distance = abs(estimate - upper_left)
        if left_distance <= above_distance and left_distance <= upper_left_distance:
            return left
        if above_distance <= upper_left_distance:
            return above
        return upper_left

    for _ in range(height):
        filter_type = raw[position]
        position += 1
        current = bytearray(raw[position : position + stride])
        position += stride
        for index in range(stride):
            left = current[index - bytes_per_pixel] if index >= bytes_per_pixel else 0
            above = previous[index]
            upper_left = previous[index - bytes_per_pixel] if index >= bytes_per_pixel else 0
            if filter_type == 1:
                current[index] = (current[index] + left) & 0xFF
            elif filter_type == 2:
                current[index] = (current[index] + above) & 0xFF
            elif filter_type == 3:
                current[index] = (current[index] + ((left + above) // 2)) & 0xFF
            elif filter_type == 4:
                current[index] = (current[index] + paeth(left, above, upper_left)) & 0xFF
            elif filter_type != 0:
                raise ValueError(f"Unsupported PNG filter {filter_type} in {path}")
        if any(current[index] for index in range(bytes_per_pixel - 1, stride, bytes_per_pixel)):
            return False
        previous = current
    return True


def write_csv(path: Path, fieldnames: list[str], rows: list[dict[str, object]]) -> None:
    with path.open("w", encoding="utf-8-sig", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--old-pack", required=True, type=Path)
    parser.add_argument("--new-assets", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--apk", type=Path)
    parser.add_argument("--version", default="unknown")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    old_pack = args.old_pack.resolve()
    new_assets = args.new_assets.resolve()
    output = args.output.resolve()
    index_path = old_pack / "arc_overrides" / "index.json"

    if not index_path.is_file():
        raise SystemExit(f"Missing old-pack index: {index_path}")
    if not new_assets.is_dir():
        raise SystemExit(f"Missing unpacked assets directory: {new_assets}")

    old_index = json.loads(index_path.read_text(encoding="utf-8-sig"))
    old_entries = old_index.get("entries", [])
    target_paths = [entry["assetPath"] for entry in old_entries]
    if len(target_paths) != len(set(target_paths)):
        raise SystemExit("Old-pack index contains duplicate assetPath values")

    for entry in old_entries:
        old_file = old_pack / entry["modulePath"]
        if not old_file.is_file():
            raise SystemExit(f"Missing indexed old-pack file: {old_file}")
        actual_hash = sha256_file(old_file)
        actual_size = old_file.stat().st_size
        if actual_hash != entry["sha256"] or actual_size != entry["size"]:
            raise SystemExit(f"Old-pack index mismatch: {old_file}")

    official_rows: list[dict[str, object]] = []
    official_by_path: dict[str, dict[str, object]] = {}
    official_by_hash: defaultdict[str, list[str]] = defaultdict(list)
    for path in sorted(p for p in new_assets.rglob("*") if p.is_file()):
        asset_path = "assets/" + path.relative_to(new_assets).as_posix()
        row = {
            "asset_path": asset_path,
            "size": path.stat().st_size,
            "sha256": sha256_file(path),
        }
        official_rows.append(row)
        official_by_path[asset_path] = row
        official_by_hash[str(row["sha256"])].append(asset_path)

    target_set = set(target_paths)
    stable_official_sources = {
        entry["assetPath"]
        for entry in old_entries
        if entry["assetPath"] in official_by_path
        and official_by_path[entry["assetPath"]]["sha256"] == entry["sha256"]
    }
    targets_by_old_hash: defaultdict[str, list[str]] = defaultdict(list)
    old_file_by_hash: dict[str, Path] = {}
    for entry in old_entries:
        targets_by_old_hash[entry["sha256"]].append(entry["assetPath"])
        old_file_by_hash.setdefault(entry["sha256"], old_pack / entry["modulePath"])
    legacy_representative_by_hash = {
        old_hash: sorted(paths)[0] for old_hash, paths in targets_by_old_hash.items()
    }
    content_kind_by_hash: dict[str, str] = {}
    for old_hash, old_file in old_file_by_hash.items():
        if official_by_hash.get(old_hash):
            content_kind_by_hash[old_hash] = "exact_current_official_content"
        elif old_file.suffix.lower() == ".png" and png_is_fully_transparent(old_file):
            content_kind_by_hash[old_hash] = "fully_transparent_placeholder"
        else:
            content_kind_by_hash[old_hash] = "no_current_official_match"

    mappings: list[dict[str, object]] = []
    for entry in old_entries:
        target = entry["assetPath"]
        old_hash = entry["sha256"]
        same = official_by_path.get(target)
        if same is None:
            same_path_status = "missing"
            same_hash = ""
            same_size: object = ""
        elif same["sha256"] == old_hash:
            same_path_status = "same"
            same_hash = same["sha256"]
            same_size = same["size"]
        else:
            same_path_status = "different"
            same_hash = same["sha256"]
            same_size = same["size"]

        candidates = sorted(official_by_hash.get(old_hash, []))
        stable_candidates = [path for path in candidates if path in stable_official_sources]
        if not candidates:
            resolution = "no_current_official_match"
            representative = ""
        elif same_path_status == "same":
            resolution = "same_path_official"
            representative = target
        elif len(candidates) == 1:
            resolution = "unique_official_source"
            representative = candidates[0]
        elif len(stable_candidates) == 1:
            resolution = "one_stable_source_multiple_identical_candidates"
            representative = stable_candidates[0]
        else:
            resolution = "multiple_identical_official_candidates"
            representative = (stable_candidates or candidates)[0]

        mappings.append(
            {
                "target_asset_path": target,
                "old_module_path": entry["modulePath"],
                "old_size": entry["size"],
                "old_sha256": old_hash,
                "new_same_path_status": same_path_status,
                "new_same_path_size": same_size,
                "new_same_path_sha256": same_hash,
                "content_classification": content_kind_by_hash[old_hash],
                "legacy_group_representative_target": legacy_representative_by_hash[old_hash],
                "source_resolution": resolution,
                "canonical_source_asset_path": representative,
                "source_candidate_count": len(candidates),
                "source_candidates": "|".join(candidates),
            }
        )

    grouped: defaultdict[str, list[dict[str, object]]] = defaultdict(list)
    for mapping in mappings:
        grouped[str(mapping["old_sha256"])].append(mapping)

    group_rows: list[dict[str, object]] = []
    for old_hash, items in grouped.items():
        candidates = sorted(official_by_hash.get(old_hash, []))
        replacement_targets = [
            str(item["target_asset_path"])
            for item in items
            if item["new_same_path_status"] != "same"
        ]
        unchanged_targets = [
            str(item["target_asset_path"])
            for item in items
            if item["new_same_path_status"] == "same"
        ]
        canonical = str(items[0]["canonical_source_asset_path"])
        resolution = str(items[0]["source_resolution"])
        if unchanged_targets:
            canonical = sorted(unchanged_targets)[0]
            resolution = "stable_same_path_source"
        elif len(candidates) == 1:
            canonical = candidates[0]
            resolution = "unique_official_source"
        elif len(candidates) > 1:
            canonical = candidates[0]
            resolution = "multiple_identical_official_candidates"
        else:
            canonical = ""
            resolution = "no_current_official_match"

        group_rows.append(
            {
                "old_sha256": old_hash,
                "old_size": items[0]["old_size"],
                "target_count": len(items),
                "replacement_target_count": len(replacement_targets),
                "unchanged_target_count": len(unchanged_targets),
                "content_classification": items[0]["content_classification"],
                "legacy_group_representative_target": items[0][
                    "legacy_group_representative_target"
                ],
                "source_resolution": resolution,
                "canonical_source_asset_path": canonical,
                "source_candidate_count": len(candidates),
                "source_candidates": "|".join(candidates),
                "replacement_targets": "|".join(sorted(replacement_targets)),
                "unchanged_targets": "|".join(sorted(unchanged_targets)),
            }
        )
    group_rows.sort(key=lambda row: (-int(row["target_count"]), str(row["old_sha256"])))

    same_counts = {
        status: sum(1 for row in mappings if row["new_same_path_status"] == status)
        for status in ("same", "different", "missing")
    }
    resolution_counts: dict[str, int] = {}
    for row in mappings:
        key = str(row["source_resolution"])
        resolution_counts[key] = resolution_counts.get(key, 0) + 1

    apk_info: dict[str, object] | None = None
    if args.apk:
        apk = args.apk.resolve()
        if not apk.is_file():
            raise SystemExit(f"Missing APK: {apk}")
        apk_info = {
            "fileName": apk.name,
            "size": apk.stat().st_size,
            "sha256": sha256_file(apk),
        }

    generated_at = datetime.now(timezone.utc).isoformat()
    summary = {
        "version": args.version,
        "generatedAtUtc": generated_at,
        "apk": apk_info,
        "oldPack": {
            "originalApk": old_index.get("originalApk"),
            "fixedApk": old_index.get("fixedApk"),
            "entryCount": len(old_entries),
        },
        "officialAssetCount": len(official_rows),
        "samePath": same_counts,
        "sourceResolution": resolution_counts,
        "distinctOldContentHashes": len(group_rows),
        "groupsWithCurrentOfficialMatch": sum(
            1 for row in group_rows if int(row["source_candidate_count"]) > 0
        ),
        "groupsWithoutCurrentOfficialMatch": sum(
            1 for row in group_rows if int(row["source_candidate_count"]) == 0
        ),
        "transparentPlaceholderGroups": sum(
            1
            for row in group_rows
            if row["content_classification"] == "fully_transparent_placeholder"
        ),
        "transparentPlaceholderFiles": sum(
            int(row["target_count"])
            for row in group_rows
            if row["content_classification"] == "fully_transparent_placeholder"
        ),
        "nonTransparentGroupsWithoutCurrentOfficialMatch": sum(
            1
            for row in group_rows
            if row["content_classification"] == "no_current_official_match"
        ),
    }

    output.mkdir(parents=True, exist_ok=True)
    write_csv(output / "file-mapping.csv", list(mappings[0]), mappings)
    write_csv(output / "hash-groups.csv", list(group_rows[0]), group_rows)
    write_csv(output / "official-assets-sha256.csv", list(official_rows[0]), official_rows)
    (output / "mapping.json").write_text(
        json.dumps({"summary": summary, "files": mappings, "groups": group_rows}, ensure_ascii=False, indent=2)
        + "\n",
        encoding="utf-8",
    )

    lines = [
        f"# Arcaea {args.version} 资源哈希映射",
        "",
        "本报告把旧覆盖包逐项与新版官方 `assets/` 比较，并以 SHA-256 对新版全部资源做反向查找。",
        "`canonical_source_asset_path` 只是确定性的代表路径；存在多个候选时，它们内容逐字节相同，不能仅凭哈希断言历史上具体选用了哪一个文件名。",
        "",
        "## 汇总",
        "",
        f"- 旧覆盖条目：{len(old_entries)}",
        f"- 新版官方 assets 文件：{len(official_rows)}",
        f"- 同路径内容未变：{same_counts['same']}",
        f"- 同路径内容不同：{same_counts['different']}",
        f"- 新版同路径缺失：{same_counts['missing']}",
        f"- 旧覆盖内容的不同 SHA-256 组：{len(group_rows)}",
        f"- 可在新版官方 assets 中精确命中的内容组：{summary['groupsWithCurrentOfficialMatch']}",
        f"- 新版已无精确官方来源的内容组：{summary['groupsWithoutCurrentOfficialMatch']}",
        f"- 其中全透明占位图：{summary['transparentPlaceholderGroups']} 组 / {summary['transparentPlaceholderFiles']} 个目标",
        f"- 非透明且新版无精确来源：{summary['nonTransparentGroupsWithoutCurrentOfficialMatch']} 组",
        "",
        "## 对应关系组",
        "",
        "|目标数|需替换|同路径未变|官方候选数|内容分类|代表来源|状态|",
        "|---:|---:|---:|---:|---|---|---|",
    ]
    for row in group_rows:
        source = str(row["canonical_source_asset_path"]) or "—"
        lines.append(
            f"|{row['target_count']}|{row['replacement_target_count']}|{row['unchanged_target_count']}|"
            f"{row['source_candidate_count']}|{row['content_classification']}|`{source}`|{row['source_resolution']}|"
        )
    unresolved = [
        row
        for row in mappings
        if row["content_classification"] == "no_current_official_match"
    ]
    lines.extend(["", "## 非透明且未在新版官方资源中命中的旧内容", ""])
    if unresolved:
        lines.extend(f"- `{row['target_asset_path']}`" for row in unresolved)
    else:
        lines.append("无。")
    lines.extend(
        [
            "",
            "## 文件说明",
            "",
            "- `file-mapping.csv`：每个旧目标路径的同路径比较与全部官方来源候选。",
            "- `hash-groups.csv`：按旧内容哈希合并后的‘一个来源对应多个目标’关系。",
            "- `official-assets-sha256.csv`：新版全部官方 assets 的 SHA-256 索引。",
            "- `mapping.json`：以上数据的完整机器可读版本及统计。",
            "",
        ]
    )
    (output / "README.md").write_text("\n".join(lines), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
