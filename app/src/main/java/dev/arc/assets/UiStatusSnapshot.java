package dev.arc.assets;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class UiStatusSnapshot {
    private static final String INDEX_ASSET = "arc_overrides/index.json";

    final ArcDarkControl.Control control;
    final String overrideCount;
    final boolean targetInstalled;
    final String targetRootPath;
    final List<PackCatalog.Entry> packs;
    final String packSummary;
    final String diagnostics;

    private UiStatusSnapshot(
            ArcDarkControl.Control control,
            String overrideCount,
            boolean targetInstalled,
            String targetRootPath,
            List<PackCatalog.Entry> packs,
            String packSummary,
            String diagnostics
    ) {
        this.control = control;
        this.overrideCount = overrideCount;
        this.targetInstalled = targetInstalled;
        this.targetRootPath = targetRootPath;
        this.packs = packs;
        this.packSummary = packSummary;
        this.diagnostics = diagnostics;
    }

    static UiStatusSnapshot capture(Context context) {
        ArcDarkControl.Control control = ArcDarkControl.readLocal(context);
        String moduleVersion = readModuleVersion(context);
        String overrideCount = readOverrideCount(context);
        boolean targetInstalled = isPackageInstalled(context, ArcDarkConstants.TARGET_PACKAGE);
        File targetRoot = ArcDarkPaths.estimatedTargetRoot();
        List<PackCatalog.Entry> packs = PackCatalog.list(targetRoot);
        appendMissingActivePacks(packs, control.activePackOrder);
        String checkedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        String activeOrder = control.activePackOrder.isEmpty()
                ? "Original"
                : control.activePackOrder.toString();
        int importedCount = 0;
        for (PackCatalog.Entry entry : packs) {
            if (!entry.builtIn) {
                importedCount++;
            }
        }
        String packSummary = "Enabled layers: " + control.activePackOrder.size()
                + "\nImported packs: " + importedCount
                + "\nTop layer: " + (control.activePackOrder.isEmpty()
                ? "Original game assets"
                : control.activePackOrder.get(0));
        String diagnostics = "Arc Dark diagnostics"
                + "\nChecked: " + checkedAt
                + "\nModule package: " + context.getPackageName()
                + "\nModule version: " + moduleVersion
                + "\nTarget package: " + ArcDarkConstants.TARGET_PACKAGE
                + "\nTarget installed: " + targetInstalled
                + "\nInjection enabled: " + control.injectionEnabled
                + "\nActive pack order: " + activeOrder
                + "\nDifference overrides: " + overrideCount
                + "\nKnown packs: " + packs.size()
                + "\nControl file: " + ArcDarkControl.controlFile(context).getAbsolutePath()
                + "\nTarget control: " + new File(targetRoot, ArcDarkConstants.CONTROL_FILE_NAME).getAbsolutePath()
                + "\nTarget root: " + targetRoot.getAbsolutePath();
        return new UiStatusSnapshot(
                control,
                overrideCount,
                targetInstalled,
                targetRoot.getAbsolutePath(),
                packs,
                packSummary,
                diagnostics
        );
    }

    private static void appendMissingActivePacks(List<PackCatalog.Entry> packs, List<String> activeOrder) {
        for (String packId : activeOrder) {
            boolean found = false;
            for (PackCatalog.Entry entry : packs) {
                if (entry.id.equals(packId)) {
                    found = true;
                    break;
                }
            }
            if (!found && ArcDarkControl.isAllowedPackId(packId)) {
                packs.add(new PackCatalog.Entry(
                        packId,
                        packId,
                        "",
                        "Not detected at the estimated runtime path",
                        "",
                        null,
                        false,
                        false
                ));
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static String readModuleVersion(Context context) {
        try {
            PackageInfo info = getPackageInfo(context, context.getPackageName());
            long code = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? info.getLongVersionCode()
                    : info.versionCode;
            return info.versionName + " (" + code + ")";
        } catch (PackageManager.NameNotFoundException exception) {
            return "Unavailable";
        }
    }

    private static String readOverrideCount(Context context) {
        try {
            JSONObject root = new JSONObject(ArcDarkFileOps.readUtf8(context.getAssets().open(INDEX_ASSET)));
            JSONArray entries = root.getJSONArray("entries");
            return String.valueOf(entries.length());
        } catch (Exception exception) {
            return "Unavailable";
        }
    }

    private static boolean isPackageInstalled(Context context, String packageName) {
        try {
            getPackageInfo(context, packageName);
            return true;
        } catch (PackageManager.NameNotFoundException exception) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private static PackageInfo getPackageInfo(Context context, String packageName)
            throws PackageManager.NameNotFoundException {
        PackageManager manager = context.getPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return manager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0));
        }
        return manager.getPackageInfo(packageName, 0);
    }
}
