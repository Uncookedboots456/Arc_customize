package dev.arc.assets;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

final class ArcDarkLauncher {
    private ArcDarkLauncher() {
    }

    static boolean openTargetApp(Activity activity, Uri importUri) {
        Intent launchIntent = activity.getPackageManager()
                .getLaunchIntentForPackage(ArcDarkConstants.TARGET_PACKAGE);
        if (launchIntent == null) {
            return false;
        }

        ArcDarkControl.Control control = ArcDarkControl.readLocal(activity);
        applyControlExtras(launchIntent, control);
        if (importUri != null) {
            applyImportUri(activity, launchIntent, importUri);
        }
        activity.startActivity(launchIntent);
        return true;
    }

    static void applyControlExtras(Intent launchIntent, ArcDarkControl.Control control) {
        launchIntent.putExtra(ArcDarkRuntimeControl.EXTRA_INJECTION_ENABLED, control.injectionEnabled);
        launchIntent.putExtra(ArcDarkRuntimeControl.EXTRA_ACTIVE_PACK_ID, control.primaryPackId());
        launchIntent.putExtra(
                ArcDarkRuntimeControl.EXTRA_ACTIVE_PACK_ORDER,
                control.activePackOrder.toArray(new String[0])
        );
    }

    private static void applyImportUri(Activity activity, Intent launchIntent, Uri importUri) {
        launchIntent.putExtra(ArcDarkRuntimeControl.EXTRA_IMPORT_PACK_URI, importUri);
        launchIntent.setClipData(ClipData.newUri(
                activity.getContentResolver(),
                "Arc Dark ZIP pack",
                importUri
        ));
        launchIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    }
}
