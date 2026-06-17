package dev.arc.assets;

import android.content.res.AssetManager;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import de.robv.android.xposed.XposedBridge;

final class TestPackSeeder {
    private TestPackSeeder() {
    }

    static void ensureTestPackAsync(AssetManager moduleAssets, File root, AssetOverrideIndex index) {
        Thread thread = new Thread(
                () -> {
                    try {
                        ensureTestPack(moduleAssets, root, index);
                    } catch (Throwable throwable) {
                        XposedBridge.log("ArcDark: async test_pkg seed failed");
                        XposedBridge.log(throwable);
                    }
                },
                "ArcDark-test-pack-seed"
        );
        thread.setDaemon(true);
        thread.start();
    }

    static File ensureTestPack(AssetManager moduleAssets, File root, AssetOverrideIndex index) throws Exception {
        File packDir = ArcDarkPaths.packDir(root, ArcDarkConstants.TEST_PACK_ID);
        if (isComplete(packDir, index)) {
            return packDir;
        }

        File packsDir = ArcDarkPaths.packsDir(root);
        if (!packsDir.isDirectory() && !packsDir.mkdirs()) {
            throw new IllegalStateException("Unable to create " + packsDir);
        }

        File tmpDir = new File(packsDir, ArcDarkConstants.TEST_PACK_ID + ".tmp");
        deleteRecursively(tmpDir);
        if (!tmpDir.mkdirs()) {
            throw new IllegalStateException("Unable to create " + tmpDir);
        }

        copyAsset(moduleAssets, "arc_overrides/index.json", new File(tmpDir, "arc_overrides/index.json"));
        copyAsset(moduleAssets, "arc_overrides/summary.json", new File(tmpDir, "arc_overrides/summary.json"));
        for (AssetOverride override : index.entries()) {
            copyAsset(moduleAssets, override.modulePath, new File(tmpDir, override.modulePath));
        }
        writePackManifest(tmpDir, index.size());

        deleteRecursively(packDir);
        if (!tmpDir.renameTo(packDir)) {
            throw new IllegalStateException("Unable to move " + tmpDir + " to " + packDir);
        }
        return packDir;
    }

    static boolean isComplete(File packDir, AssetOverrideIndex index) {
        if (!new File(packDir, "pack.json").isFile()
                || !new File(packDir, "arc_overrides/index.json").isFile()
                || !new File(packDir, "arc_overrides/summary.json").isFile()) {
            return false;
        }

        for (AssetOverride override : index.entries()) {
            File file = new File(packDir, override.modulePath);
            if (!file.isFile() || file.length() != override.size) {
                return false;
            }
        }
        return true;
    }

    private static void copyAsset(AssetManager assets, String assetPath, File destination) throws Exception {
        File parent = destination.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("Unable to create " + parent);
        }

        try (InputStream in = assets.open(assetPath);
             FileOutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    private static void writePackManifest(File packDir, int assetCount) throws Exception {
        JSONObject root = new JSONObject();
        root.put("id", ArcDarkConstants.TEST_PACK_ID);
        root.put("name", ArcDarkConstants.TEST_PACK_ID);
        root.put("source", ArcDarkConstants.DEFAULT_PACK_ID);
        root.put("assetCount", assetCount);
        root.put("generatedAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date()));

        File file = new File(packDir, "pack.json");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void deleteRecursively(File file) {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }
}
