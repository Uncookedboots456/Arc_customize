package dev.arc.assets;

final class AssetOverride {
    final String assetPath;
    final String modulePath;
    final long size;
    final String sha256;
    final boolean materialize;

    AssetOverride(String assetPath, String modulePath, long size, String sha256, boolean materialize) {
        this.assetPath = assetPath;
        this.modulePath = modulePath;
        this.size = size;
        this.sha256 = sha256;
        this.materialize = materialize;
    }
}
