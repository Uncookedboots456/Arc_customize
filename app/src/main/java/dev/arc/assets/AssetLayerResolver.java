package dev.arc.assets;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XposedBridge;

final class AssetLayerResolver {
    private AssetLayerResolver() {
    }

    static ActiveAssetLayers resolve(
            AssetManager moduleAssets,
            Context targetContext,
            File root,
            AssetOverrideIndex bundledIndex,
            ArcDarkControl.Control control
    ) {
        List<AssetProvider> providers = new ArrayList<>();
        List<AssetOverrideIndex> indexes = new ArrayList<>();
        List<String> activeOrder = new ArrayList<>();
        BundledAssetProvider bundled = new BundledAssetProvider(moduleAssets, targetContext, bundledIndex);

        for (String packId : control.activePackOrder) {
            if (ArcDarkConstants.DEFAULT_PACK_ID.equals(packId)) {
                providers.add(bundled);
                indexes.add(bundledIndex);
                activeOrder.add(packId);
                XposedBridge.log("ArcDark: using bundled default layer");
                continue;
            }

            if (ArcDarkControl.isBuiltInPackId(packId)) {
                resolveBuiltInLayer(moduleAssets, targetContext, providers, indexes, activeOrder, packId);
                continue;
            }

            resolveImportedLayer(root, providers, indexes, activeOrder, packId);
        }

        AssetProvider activeProvider = providers.size() == 1
                ? providers.get(0)
                : new CompositeAssetProvider(providers.toArray(new AssetProvider[0]));
        AssetOverrideIndex activeIndex = AssetOverrideIndex.merge(indexes);
        return new ActiveAssetLayers(activeProvider, activeIndex, activeOrder);
    }

    private static void resolveImportedLayer(
            File root,
            List<AssetProvider> providers,
            List<AssetOverrideIndex> indexes,
            List<String> activeOrder,
            String packId
    ) {
        try {
            File packDir = ArcDarkPaths.packDir(root, packId);
            AssetOverrideIndex packIndex = AssetOverrideIndex.load(
                    new File(packDir, "arc_overrides/index.json")
            );
            FilePackProvider filePackProvider = new FilePackProvider(packDir, packIndex);
            preflightProvider(filePackProvider, packIndex);
            providers.add(filePackProvider);
            indexes.add(packIndex);
            activeOrder.add(packId);
            XposedBridge.log("ArcDark: using imported layer " + packDir
                    + " assets=" + packIndex.size());
        } catch (Throwable throwable) {
            XposedBridge.log("ArcDark: unable to use imported layer " + packId);
            XposedBridge.log(throwable);
        }
    }

    private static void resolveBuiltInLayer(
            AssetManager moduleAssets,
            Context targetContext,
            List<AssetProvider> providers,
            List<AssetOverrideIndex> indexes,
            List<String> activeOrder,
            String packId
    ) {
        try {
            String assetRoot = builtInAssetRoot(packId);
            AssetOverrideIndex packIndex = AssetOverrideIndex.load(
                    moduleAssets,
                    assetRoot + "/arc_overrides/index.json"
            );
            ModulePackProvider modulePackProvider =
                    new ModulePackProvider(moduleAssets, targetContext, assetRoot, packIndex);
            preflightProvider(modulePackProvider, packIndex);
            providers.add(modulePackProvider);
            indexes.add(packIndex);
            activeOrder.add(packId);
            XposedBridge.log("ArcDark: using built-in layer " + packId
                    + " assets=" + packIndex.size());
        } catch (Throwable throwable) {
            XposedBridge.log("ArcDark: unable to use built-in layer " + packId);
            XposedBridge.log(throwable);
        }
    }

    private static String builtInAssetRoot(String packId) {
        if (ArcDarkConstants.PAIRUMU_DARK_PACK_ID.equals(packId)) {
            return ArcDarkConstants.BUILT_IN_PACK_ASSET_ROOT;
        }
        throw new IllegalArgumentException("Unknown built-in pack: " + packId);
    }

    private static void preflightProvider(AssetProvider provider, AssetOverrideIndex index) throws Exception {
        for (AssetOverride override : index.entries()) {
            provider.materialize(override.assetPath);
        }
    }
}
