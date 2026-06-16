package dev.arc.assets;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

final class CompositeAssetProvider implements AssetProvider {
    private final List<AssetProvider> providers;

    CompositeAssetProvider(AssetProvider... providers) {
        this.providers = Arrays.asList(providers);
    }

    @Override
    public boolean has(String assetPath) {
        return find(assetPath) != null;
    }

    @Override
    public InputStream open(String assetPath) throws Exception {
        return require(assetPath).open(assetPath);
    }

    @Override
    public File materialize(String assetPath) throws Exception {
        return require(assetPath).materialize(assetPath);
    }

    private AssetProvider require(String assetPath) {
        AssetProvider provider = find(assetPath);
        if (provider == null) {
            throw new IllegalArgumentException("No override for " + assetPath);
        }
        return provider;
    }

    private AssetProvider find(String assetPath) {
        for (AssetProvider provider : providers) {
            if (provider.has(assetPath)) {
                return provider;
            }
        }
        return null;
    }
}
