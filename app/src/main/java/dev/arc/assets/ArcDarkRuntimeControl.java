package dev.arc.assets;

import android.content.Context;
import android.content.Intent;

import java.io.File;

import de.robv.android.xposed.XposedBridge;

final class ArcDarkRuntimeControl {
    static final String EXTRA_INJECTION_ENABLED = "dev.arc.assets.extra.INJECTION_ENABLED";
    static final String EXTRA_ACTIVE_PACK_ID = "dev.arc.assets.extra.ACTIVE_PACK_ID";

    private ArcDarkRuntimeControl() {
    }

    static ArcDarkControl.Control read(Context targetContext, Intent launchIntent) {
        File controlFile = targetControlFile(targetContext);
        ArcDarkControl.Control fromIntent = readIntentControl(launchIntent);
        if (fromIntent != null) {
            try {
                ArcDarkControl.writeFile(controlFile, fromIntent);
                XposedBridge.log("ArcDark: applied launch control injection="
                        + fromIntent.injectionEnabled
                        + ", activePack="
                        + fromIntent.activePackId
                        + ", file="
                        + controlFile);
            } catch (Throwable throwable) {
                XposedBridge.log("ArcDark: unable to persist launch control to " + controlFile);
                XposedBridge.log(throwable);
            }
            return fromIntent;
        }

        try {
            ArcDarkControl.Control control = ArcDarkControl.readFile(controlFile);
            XposedBridge.log("ArcDark: control read from target media injection="
                    + control.injectionEnabled
                    + ", activePack="
                    + control.activePackId
                    + ", file="
                    + controlFile);
            return control;
        } catch (Throwable throwable) {
            XposedBridge.log("ArcDark: target media control unavailable; using defaults, file="
                    + controlFile);
            return ArcDarkControl.defaults();
        }
    }

    static File targetControlFile(Context targetContext) {
        return new File(ArcDarkPaths.targetRoot(targetContext), ArcDarkConstants.CONTROL_FILE_NAME);
    }

    private static ArcDarkControl.Control readIntentControl(Intent intent) {
        if (intent == null
                || !intent.hasExtra(EXTRA_INJECTION_ENABLED)
                || !intent.hasExtra(EXTRA_ACTIVE_PACK_ID)) {
            return null;
        }
        return new ArcDarkControl.Control(
                intent.getBooleanExtra(EXTRA_INJECTION_ENABLED, true),
                intent.getStringExtra(EXTRA_ACTIVE_PACK_ID)
        );
    }
}
