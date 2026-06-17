package dev.arc.assets;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class PackManifest {
    static final int FORMAT_VERSION = 1;

    final String id;
    final String name;
    final int formatVersion;

    PackManifest(String id, String name, int formatVersion) {
        this.id = id;
        this.name = name;
        this.formatVersion = formatVersion;
    }

    static PackManifest readRequired(InputStream input) throws Exception {
        JSONObject root = new JSONObject(readUtf8(input));
        int version = root.getInt("formatVersion");
        String id = root.getString("id");
        String name = root.getString("name");
        validateExternal(id);
        if (version != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported pack formatVersion: " + version);
        }
        if (name.trim().length() == 0) {
            throw new IllegalArgumentException("Pack name is empty");
        }
        return new PackManifest(id, name, version);
    }

    static PackManifest readLenient(File file, String fallbackId, String fallbackName) {
        if (!file.isFile()) {
            return new PackManifest(fallbackId, fallbackName, FORMAT_VERSION);
        }
        try (InputStream input = new FileInputStream(file)) {
            JSONObject root = new JSONObject(readUtf8(input));
            String id = root.optString("id", fallbackId);
            String name = root.optString("name", fallbackName);
            int version = root.optInt("formatVersion", FORMAT_VERSION);
            if (!ArcDarkControl.isAllowedPackId(id)) {
                id = fallbackId;
            }
            if (name.trim().length() == 0) {
                name = fallbackName;
            }
            return new PackManifest(id, name, version);
        } catch (Exception ignored) {
            return new PackManifest(fallbackId, fallbackName, FORMAT_VERSION);
        }
    }

    JSONObject toJson(int assetCount, String importedAtUtc) throws Exception {
        JSONObject root = new JSONObject();
        root.put("formatVersion", FORMAT_VERSION);
        root.put("id", id);
        root.put("name", name);
        root.put("assetCount", assetCount);
        root.put("importedAtUtc", importedAtUtc);
        return root;
    }

    private static void validateExternal(String packId) {
        if (!ArcDarkControl.isExternalPackId(packId)) {
            throw new IllegalArgumentException("Invalid third-party pack id: " + packId);
        }
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
