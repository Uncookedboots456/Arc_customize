package dev.arc.assets;

import android.content.Context;

import java.io.File;
import java.io.InputStream;

final class OfficialCoverMaterializer {
    private static final String SOURCE_ASSET = "img/default_jacket_256.jpg";
    private static final long SOURCE_SIZE = 47667L;
    private static final String SOURCE_SHA256 =
            "f8c48cce52c51ed2f0017cd8ef5972933c34c17a274d8722073ec62df9f132fc";

    private OfficialCoverMaterializer() {
    }

    static synchronized File materialize(Context moduleContext) {
        File root = new File(moduleContext.getCacheDir(), "official_pack_covers");
        File cover = new File(root, SOURCE_SHA256 + ".jpg");
        try {
            if (isVerified(cover)) {
                return cover;
            }
            if (!root.isDirectory() && !root.mkdirs()) {
                return null;
            }

            Context targetContext = moduleContext.createPackageContext(
                    ArcDarkConstants.TARGET_PACKAGE,
                    Context.CONTEXT_IGNORE_SECURITY
            );
            File tmp = new File(root, cover.getName() + ".tmp");
            try (InputStream input = targetContext.getAssets().open(SOURCE_ASSET)) {
                ArcDarkFileOps.copy(input, tmp);
            }
            if (!isVerified(tmp)) {
                deleteTemporary(tmp);
                return null;
            }
            if (cover.exists() && !cover.delete()) {
                deleteTemporary(tmp);
                return null;
            }
            if (!tmp.renameTo(cover)) {
                deleteTemporary(tmp);
                return null;
            }
            return cover;
        } catch (Exception error) {
            return null;
        }
    }

    private static boolean isVerified(File file) throws Exception {
        return file.isFile()
                && file.length() == SOURCE_SIZE
                && SOURCE_SHA256.equals(ArcDarkFileOps.sha256(file));
    }

    private static void deleteTemporary(File file) {
        if (file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }
}
