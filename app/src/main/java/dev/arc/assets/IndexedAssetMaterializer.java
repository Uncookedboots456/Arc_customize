package dev.arc.assets;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

final class IndexedAssetMaterializer {
    private static final String CACHE_DIR = "arc_index_assets";

    private IndexedAssetMaterializer() {
    }

    static synchronized File materialize(Context targetContext, AssetOverride override)
            throws Exception {
        if (!override.isIndexed()) {
            throw new IllegalArgumentException("Not an indexed override: " + override.assetPath);
        }

        File root = new File(targetContext.getCacheDir(), CACHE_DIR);
        if (!root.isDirectory() && !root.mkdirs()) {
            throw new IllegalStateException("Unable to create " + root);
        }

        if (AssetOverride.MODE_TRANSPARENT.equals(override.mode)) {
            return materializeTransparent(root, override);
        }
        return materializeOfficial(targetContext, root, override);
    }

    private static File materializeOfficial(
            Context targetContext,
            File root,
            AssetOverride override
    ) throws Exception {
        File file = new File(root, "official-" + override.sha256);
        if (isVerified(file, override)) {
            return file;
        }

        File tmp = new File(root, file.getName() + ".tmp");
        String sourcePath = stripAssetsPrefix(override.sourceAssetPath);
        StringBuilder failures = new StringBuilder();

        File contentBundleRoot = new File(targetContext.getFilesDir(), "cb/active");
        File downloadedSource = ArcDarkFileOps.resolveInside(contentBundleRoot, sourcePath);
        if (downloadedSource.isFile()) {
            try (InputStream input = new FileInputStream(downloadedSource)) {
                ArcDarkFileOps.copy(input, tmp);
            }
            if (isVerified(tmp, override)) {
                replace(tmp, file);
                return file;
            }
            deleteTemporary(tmp);
            failures.append("downloaded content hash mismatch; ");
        } else {
            failures.append("downloaded content missing; ");
        }

        try (InputStream input = targetContext.getAssets().open(sourcePath)) {
            ArcDarkFileOps.copy(input, tmp);
            if (isVerified(tmp, override)) {
                replace(tmp, file);
                return file;
            }
            deleteTemporary(tmp);
            failures.append("APK asset hash mismatch");
        } catch (Exception error) {
            deleteTemporary(tmp);
            failures.append("APK asset unavailable: ").append(error.getClass().getSimpleName());
        }

        throw new IllegalStateException(
                "No verified official source for " + sourcePath + " (" + failures + ")"
        );
    }

    private static File materializeTransparent(File root, AssetOverride override) throws Exception {
        File file = new File(root, "transparent-" + override.width + "x" + override.height + ".png");
        if (file.isFile() && file.length() > 0) {
            return file;
        }

        File tmp = new File(root, file.getName() + ".tmp");
        TransparentPng.write(tmp, override.width, override.height);
        if (!tmp.isFile() || tmp.length() == 0) {
            throw new IllegalStateException("Unable to generate " + file.getName());
        }
        replace(tmp, file);
        return file;
    }

    private static boolean isVerified(File file, AssetOverride override) throws Exception {
        return file.isFile()
                && file.length() == override.size
                && override.sha256.equals(ArcDarkFileOps.sha256(file));
    }

    private static void deleteTemporary(File file) {
        if (file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }

    private static void replace(File tmp, File file) {
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("Unable to replace " + file);
        }
        if (!tmp.renameTo(file)) {
            throw new IllegalStateException("Unable to move " + tmp + " to " + file);
        }
    }

    private static String stripAssetsPrefix(String assetPath) {
        return assetPath.startsWith("assets/") ? assetPath.substring("assets/".length()) : assetPath;
    }
}
