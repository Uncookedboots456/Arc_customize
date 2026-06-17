package dev.arc.assets;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XposedBridge;

final class ArcDarkRuntimeControl {
    static final String EXTRA_INJECTION_ENABLED = "dev.arc.assets.extra.INJECTION_ENABLED";
    static final String EXTRA_ACTIVE_PACK_ID = "dev.arc.assets.extra.ACTIVE_PACK_ID";
    static final String EXTRA_ACTIVE_PACK_ORDER = "dev.arc.assets.extra.ACTIVE_PACK_ORDER";
    static final String EXTRA_IMPORT_PACK_URI = "dev.arc.assets.extra.IMPORT_PACK_URI";

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
                        + ", activeOrder="
                        + fromIntent.activePackOrder
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
                    + ", activeOrder="
                    + control.activePackOrder
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
        if (intent == null || !intent.hasExtra(EXTRA_INJECTION_ENABLED)) {
            return null;
        }
        List<String> order = readPackOrderExtra(intent);
        if (order == null && intent.hasExtra(EXTRA_ACTIVE_PACK_ID)) {
            order = new ArrayList<>();
            order.add(intent.getStringExtra(EXTRA_ACTIVE_PACK_ID));
        }
        if (order == null) {
            return null;
        }
        return new ArcDarkControl.Control(
                intent.getBooleanExtra(EXTRA_INJECTION_ENABLED, true),
                order
        );
    }

    static Uri readImportUri(Intent intent) {
        if (intent == null || !intent.hasExtra(EXTRA_IMPORT_PACK_URI)) {
            return null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(EXTRA_IMPORT_PACK_URI, Uri.class);
        }
        @SuppressWarnings("deprecation")
        Uri uri = intent.getParcelableExtra(EXTRA_IMPORT_PACK_URI);
        return uri;
    }

    private static List<String> readPackOrderExtra(Intent intent) {
        String[] array = intent.getStringArrayExtra(EXTRA_ACTIVE_PACK_ORDER);
        if (array == null) {
            return null;
        }
        List<String> order = new ArrayList<>(array.length);
        for (String packId : array) {
            order.add(packId);
        }
        return order;
    }
}
