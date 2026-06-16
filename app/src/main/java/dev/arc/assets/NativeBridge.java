package dev.arc.assets;

import android.os.Process;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XposedBridge;

final class NativeBridge {
    private static final String LIB_NAME = "arcdarkhook";

    private static boolean loaded;
    private static boolean installed;

    private NativeBridge() {
    }

    static synchronized void install(
            String modulePath,
            BundledAssetProvider bundledProvider,
            AssetOverrideIndex index
    ) {
        if (installed) {
            return;
        }

        try {
            loadLibrary(modulePath);

            List<String> assetPaths = new ArrayList<>(index.size());
            List<String> filePaths = new ArrayList<>(index.size());
            for (AssetOverride override : index.entries()) {
                File file = bundledProvider.materialize(override.assetPath);
                assetPaths.add(override.assetPath);
                filePaths.add(file.getAbsolutePath());
            }

            nativeInstall(
                    assetPaths.toArray(new String[0]),
                    filePaths.toArray(new String[0])
            );
            installed = true;
            XposedBridge.log("ArcDark: native install registered " + assetPaths.size() + " assets");
        } catch (Throwable throwable) {
            XposedBridge.log("ArcDark: native install failed");
            XposedBridge.log(throwable);
        }
    }

    static synchronized void refreshHooks(String reason) {
        if (!loaded) {
            return;
        }

        try {
            int result = nativeRefreshHooks();
            XposedBridge.log("ArcDark: native refresh(" + reason + ") result=" + result);
        } catch (Throwable throwable) {
            XposedBridge.log("ArcDark: native refresh failed after " + reason);
            XposedBridge.log(throwable);
        }
    }

    private static void loadLibrary(String modulePath) {
        if (loaded) {
            return;
        }

        File library = findExtractedLibrary(modulePath);
        System.load(library.getAbsolutePath());
        loaded = true;
        XposedBridge.log("ArcDark: loaded native hook library " + library);
    }

    private static File findExtractedLibrary(String modulePath) {
        if (modulePath == null || modulePath.length() == 0) {
            throw new IllegalStateException("Module path unavailable");
        }

        File apk = new File(modulePath);
        File appDir = apk.getParentFile();
        if (appDir == null) {
            throw new IllegalStateException("Unable to resolve module app dir from " + modulePath);
        }

        String mappedName = System.mapLibraryName(LIB_NAME);
        String[] preferredAbiDirs = Process.is64Bit()
                ? new String[]{"arm64", "arm64-v8a"}
                : new String[]{"arm", "armeabi-v7a"};

        for (String abiDir : preferredAbiDirs) {
            File library = new File(new File(new File(appDir, "lib"), abiDir), mappedName);
            if (library.isFile()) {
                return library;
            }
        }

        String[] fallbackAbiDirs = new String[]{"arm64", "arm64-v8a", "arm", "armeabi-v7a"};
        for (String abiDir : fallbackAbiDirs) {
            File library = new File(new File(new File(appDir, "lib"), abiDir), mappedName);
            if (library.isFile()) {
                return library;
            }
        }

        throw new IllegalStateException("Native hook library not found next to " + modulePath);
    }

    private static native void nativeInstall(String[] assetPaths, String[] filePaths);

    private static native int nativeRefreshHooks();
}
