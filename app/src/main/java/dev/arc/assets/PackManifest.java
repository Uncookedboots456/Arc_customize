package dev.arc.assets;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class PackManifest {
    static final int FORMAT_VERSION = 1;
    static final String ASSET_PREFIX = "assets/";
    private static final Set<String> DEFAULT_ASSET_FOLDERS = defaultAssetFolders();

    final String id;
    final String name;
    final String version;
    final String description;
    final String author;
    final String cover;
    final Map<String, String> changeMap;
    final int formatVersion;

    PackManifest(String id, String name, int formatVersion) {
        this(id, name, "", "", "", "", Collections.emptyMap(), formatVersion);
    }

    PackManifest(
            String id,
            String name,
            String version,
            String description,
            String author,
            String cover,
            Map<String, String> changeMap,
            int formatVersion
    ) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.description = description;
        this.author = author;
        this.cover = cover;
        this.changeMap = Collections.unmodifiableMap(new LinkedHashMap<>(changeMap));
        this.formatVersion = formatVersion;
    }

    static PackManifest readRequired(InputStream input) throws Exception {
        JSONObject root = new JSONObject(ArcDarkFileOps.readUtf8(input));
        int formatVersion = root.getInt("formatVersion");
        String id = root.getString("id");
        String name = root.getString("name");
        validateExternal(id);
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported pack formatVersion: " + formatVersion);
        }
        if (name.trim().length() == 0) {
            throw new IllegalArgumentException("Pack name is empty");
        }
        return new PackManifest(
                id,
                name,
                optionalText(root, "version"),
                optionalText(root, "description"),
                optionalText(root, "author"),
                optionalCover(root),
                readChangeMap(root.optJSONObject("Change")),
                formatVersion
        );
    }

    static PackManifest readLenient(File file, String fallbackId, String fallbackName) {
        if (!file.isFile()) {
            return new PackManifest(fallbackId, fallbackName, FORMAT_VERSION);
        }
        try (InputStream input = new FileInputStream(file)) {
            JSONObject root = new JSONObject(ArcDarkFileOps.readUtf8(input));
            String id = root.optString("id", fallbackId);
            String name = root.optString("name", fallbackName);
            int formatVersion = root.optInt("formatVersion", FORMAT_VERSION);
            if (!ArcDarkControl.isAllowedPackId(id)) {
                id = fallbackId;
            }
            if (name.trim().length() == 0) {
                name = fallbackName;
            }
            return new PackManifest(
                    id,
                    name,
                    optionalText(root, "version"),
                    optionalText(root, "description"),
                    optionalText(root, "author"),
                    optionalCoverLenient(root),
                    readChangeMapLenient(root.optJSONObject("Change")),
                    formatVersion
            );
        } catch (Exception ignored) {
            return new PackManifest(fallbackId, fallbackName, FORMAT_VERSION);
        }
    }

    JSONObject toJson(int assetCount, String importedAtUtc) throws Exception {
        JSONObject root = new JSONObject();
        root.put("formatVersion", FORMAT_VERSION);
        root.put("id", id);
        root.put("name", name);
        if (version.length() > 0) {
            root.put("version", version);
        }
        if (description.length() > 0) {
            root.put("description", description);
        }
        if (author.length() > 0) {
            root.put("author", author);
        }
        if (cover.length() > 0) {
            root.put("cover", cover);
        }
        if (!changeMap.isEmpty()) {
            JSONObject change = new JSONObject();
            for (Map.Entry<String, String> entry : changeMap.entrySet()) {
                change.put(entry.getKey(), entry.getValue());
            }
            root.put("Change", change);
        }
        root.put("assetCount", assetCount);
        root.put("importedAtUtc", importedAtUtc);
        return root;
    }

    String mapAssetPath(String sourcePath) {
        String normalized = normalizePath(sourcePath);
        if (!normalized.startsWith(ASSET_PREFIX)) {
            throw new IllegalArgumentException("Unsupported ZIP entry: " + sourcePath);
        }

        String relative = normalized.substring(ASSET_PREFIX.length());
        int slash = relative.indexOf('/');
        if (slash <= 0 || slash == relative.length() - 1) {
            throw new IllegalArgumentException("Asset entry must be inside a folder: " + sourcePath);
        }

        String sourceFolder = relative.substring(0, slash);
        String nestedPath = relative.substring(slash + 1);
        String targetFolder = changeMap.containsKey(sourceFolder)
                ? changeMap.get(sourceFolder)
                : sourceFolder;
        if (!DEFAULT_ASSET_FOLDERS.contains(sourceFolder) && !changeMap.containsKey(sourceFolder)) {
            throw new IllegalArgumentException("Unsupported asset folder: " + sourceFolder);
        }
        validateRelativePath(targetFolder + "/" + nestedPath, "mapped asset path");
        return ASSET_PREFIX + targetFolder + "/" + nestedPath;
    }

    boolean isExpectedCover(String entryName) {
        return cover.length() > 0 && cover.equals(normalizePath(entryName));
    }

    private static void validateExternal(String packId) {
        if (!ArcDarkControl.isExternalPackId(packId)) {
            throw new IllegalArgumentException("Invalid third-party pack id: " + packId);
        }
    }

    private static String optionalText(JSONObject root, String key) {
        if (!root.has(key) || root.isNull(key)) {
            return "";
        }
        return root.optString(key, "").trim();
    }

    private static String optionalCover(JSONObject root) {
        String value = optionalText(root, "cover");
        if (value.length() == 0) {
            return "";
        }
        validateFileName(value, "cover");
        return value;
    }

    private static String optionalCoverLenient(JSONObject root) {
        try {
            return optionalCover(root);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static Map<String, String> readChangeMap(JSONObject change) {
        Map<String, String> mapped = new LinkedHashMap<>();
        if (change == null) {
            return mapped;
        }

        java.util.Iterator<String> keys = change.keys();
        while (keys.hasNext()) {
            String sourceFolder = keys.next();
            validateFolderName(sourceFolder, "Change source");
            String targetFolder = change.optString(sourceFolder, "").trim();
            validateRelativePath(targetFolder, "Change target");
            mapped.put(sourceFolder, targetFolder);
        }
        return mapped;
    }

    private static Map<String, String> readChangeMapLenient(JSONObject change) {
        try {
            return readChangeMap(change);
        } catch (Exception ignored) {
            return Collections.emptyMap();
        }
    }

    private static void validateFolderName(String value, String fieldName) {
        if (value == null || value.length() == 0) {
            throw new IllegalArgumentException(fieldName + " is empty");
        }
        if (value.indexOf('/') != -1 || value.indexOf('\\') != -1 || value.indexOf(':') != -1
                || ".".equals(value) || "..".equals(value)) {
            throw new IllegalArgumentException(fieldName + " is unsafe: " + value);
        }
    }

    private static void validateFileName(String value, String fieldName) {
        validateFolderName(value, fieldName);
    }

    private static void validateRelativePath(String value, String fieldName) {
        String normalized = normalizePath(value);
        if (normalized.length() == 0 || normalized.startsWith("/") || normalized.indexOf(':') != -1) {
            throw new IllegalArgumentException(fieldName + " must be relative: " + value);
        }
        String[] segments = normalized.split("/", -1);
        for (String segment : segments) {
            if (segment.length() == 0 || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException(fieldName + " contains unsafe segment: " + value);
            }
        }
    }

    private static String normalizePath(String value) {
        return value == null ? "" : value.replace('\\', '/');
    }

    private static Set<String> defaultAssetFolders() {
        Set<String> folders = new LinkedHashSet<>();
        folders.add("audio");
        folders.add("char");
        folders.add("Default Fonts");
        folders.add("img");
        folders.add("layout");
        folders.add("models");
        folders.add("particle");
        folders.add("songs");
        folders.add("start_up");
        folders.add("voice");
        return Collections.unmodifiableSet(folders);
    }
}
