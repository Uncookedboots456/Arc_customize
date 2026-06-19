package dev.arc.assets;

import android.content.Context;
import android.os.Environment;

import java.io.File;

@SuppressWarnings("deprecation")
final class ArcDarkPaths {
    private ArcDarkPaths() {
    }

    static File targetRoot(Context targetContext) {
        File[] mediaDirs = targetContext.getExternalMediaDirs();
        if (mediaDirs != null) {
            for (File dir : mediaDirs) {
                if (dir != null) {
                    return new File(dir, ArcDarkConstants.ROOT_DIR_NAME);
                }
            }
        }

        File fallback = targetContext.getExternalFilesDir(null);
        if (fallback != null) {
            return new File(fallback, ArcDarkConstants.ROOT_DIR_NAME);
        }
        return new File(targetContext.getCacheDir(), ArcDarkConstants.ROOT_DIR_NAME);
    }

    static File estimatedTargetRoot() {
        return new File(
                Environment.getExternalStorageDirectory(),
                "Android/media/" + ArcDarkConstants.TARGET_PACKAGE + "/" + ArcDarkConstants.ROOT_DIR_NAME
        );
    }

    static File packsDir(File root) {
        return new File(root, ArcDarkConstants.PACKS_DIR_NAME);
    }

    static File packDir(File root, String packId) {
        File packsDir = packsDir(root);
        try {
            File canonicalPacksDir = packsDir.getCanonicalFile();
            File canonicalPackDir = new File(canonicalPacksDir, packId).getCanonicalFile();
            String packsPath = canonicalPacksDir.getPath();
            String packPath = canonicalPackDir.getPath();
            if (packPath.equals(packsPath) || !packPath.startsWith(packsPath + File.separator)) {
                throw new IllegalStateException("Pack path escapes packs root: " + packId);
            }
            return canonicalPackDir;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unable to resolve pack path: " + packId, exception);
        }
    }

    static String displayPath(File file) {
        return file == null ? "Unavailable" : file.getAbsolutePath();
    }
}
