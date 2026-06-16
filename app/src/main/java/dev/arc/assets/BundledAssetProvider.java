package dev.arc.assets;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;

final class BundledAssetProvider implements AssetProvider {
    private final AssetManager moduleAssets;
    private final Context targetContext;
    private final AssetOverrideIndex index;

    BundledAssetProvider(AssetManager moduleAssets, Context targetContext, AssetOverrideIndex index) {
        this.moduleAssets = moduleAssets;
        this.targetContext = targetContext;
        this.index = index;
    }

    @Override
    public boolean has(String assetPath) {
        return index.find(assetPath) != null;
    }

    @Override
    public InputStream open(String assetPath) throws Exception {
        AssetOverride override = require(assetPath);
        return moduleAssets.open(override.modulePath);
    }

    @Override
    public File materialize(String assetPath) throws Exception {
        AssetOverride override = require(assetPath);
        File root = new File(targetContext.getCacheDir(), "arc_dark_assets");
        File file = new File(root, override.sha256);

        if (file.isFile() && file.length() == override.size && override.sha256.equals(sha256(file))) {
            return file;
        }

        if (!root.isDirectory() && !root.mkdirs()) {
            throw new IllegalStateException("Unable to create " + root);
        }

        File tmp = new File(root, override.sha256 + ".tmp");
        try (InputStream in = moduleAssets.open(override.modulePath);
             FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }

        if (tmp.length() != override.size || !override.sha256.equals(sha256(tmp))) {
            if (!tmp.delete()) {
                tmp.deleteOnExit();
            }
            throw new IllegalStateException("Materialized asset failed verification: " + override.assetPath);
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
            throw new IllegalArgumentException("No override for " + assetPath);
        }
        return override;
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }

        byte[] bytes = digest.digest();
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
