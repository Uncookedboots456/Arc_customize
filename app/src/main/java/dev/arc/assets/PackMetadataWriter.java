package dev.arc.assets;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class PackMetadataWriter {
    private static final String MANIFEST_ENTRY = "pack.json";

    private PackMetadataWriter() {
    }

    static void writeImportedMetadata(
            File packDir,
            PackManifest manifest,
            List<AssetOverride> entries,
            String importedAt
    ) throws Exception {
        ArcDarkFileOps.writeUtf8(
                new File(packDir, MANIFEST_ENTRY),
                manifest.toJson(entries.size(), importedAt).toString(2)
        );

        File metadataDir = new File(packDir, "arc_overrides");
        if (!metadataDir.isDirectory() && !metadataDir.mkdirs()) {
            throw new IllegalStateException("Unable to create " + metadataDir);
        }

        JSONArray entryArray = new JSONArray();
        for (AssetOverride entry : entries) {
            entryArray.put(toIndexJson(entry));
        }

        JSONObject index = new JSONObject();
        index.put("targetPackage", ArcDarkConstants.TARGET_PACKAGE);
        index.put("source", "zip_import");
        index.put("packId", manifest.id);
        index.put("generatedAtUtc", importedAt);
        index.put("entries", entryArray);
        ArcDarkFileOps.writeUtf8(new File(metadataDir, "index.json"), index.toString(2));

        JSONObject summary = new JSONObject();
        summary.put("source", "zip_import");
        summary.put("packId", manifest.id);
        summary.put("includedAssets", entries.size());
        summary.put("generatedAtUtc", importedAt);
        summary.put("includedPolicy", "Only safe root cover files and mapped assets/ files are imported.");
        ArcDarkFileOps.writeUtf8(new File(metadataDir, "summary.json"), summary.toString(2));
    }

    static void writeTestPackManifest(File packDir, int assetCount) throws Exception {
        JSONObject root = new JSONObject();
        root.put("id", ArcDarkConstants.TEST_PACK_ID);
        root.put("name", ArcDarkConstants.TEST_PACK_ID);
        root.put("source", ArcDarkConstants.DEFAULT_PACK_ID);
        root.put("assetCount", assetCount);
        root.put(
                "generatedAt",
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date())
        );

        ArcDarkFileOps.writeUtf8(new File(packDir, MANIFEST_ENTRY), root.toString(2));
    }

    private static JSONObject toIndexJson(AssetOverride entry) throws Exception {
        JSONObject item = new JSONObject();
        item.put("assetPath", entry.assetPath);
        item.put("modulePath", entry.modulePath);
        item.put("size", entry.size);
        item.put("sha256", entry.sha256);
        item.put("materialize", entry.materialize);
        return item;
    }
}
