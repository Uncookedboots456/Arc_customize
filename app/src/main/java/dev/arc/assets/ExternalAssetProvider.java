package dev.arc.assets;

import java.io.File;
import java.io.InputStream;

final class ExternalAssetProvider implements AssetProvider {
    @Override
    public boolean has(String assetPath) {
        return false;
    }

    @Override
    public InputStream open(String assetPath) {
        throw new UnsupportedOperationException("External assets are reserved for a later version.");
    }

    @Override
    public File materialize(String assetPath) {
        throw new UnsupportedOperationException("External assets are reserved for a later version.");
    }
}
