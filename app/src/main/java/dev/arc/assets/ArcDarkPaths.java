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
        return new File(packsDir(root), packId);
    }

    static String displayPath(File file) {
        return file == null ? "Unavailable" : file.getAbsolutePath();
    }
}
