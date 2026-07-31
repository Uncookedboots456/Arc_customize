package dev.arc.assets;

import android.content.res.AssetManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AssetOverrideIndex {
    private static final String INDEX_ASSET = "arc_overrides/index.json";

    private final Map<String, AssetOverride> overrides;

    private AssetOverrideIndex(Map<String, AssetOverride> overrides) {
        this.overrides = overrides;
    }

    static AssetOverrideIndex load(AssetManager moduleAssets) throws Exception {
        return load(moduleAssets.open(INDEX_ASSET));
    }

    static AssetOverrideIndex load(AssetManager moduleAssets, String indexAssetPath) throws Exception {
        return load(moduleAssets.open(indexAssetPath));
    }

    static AssetOverrideIndex load(File indexFile) throws Exception {
        return load(new FileInputStream(indexFile));
    }

    static AssetOverrideIndex load(InputStream input) throws Exception {
        String json = ArcDarkFileOps.readUtf8(input);
        JSONObject root = new JSONObject(json);
        JSONArray entries = root.getJSONArray("entries");
        Map<String, AssetOverride> loaded = new HashMap<>();

        for (int i = 0; i < entries.length(); i++) {
            JSONObject item = entries.getJSONObject(i);
            String assetPath = normalizeIndexPath(item.getString("assetPath"), "assetPath");
            String mode = item.optString("mode", AssetOverride.MODE_BUNDLED);
            AssetOverride override;
            if (AssetOverride.MODE_BUNDLED.equals(mode)) {
                String modulePath = normalizeIndexPath(item.getString("modulePath"), "modulePath");
                override = new AssetOverride(
                        assetPath,
                        modulePath,
                        item.getLong("size"),
                        item.getString("sha256"),
                        item.optBoolean("materialize", true)
                );
            } else if (AssetOverride.MODE_ALIAS.equals(mode)) {
                override = indexedOverride(
                        item,
                        assetPath,
                        mode,
                        normalizeIndexPath(item.getString("sourceAssetPath"), "sourceAssetPath")
                );
            } else if (AssetOverride.MODE_PASSTHROUGH.equals(mode)) {
                override = indexedOverride(item, assetPath, mode, assetPath);
            } else if (AssetOverride.MODE_TRANSPARENT.equals(mode)) {
                int width = item.getInt("width");
                int height = item.getInt("height");
                if (width <= 0 || height <= 0 || width > 8192 || height > 8192) {
                    throw new IllegalArgumentException(
                            "transparent dimensions are invalid: " + width + "x" + height
                    );
                }
                override = new AssetOverride(
                        assetPath,
                        mode,
                        "",
                        "",
                        0,
                        "",
                        width,
                        height,
                        true
                );
            } else {
                throw new IllegalArgumentException("Unsupported override mode: " + mode);
            }
            loaded.put(assetPath, override);
        }

        return new AssetOverrideIndex(Collections.unmodifiableMap(loaded));
    }

    static AssetOverrideIndex of(Collection<AssetOverride> entries) {
        Map<String, AssetOverride> loaded = new LinkedHashMap<>();
        for (AssetOverride entry : entries) {
            loaded.put(entry.assetPath, entry);
        }
        return new AssetOverrideIndex(Collections.unmodifiableMap(loaded));
    }

    static AssetOverrideIndex merge(List<AssetOverrideIndex> indexes) {
        Map<String, AssetOverride> merged = new LinkedHashMap<>();
        for (AssetOverrideIndex index : indexes) {
            for (AssetOverride override : index.entries()) {
                if (!merged.containsKey(override.assetPath)) {
                    merged.put(override.assetPath, override);
                }
            }
        }
        return new AssetOverrideIndex(Collections.unmodifiableMap(merged));
    }

    AssetOverride find(String requestedPath) {
        String normalized = normalize(requestedPath);
        AssetOverride direct = overrides.get(normalized);
        if (direct != null) {
            return direct;
        }
        if (!normalized.startsWith("assets/")) {
            return overrides.get("assets/" + normalized);
        }
        return null;
    }

    int size() {
        return overrides.size();
    }

    Collection<AssetOverride> entries() {
        return overrides.values();
    }

    private static AssetOverride indexedOverride(
            JSONObject item,
            String assetPath,
            String mode,
            String sourceAssetPath
    ) throws Exception {
        long sourceSize = item.getLong("sourceSize");
        String sourceSha256 = item.getString("sourceSha256").toLowerCase(java.util.Locale.US);
        if (sourceSize < 0) {
            throw new IllegalArgumentException("sourceSize must not be negative");
        }
        if (!sourceSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sourceSha256 must be 64 lowercase hex characters");
        }
        return new AssetOverride(
                assetPath,
                mode,
                "",
                sourceAssetPath,
                sourceSize,
                sourceSha256,
                0,
                0,
                true
        );
    }

    private static String normalize(String path) {
        String value = path == null ? "" : path.replace('\\', '/');
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    private static String normalizeIndexPath(String path, String fieldName) {
        if (path == null) {
            throw new IllegalArgumentException(fieldName + " is missing");
        }

        String value = path.replace('\\', '/');
        if (value.length() == 0) {
            throw new IllegalArgumentException(fieldName + " is empty");
        }
        if (value.startsWith("/") || value.indexOf(':') != -1) {
            throw new IllegalArgumentException(fieldName + " must be relative: " + path);
        }

        String[] segments = value.split("/", -1);
        for (String segment : segments) {
            if (segment.length() == 0 || "..".equals(segment)) {
                throw new IllegalArgumentException(fieldName + " contains unsafe segment: " + path);
            }
        }
        return value;
    }

}
