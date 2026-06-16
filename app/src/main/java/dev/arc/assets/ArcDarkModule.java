package dev.arc.assets;

import android.app.Application;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class ArcDarkModule implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    private static final String TARGET_PACKAGE = "moe.low.arc";

    private static String modulePath;
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

    @Override
    public void initZygote(StartupParam startupParam) {
        modulePath = startupParam.modulePath;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("ArcDark: handleLoadPackage entered, package="
                + lpparam.packageName
                + ", process="
                + lpparam.processName);

        XposedHelpers.findAndHookMethod(
                Application.class,
                "attach",
                Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Context targetContext = (Context) param.args[0];
                        XposedBridge.log("ArcDark: Application.attach called, context package="
                                + targetContext.getPackageName());
                        installOnce(targetContext);
                    }
                }
        );
    }

    private static synchronized void installOnce(Context targetContext) {
        if (installed) {
            return;
        }

        try {
            AssetManager moduleAssets = createModuleAssetManager();
            moduleAssetManager = moduleAssets;

            AssetOverrideIndex index = AssetOverrideIndex.load(moduleAssets);
            XposedBridge.log("ArcDark: index loaded, count=" + index.size());

            BundledAssetProvider bundled = new BundledAssetProvider(moduleAssets, targetContext, index);
            provider = new CompositeAssetProvider(new ExternalAssetProvider(), bundled);
            XposedBridge.log("ArcDark: provider initialized");

            attachModuleAssetPath(targetContext);

            NativeBridge.install(modulePath, bundled, index);
            NativeBridge.refreshHooks("initial");
            scheduleNativeRefreshes();

            hookAssetOpen();
            XposedBridge.log("ArcDark: hook AssetManager.open(String) installed");
            hookAssetOpenWithMode();
            XposedBridge.log("ArcDark: hook AssetManager.open(String,int) installed");
            hookAssetOpenFd();
            XposedBridge.log("ArcDark: hook AssetManager.openFd(String) installed");

            installed = true;
            XposedBridge.log("ArcDark: installed " + index.size() + " asset overrides for "
                    + TARGET_PACKAGE + " from " + modulePath);
        } catch (Throwable throwable) {
            XposedBridge.log("ArcDark: failed to install hooks");
            XposedBridge.log(throwable);
        }
    }

    private static AssetManager createModuleAssetManager() throws Exception {
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

    private static void attachModuleAssetPath(Context targetContext) {
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
