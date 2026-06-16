package dev.arc.assets;

import android.content.res.AssetManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

final class AssetOverrideIndex {
    private static final String INDEX_ASSET = "arc_overrides/index.json";

    private final Map<String, AssetOverride> overrides;

    private AssetOverrideIndex(Map<String, AssetOverride> overrides) {
        this.overrides = overrides;
    }

    static AssetOverrideIndex load(AssetManager moduleAssets) throws Exception {
        String json = readUtf8(moduleAssets.open(INDEX_ASSET));
        JSONObject root = new JSONObject(json);
        JSONArray entries = root.getJSONArray("entries");
        Map<String, AssetOverride> loaded = new HashMap<>();

        for (int i = 0; i < entries.length(); i++) {
            JSONObject item = entries.getJSONObject(i);
            String assetPath = normalize(item.getString("assetPath"));
            AssetOverride override = new AssetOverride(
                    assetPath,
                    item.getString("modulePath"),
                    item.getLong("size"),
                    item.getString("sha256"),
                    item.optBoolean("materialize", true)
            );
            loaded.put(assetPath, override);
        }

        return new AssetOverrideIndex(Collections.unmodifiableMap(loaded));
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

    private static String normalize(String path) {
        String value = path == null ? "" : path.replace('\\', '/');
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }

    private static String readUtf8(InputStream input) throws Exception {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
