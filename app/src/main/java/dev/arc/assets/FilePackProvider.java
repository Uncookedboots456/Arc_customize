package dev.arc.assets;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

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
        if (!override.sha256.equals(sha256(file))) {
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
        File root = packDir.getCanonicalFile();
        File file = new File(root, override.modulePath).getCanonicalFile();
        String rootPath = root.getPath();
        String filePath = file.getPath();
        if (!filePath.equals(rootPath) && !filePath.startsWith(rootPath + File.separator)) {
            throw new IllegalStateException("Pack file escapes pack root: " + override.modulePath);
        }
        return file;
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
