package dev.arc.assets;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

final class ArcDarkRuntimeInstaller {
    private static boolean installed;
    private static AssetProvider provider;
    private static AssetManager moduleAssetManager;
    private static final ThreadLocal<Boolean> providerReadGuard = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };
    private static int javaHitLogs = 0;
    private static final int MAX_JAVA_HIT_LOGS = 50;

    private ArcDarkRuntimeInstaller() {
    }

    static synchronized void installOnce(String modulePath, Context targetContext, Intent intent) {
        if (installed) {
            return;
        }

        try {
            AssetManager moduleAssets = createModuleAssetManager(modulePath);
            moduleAssetManager = moduleAssets;

            AssetOverrideIndex index = AssetOverrideIndex.load(moduleAssets);
            XposedBridge.log("ArcDark: index loaded, count=" + index.size());

            File root = ArcDarkPaths.targetRoot(targetContext);
            ArcDarkControl.Control control = ArcDarkRuntimeControl.read(targetContext, intent);
            control = importPendingPackIfNeeded(targetContext, root, intent, control);
            XposedBridge.log("ArcDark: control injection="
                    + control.injectionEnabled
                    + ", activeOrder="
                    + control.activePackOrder
                    + ", root="
                    + root);

            if (!control.injectionEnabled) {
                TestPackSeeder.ensureTestPackAsync(moduleAssets, root, index);
                installed = true;
                XposedBridge.log("ArcDark: injection disabled by control; hooks not installed");
                return;
            }

            ActiveAssetLayers activeLayers =
                    AssetLayerResolver.resolve(moduleAssets, targetContext, root, index, control);
            if (activeLayers.index.size() == 0) {
                TestPackSeeder.ensureTestPackAsync(moduleAssets, root, index);
                installed = true;
                XposedBridge.log("ArcDark: no active asset overrides; original game assets remain in use");
                return;
            }

            provider = activeLayers.provider;
            XposedBridge.log("ArcDark: provider initialized for " + activeLayers.packOrder);

            attachModuleAssetPath(modulePath, targetContext);

            boolean nativeInstalled = NativeBridge.install(modulePath, activeLayers.provider, activeLayers.index);
            if (nativeInstalled) {
                NativeBridge.refreshHooks("initial");
                scheduleNativeRefreshes();
            } else {
                XposedBridge.log("ArcDark: native hook unavailable; Java AssetManager hooks remain active");
            }

            hookAssetOpen();
            XposedBridge.log("ArcDark: hook AssetManager.open(String) installed");
            hookAssetOpenWithMode();
            XposedBridge.log("ArcDark: hook AssetManager.open(String,int) installed");
            hookAssetOpenFd();
            XposedBridge.log("ArcDark: hook AssetManager.openFd(String) installed");

            installed = true;
            XposedBridge.log("ArcDark: installed Java hooks for " + activeLayers.index.size()
                    + " asset overrides for "
                    + ArcDarkConstants.TARGET_PACKAGE
                    + ", order="
                    + activeLayers.packOrder
                    + ", native="
                    + nativeInstalled);
        } catch (Throwable throwable) {
            XposedBridge.log("ArcDark: failed to install hooks");
            XposedBridge.log(throwable);
        }
    }

    private static ArcDarkControl.Control importPendingPackIfNeeded(
            Context targetContext,
            File root,
            Intent intent,
            ArcDarkControl.Control control
    ) {
        Uri importUri = ArcDarkRuntimeControl.readImportUri(intent);
        if (importUri == null) {
            return control;
        }

        try {
            ImportedPackInstaller.ImportResult result =
                    ImportedPackInstaller.install(targetContext, root, importUri);
            ArcDarkControl.Control imported = control.withPackAtFront(result.manifest.id);
            ArcDarkControl.writeFile(ArcDarkRuntimeControl.targetControlFile(targetContext), imported);
            XposedBridge.log("ArcDark: imported pack "
                    + result.manifest.id
                    + " assets="
                    + result.assetCount
                    + " dir="
                    + result.packDir);
            return imported;
        } catch (Throwable throwable) {
            XposedBridge.log("ArcDark: import pack failed");
            XposedBridge.log(throwable);
            return control;
        }
    }

    private static AssetManager createModuleAssetManager(String modulePath) throws Exception {
        if (modulePath == null || modulePath.length() == 0) {
            throw new IllegalStateException("Module path unavailable");
        }

        AssetManager assets = AssetManager.class.getDeclaredConstructor().newInstance();
        Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
        addAssetPath.setAccessible(true);
        Object result = addAssetPath.invoke(assets, modulePath);
        int cookie = result instanceof Integer ? (Integer) result : 0;
        if (cookie == 0) {
            throw new IllegalStateException("Unable to add module asset path " + modulePath);
        }
        XposedBridge.log("ArcDark: module assets opened from " + modulePath
                + " with cookie " + cookie);
        return assets;
    }

    private static void attachModuleAssetPath(String modulePath, Context targetContext) {
        if (modulePath == null || modulePath.length() == 0) {
            XposedBridge.log("ArcDark: module path unavailable; skipping AssetManager path attach");
            return;
        }

        try {
            Method addAssetPath = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
            addAssetPath.setAccessible(true);
            Object result = addAssetPath.invoke(targetContext.getAssets(), modulePath);
            XposedBridge.log("ArcDark: attached module asset path with cookie " + result);
        } catch (Throwable throwable) {
            XposedBridge.log("ArcDark: unable to attach module asset path");
            XposedBridge.log(throwable);
        }
    }

    private static void scheduleNativeRefreshes() {
        Handler handler = new Handler(Looper.getMainLooper());
        long[] delaysMs = new long[]{250L, 1000L, 3000L, 7000L};
        for (long delayMs : delaysMs) {
            final long scheduledDelayMs = delayMs;
            handler.postDelayed(
                    () -> NativeBridge.refreshHooks("delayed:" + scheduledDelayMs + "ms"),
                    scheduledDelayMs
            );
        }
    }

    private static void hookAssetOpen() {
        XposedHelpers.findAndHookMethod(
                AssetManager.class,
                "open",
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String path = (String) param.args[0];
                        if (shouldSkipAssetHook(param)) {
                            return;
                        }

                        AssetProvider active = provider;
                        if (active != null && active.has(path)) {
                            logJavaHit(path);
                            param.setResult(openProviderAsset(active, path));
                        }
                    }
                }
        );
    }

    private static void hookAssetOpenWithMode() {
        XposedHelpers.findAndHookMethod(
                AssetManager.class,
                "open",
                String.class,
                int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String path = (String) param.args[0];
                        if (shouldSkipAssetHook(param)) {
                            return;
                        }

                        AssetProvider active = provider;
                        if (active != null && active.has(path)) {
                            logJavaHit(path);
                            param.setResult(openProviderAsset(active, path));
                        }
                    }
                }
        );
    }

    private static void hookAssetOpenFd() {
        XposedHelpers.findAndHookMethod(
                AssetManager.class,
                "openFd",
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String path = (String) param.args[0];
                        if (shouldSkipAssetHook(param)) {
                            return;
                        }

                        AssetProvider active = provider;
                        if (active == null || !active.has(path)) {
                            return;
                        }

                        logJavaHit(path);
                        File file = materializeProviderAsset(active, path);
                        ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(
                                file,
                                ParcelFileDescriptor.MODE_READ_ONLY
                        );
                        param.setResult(new AssetFileDescriptor(descriptor, 0, file.length()));
                    }
                }
        );
    }

    private static boolean shouldSkipAssetHook(XC_MethodHook.MethodHookParam param) {
        if (Boolean.TRUE.equals(providerReadGuard.get())) {
            return true;
        }
        return param.thisObject == moduleAssetManager;
    }

    private static Object openProviderAsset(AssetProvider active, String path) throws Exception {
        providerReadGuard.set(true);
        try {
            return active.open(path);
        } finally {
            providerReadGuard.set(false);
        }
    }

    private static File materializeProviderAsset(AssetProvider active, String path) throws Exception {
        providerReadGuard.set(true);
        try {
            return active.materialize(path);
        } finally {
            providerReadGuard.set(false);
        }
    }

    private static void logJavaHit(String path) {
        if (javaHitLogs < MAX_JAVA_HIT_LOGS) {
            javaHitLogs++;
            XposedBridge.log("ArcDark: Java AssetManager hit #"
                    + javaHitLogs + ": " + path);
        }
    }
}
