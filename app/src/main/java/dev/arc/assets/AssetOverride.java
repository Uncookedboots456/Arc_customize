package dev.arc.assets;

final class AssetOverride {
    static final String MODE_BUNDLED = "bundled";
    static final String MODE_ALIAS = "alias";
    static final String MODE_TRANSPARENT = "transparent";
    static final String MODE_PASSTHROUGH = "passthrough";

    final String assetPath;
    final String mode;
    final String modulePath;
    final String sourceAssetPath;
    final long size;
    final String sha256;
    final int width;
    final int height;
    final boolean materialize;

    AssetOverride(String assetPath, String modulePath, long size, String sha256, boolean materialize) {
        this(
                assetPath,
                MODE_BUNDLED,
                modulePath,
                "",
                size,
                sha256,
                0,
                0,
                materialize
        );
    }

    AssetOverride(
            String assetPath,
            String mode,
            String modulePath,
            String sourceAssetPath,
            long size,
            String sha256,
            int width,
            int height,
            boolean materialize
    ) {
        this.assetPath = assetPath;
        this.mode = mode;
        this.modulePath = modulePath;
        this.sourceAssetPath = sourceAssetPath;
        this.size = size;
        this.sha256 = sha256;
        this.width = width;
        this.height = height;
        this.materialize = materialize;
    }

    boolean isBundled() {
        return MODE_BUNDLED.equals(mode);
    }

    boolean isIndexed() {
        return !isBundled();
    }
}
