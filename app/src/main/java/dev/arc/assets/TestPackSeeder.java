package dev.arc.assets;

import android.content.res.AssetManager;

import java.io.File;

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
        ArcDarkFileOps.deleteRecursively(tmpDir);
        if (!tmpDir.mkdirs()) {
            throw new IllegalStateException("Unable to create " + tmpDir);
        }

        copyAsset(moduleAssets, "arc_overrides/index.json", new File(tmpDir, "arc_overrides/index.json"));
        copyAsset(moduleAssets, "arc_overrides/summary.json", new File(tmpDir, "arc_overrides/summary.json"));
        for (AssetOverride override : index.entries()) {
            copyAsset(moduleAssets, override.modulePath, new File(tmpDir, override.modulePath));
        }
        PackMetadataWriter.writeTestPackManifest(tmpDir, index.size());

        ArcDarkFileOps.deleteRecursively(packDir);
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
        ArcDarkFileOps.copy(assets.open(assetPath), destination);
    }
}
