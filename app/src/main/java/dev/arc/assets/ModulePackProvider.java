package dev.arc.assets;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.InputStream;

final class ModulePackProvider implements AssetProvider {
    private final AssetManager moduleAssets;
    private final Context targetContext;
    private final String assetRoot;
    private final AssetOverrideIndex index;

    ModulePackProvider(
            AssetManager moduleAssets,
            Context targetContext,
            String assetRoot,
            AssetOverrideIndex index
    ) {
        this.moduleAssets = moduleAssets;
        this.targetContext = targetContext;
        this.assetRoot = assetRoot;
        this.index = index;
    }

    @Override
    public boolean has(String assetPath) {
        return index.find(assetPath) != null;
    }

    @Override
    public InputStream open(String assetPath) throws Exception {
        AssetOverride override = require(assetPath);
        return moduleAssets.open(assetRoot + "/" + override.modulePath);
    }

    @Override
    public File materialize(String assetPath) throws Exception {
        AssetOverride override = require(assetPath);
        File root = new File(targetContext.getCacheDir(), "arc_dark_module_packs");
        File file = new File(root, override.sha256);

        if (file.isFile()
                && file.length() == override.size
                && override.sha256.equals(ArcDarkFileOps.sha256(file))) {
            return file;
        }

        if (!root.isDirectory() && !root.mkdirs()) {
            throw new IllegalStateException("Unable to create " + root);
        }

        File tmp = new File(root, override.sha256 + ".tmp");
        ArcDarkFileOps.copy(moduleAssets.open(assetRoot + "/" + override.modulePath), tmp);

        if (tmp.length() != override.size || !override.sha256.equals(ArcDarkFileOps.sha256(tmp))) {
            if (!tmp.delete()) {
                tmp.deleteOnExit();
            }
            throw new IllegalStateException("Materialized module pack asset failed verification: "
                    + override.assetPath);
        }

        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("Unable to replace " + file);
        }
        if (!tmp.renameTo(file)) {
            throw new IllegalStateException("Unable to move " + tmp + " to " + file);
        }
        return file;
    }

    private AssetOverride require(String assetPath) {
        AssetOverride override = index.find(assetPath);
        if (override == null) {
            throw new IllegalArgumentException("No module pack override for " + assetPath);
        }
        return override;
    }
}
