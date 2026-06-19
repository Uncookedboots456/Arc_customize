package dev.arc.assets;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

final class FilePackProvider implements AssetProvider {
    private final File packDir;
    private final AssetOverrideIndex index;

    FilePackProvider(File packDir, AssetOverrideIndex index) {
        this.packDir = packDir;
        this.index = index;
    }

    @Override
    public boolean has(String assetPath) {
        AssetOverride override = index.find(assetPath);
        if (override == null) {
            return false;
        }
        try {
            return resolve(override).isFile();
        } catch (Exception exception) {
            return false;
        }
    }

    @Override
    public InputStream open(String assetPath) throws Exception {
        return new FileInputStream(materialize(assetPath));
    }

    @Override
    public File materialize(String assetPath) throws Exception {
        AssetOverride override = require(assetPath);
        File file = resolve(override);
        if (!file.isFile()) {
            throw new IllegalStateException("Pack file missing: " + file);
        }
        if (file.length() != override.size) {
            throw new IllegalStateException("Pack file size mismatch: " + file);
        }
        if (!override.sha256.equals(ArcDarkFileOps.sha256(file))) {
            throw new IllegalStateException("Pack file checksum mismatch: " + file);
        }
        return file;
    }

    private AssetOverride require(String assetPath) {
        AssetOverride override = index.find(assetPath);
        if (override == null) {
            throw new IllegalArgumentException("No override for " + assetPath);
        }
        return override;
    }

    private File resolve(AssetOverride override) throws Exception {
        try {
            return ArcDarkFileOps.resolveInside(packDir, override.modulePath);
        } catch (IllegalStateException exception) {
            throw new IllegalStateException("Pack file escapes pack root: " + override.modulePath);
        }
    }
}
