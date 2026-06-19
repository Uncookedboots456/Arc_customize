package dev.arc.assets;

import java.util.List;

final class ActiveAssetLayers {
    final AssetProvider provider;
    final AssetOverrideIndex index;
    final List<String> packOrder;

    ActiveAssetLayers(AssetProvider provider, AssetOverrideIndex index, List<String> packOrder) {
        this.provider = provider;
        this.index = index;
        this.packOrder = packOrder;
    }
}
