# Arcaea 6.16.0c 资源哈希映射

本报告把旧覆盖包逐项与新版官方 `assets/` 比较，并以 SHA-256 对新版全部资源做反向查找。
`canonical_source_asset_path` 只是确定性的代表路径；存在多个候选时，它们内容逐字节相同，不能仅凭哈希断言历史上具体选用了哪一个文件名。

## 汇总

- 旧覆盖条目：348
- 新版官方 assets 文件：6787
- 同路径内容未变：0
- 同路径内容不同：288
- 新版同路径缺失：60
- 旧覆盖内容的不同 SHA-256 组：43
- 可在新版官方 assets 中精确命中的内容组：8
- 新版已无精确官方来源的内容组：35
- 其中全透明占位图：33 组 / 179 个目标
- 非透明且新版无精确来源：2 组

补充校验使用 Pillow 12.2.0 将旧包 43 组图片与新版 5,545 个 PNG/JPG 解码为 RGBA 后再次计算像素 SHA-256；仍然只命中相同的 8 组，新增命中为 0。因此其余 35 组不是单纯由 PNG/JPG 压缩参数或元数据变化造成的文件哈希差异。

## 对应关系组

|目标数|需替换|同路径未变|官方候选数|内容分类|代表来源|状态|
|---:|---:|---:|---:|---|---|---|
|149|149|0|1|exact_current_official_content|`assets/img/bg/1080/observer_conflict.jpg`|unique_official_source|
|65|65|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|28|28|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|12|12|0|1|exact_current_official_content|`assets/img/track_black.png`|unique_official_source|
|11|11|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|10|10|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|7|7|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|5|5|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|5|5|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|3|3|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|3|3|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|3|3|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|3|3|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|3|3|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|2|2|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|2|2|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|2|2|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|2|2|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|2|2|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|2|2|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|2|2|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|2|2|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|2|2|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|2|2|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|2|2|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|2|2|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|1|1|0|1|exact_current_official_content|`assets/models/tap_d.png`|unique_official_source|
|1|1|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|1|1|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|1|1|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|1|1|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|1|1|0|1|exact_current_official_content|`assets/img/note_hold_dark.png`|unique_official_source|
|1|1|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|1|1|0|1|exact_current_official_content|`assets/img/note_hold_dark_hi.png`|unique_official_source|
|1|1|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|1|1|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|1|1|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|1|1|0|0|fully_transparent_placeholder|`—`|no_current_official_match|
|1|1|0|1|exact_current_official_content|`assets/models/sfx_l_core.jpg`|unique_official_source|
|1|1|0|0|no_current_official_match|`—`|no_current_official_match|
|1|1|0|0|no_current_official_match|`—`|no_current_official_match|
|1|1|0|1|exact_current_official_content|`assets/models/sfx_l_note.jpg`|unique_official_source|
|1|1|0|1|exact_current_official_content|`assets/img/note_dark.png`|unique_official_source|

## 非透明且未在新版官方资源中命中的旧内容

- `assets/img/card_mask.png`
- `assets/img/course/hp-bar.png`

## 文件说明

- `file-mapping.csv`：每个旧目标路径的同路径比较与全部官方来源候选。
- `hash-groups.csv`：按旧内容哈希合并后的‘一个来源对应多个目标’关系。
- `official-assets-sha256.csv`：新版全部官方 assets 的 SHA-256 索引。
- `mapping.json`：以上数据的完整机器可读版本及统计。
- `pixel-hash-validation.json`：解码后像素哈希的补充校验统计。
