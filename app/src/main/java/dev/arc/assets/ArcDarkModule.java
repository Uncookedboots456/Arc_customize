package dev.arc.assets;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class ArcDarkModule implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    private static String modulePath;

    @Override
    public void initZygote(StartupParam startupParam) {
        modulePath = startupParam.modulePath;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!ArcDarkConstants.TARGET_PACKAGE.equals(lpparam.packageName)) {
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
                    }
                }
        );
        XposedHelpers.findAndHookMethod(
                Activity.class,
                "onCreate",
                Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Activity activity = (Activity) param.thisObject;
                        XposedBridge.log("ArcDark: Activity.onCreate called, activity="
                                + activity.getClass().getName());
                        ArcDarkRuntimeInstaller.installOnce(modulePath, activity, activity.getIntent());
                    }
                }
        );
    }
}
