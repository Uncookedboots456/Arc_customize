package dev.arc.assets;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String INDEX_ASSET = "arc_overrides/index.json";
    private static final String APPLY_NOTICE =
            "Use Open Arcaea to apply changes. Later restarts reuse the applied state.";
    private static final int REQUEST_IMPORT_ZIP = 2001;

    private TextView injectionStatusValue;
    private TextView currentPackValue;
    private TextView targetStatusValue;
    private TextView assetCountValue;
    private TextView rootPathValue;
    private TextView packSummaryValue;
    private TextView restartNoticeValue;
    private Switch injectionSwitch;
    private LinearLayout packList;
    private Button openTargetButton;
    private StatusSnapshot snapshot;
    private boolean binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ensureControlFile();
        setContentView(createContentView());
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT_ZIP || resultCode != RESULT_OK || data == null) {
            return;
        }

        Uri uri = data.getData();
        if (uri == null) {
            Toast.makeText(this, "Selected file is unavailable", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            PackManifest manifest = ImportedPackInstaller.readManifest(this, uri);
            ArcDarkControl.Control next = ArcDarkControl.readLocal(this).withPackAtFront(manifest.id);
            ArcDarkControl.writeLocal(this, next);
            refreshStatus();
            openTargetApp(uri);
        } catch (Exception exception) {
            Toast.makeText(this, "Invalid Arc Dark ZIP pack", Toast.LENGTH_SHORT).show();
        }
    }

    private View createContentView() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.parseColor("#0D1017"));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(22), dp(22), dp(22));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        root.addView(createHeader());

        boolean wide = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                || getResources().getConfiguration().screenWidthDp >= 700;
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        main.setBaselineAligned(false);
        root.addView(main, withTopMargin(dp(18)));

        View injectionPanel = createInjectionPanel();
        View packPanel = createPackPanel(wide);
        if (wide) {
            main.addView(injectionPanel, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.1f
            ));
            LinearLayout.LayoutParams packParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    0.9f
            );
            packParams.leftMargin = dp(14);
            main.addView(packPanel, packParams);
            main.post(() -> alignPanelHeights(injectionPanel, packPanel));
        } else {
            main.addView(injectionPanel);
            main.addView(packPanel, withTopMargin(dp(14)));
        }

        root.addView(createActionPanel(wide), withTopMargin(dp(14)));
        return scroll;
    }

    private View createHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView mark = new TextView(this);
        mark.setText("AD");
        mark.setTextColor(Color.parseColor("#F4F7FB"));
        mark.setTypeface(Typeface.DEFAULT_BOLD);
        mark.setTextSize(19);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(rounded(Color.parseColor("#182331"), Color.parseColor("#2C3E50"), dp(14)));
        header.addView(mark, new LinearLayout.LayoutParams(dp(56), dp(56)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(14), 0, 0, 0);

        TextView title = new TextView(this);
        title.setText("Arc Dark");
        title.setTextColor(Color.parseColor("#F4F7FB"));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(27);
        titleBox.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Asset pack control panel");
        subtitle.setTextColor(Color.parseColor("#9AA8B6"));
        subtitle.setTextSize(14);
        titleBox.addView(subtitle, withTopMargin(dp(2)));

        header.addView(titleBox, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));
        return header;
    }

    private View createInjectionPanel() {
        LinearLayout panel = panel();
        panel.addView(sectionTitle("Injection"));

        injectionSwitch = new Switch(this);
        injectionSwitch.setText("Enable injection");
        injectionSwitch.setTextColor(Color.parseColor("#F4F7FB"));
        injectionSwitch.setTextSize(16);
        injectionSwitch.setPadding(0, dp(8), 0, dp(6));
        injectionSwitch.setOnCheckedChangeListener(this::onInjectionChanged);
        panel.addView(injectionSwitch);

        injectionStatusValue = addStatusRow(panel, "State");
        currentPackValue = addStatusRow(panel, "Pack order");
        targetStatusValue = addStatusRow(panel, "Target app");
        assetCountValue = addStatusRow(panel, "Bundled");

        rootPathValue = bodyText();
        panel.addView(label("Runtime path"), withTopMargin(dp(12)));
        panel.addView(rootPathValue, withTopMargin(dp(4)));

        restartNoticeValue = bodyText();
        restartNoticeValue.setTextColor(Color.parseColor("#FFCF7A"));
        panel.addView(restartNoticeValue, withTopMargin(dp(12)));
        return panel;
    }

    private View createPackPanel(boolean wide) {
        LinearLayout panel = panel();
        panel.addView(sectionTitle("Material packs"));

        ScrollView listScroll = new ScrollView(this);
        listScroll.setVerticalScrollBarEnabled(true);
        listScroll.setScrollbarFadingEnabled(false);
        listScroll.setFillViewport(false);
        packList = new LinearLayout(this);
        packList.setOrientation(LinearLayout.VERTICAL);
        listScroll.addView(packList, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                wide ? 0 : dp(220),
                wide ? 1f : 0f
        );
        listParams.topMargin = dp(10);
        panel.addView(listScroll, listParams);

        packSummaryValue = bodyText();
        panel.addView(label("Status"), withTopMargin(dp(12)));
        panel.addView(packSummaryValue, withTopMargin(dp(4)));
        return panel;
    }

    private View createActionPanel(boolean wide) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        Button refresh = createActionButton("Refresh", view -> refreshStatus());
        Button importZip = createActionButton("Import ZIP", view -> openImportPicker());
        Button copy = createActionButton("Copy diagnostics", this::copyDiagnostics);
        openTargetButton = createActionButton("Open Arcaea", view -> openTargetApp(null));

        if (wide) {
            panel.addView(refresh, weightedButton());
            panel.addView(importZip, weightedButtonWithLeftMargin());
            panel.addView(copy, weightedButtonWithLeftMargin());
            panel.addView(openTargetButton, weightedButtonWithLeftMargin());
        } else {
            panel.addView(refresh);
            panel.addView(importZip, withTopMargin(dp(10)));
            panel.addView(copy, withTopMargin(dp(10)));
            panel.addView(openTargetButton, withTopMargin(dp(10)));
        }
        return panel;
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));
        panel.setBackground(rounded(Color.parseColor("#151B24"), Color.parseColor("#273242"), dp(8)));
        return panel;
    }

    private View createPackRow(PackCatalog.Entry entry, int activeIndex, int activeCount) {
        boolean checked = activeIndex >= 0;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(rounded(Color.parseColor("#121923"), Color.parseColor("#243344"), dp(7)));

        CheckBox checkBox = new CheckBox(this);
        checkBox.setText(entry.name + "\n" + entry.description);
        checkBox.setTextColor(checked ? Color.parseColor("#F4F7FB") : Color.parseColor("#C8D4DF"));
        checkBox.setTextSize(13);
        checkBox.setLineSpacing(dp(2), 1f);
        checkBox.setChecked(checked);
        checkBox.setEnabled(checked || entry.available || ArcDarkConstants.TEST_PACK_ID.equals(entry.id));
        checkBox.setOnCheckedChangeListener((button, enabled) -> onPackEnabledChanged(entry.id, enabled));
        row.addView(checkBox);

        if (checked) {
            LinearLayout controls = new LinearLayout(this);
            controls.setOrientation(LinearLayout.HORIZONTAL);
            controls.setGravity(Gravity.END);

            Button up = smallButton("Up", view -> movePack(entry.id, -1));
            up.setEnabled(activeIndex > 0);
            up.setAlpha(activeIndex > 0 ? 1f : 0.45f);
            Button down = smallButton("Down", view -> movePack(entry.id, 1));
            down.setEnabled(activeIndex < activeCount - 1);
            down.setAlpha(activeIndex < activeCount - 1 ? 1f : 0.45f);
            controls.addView(up, smallButtonParams());
            LinearLayout.LayoutParams downParams = smallButtonParams();
            downParams.leftMargin = dp(8);
            controls.addView(down, downParams);
            row.addView(controls, withTopMargin(dp(6)));
        }

        return row;
    }

    private TextView addStatusRow(LinearLayout panel, String labelText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView label = label(labelText);
        row.addView(label, new LinearLayout.LayoutParams(dp(110), LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView value = valueText();
        row.addView(value, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        panel.addView(row, withTopMargin(dp(10)));
        return value;
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(Color.parseColor("#66E2D5"));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(14);
        return title;
    }

    private TextView label(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(Color.parseColor("#91A0AE"));
        label.setTextSize(13);
        return label;
    }

    private TextView valueText() {
        TextView value = new TextView(this);
        value.setTextColor(Color.parseColor("#F4F7FB"));
        value.setTypeface(Typeface.DEFAULT_BOLD);
        value.setTextSize(14);
        value.setGravity(Gravity.END);
        value.setLineSpacing(dp(2), 1f);
        return value;
    }

    private TextView bodyText() {
        TextView text = new TextView(this);
        text.setTextColor(Color.parseColor("#C8D4DF"));
        text.setTextSize(13);
        text.setLineSpacing(dp(2), 1f);
        return text;
    }

    private Button createActionButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(14);
        button.setTextColor(Color.parseColor("#F4F7FB"));
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(46));
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(rounded(Color.parseColor("#1D2A38"), Color.parseColor("#344658"), dp(7)));
        button.setOnClickListener(listener);
        return button;
    }

    private Button smallButton(String text, View.OnClickListener listener) {
        Button button = createActionButton(text, listener);
        button.setTextSize(12);
        button.setMinHeight(dp(34));
        button.setPadding(dp(10), 0, dp(10), 0);
        return button;
    }

    private void refreshStatus() {
        snapshot = StatusSnapshot.capture(this);
        binding = true;
        injectionSwitch.setChecked(snapshot.control.injectionEnabled);
        binding = false;

        injectionStatusValue.setText(snapshot.control.injectionEnabled ? "Enabled" : "Disabled");
        injectionStatusValue.setTextColor(snapshot.control.injectionEnabled
                ? Color.parseColor("#80E6A2")
                : Color.parseColor("#FFB87A"));
        currentPackValue.setText(formatPackOrder(snapshot.control.activePackOrder));
        targetStatusValue.setText(snapshot.targetInstalled ? "Installed" : "Not installed");
        targetStatusValue.setTextColor(snapshot.targetInstalled
                ? Color.parseColor("#80E6A2")
                : Color.parseColor("#FFB87A"));
        assetCountValue.setText(snapshot.overrideCount);
        rootPathValue.setText(snapshot.targetRootPath);
        packSummaryValue.setText(snapshot.packSummary);
        restartNoticeValue.setText(APPLY_NOTICE);
        openTargetButton.setEnabled(snapshot.targetInstalled);
        openTargetButton.setAlpha(snapshot.targetInstalled ? 1f : 0.55f);
        rebuildPackList();
    }

    private void rebuildPackList() {
        packList.removeAllViews();
        int activeCount = snapshot.control.activePackOrder.size();
        for (PackCatalog.Entry entry : displayPacksInOrder()) {
            int activeIndex = snapshot.control.activePackOrder.indexOf(entry.id);
            LinearLayout.LayoutParams params = withTopMargin(packList.getChildCount() == 0 ? 0 : dp(8));
            packList.addView(createPackRow(entry, activeIndex, activeCount), params);
        }
    }

    private List<PackCatalog.Entry> displayPacksInOrder() {
        List<PackCatalog.Entry> ordered = new ArrayList<>();
        for (String packId : snapshot.control.activePackOrder) {
            PackCatalog.Entry entry = findPackEntry(packId);
            if (entry != null) {
                ordered.add(entry);
            }
        }
        for (PackCatalog.Entry entry : snapshot.packs) {
            if (snapshot.control.activePackOrder.indexOf(entry.id) < 0) {
                ordered.add(entry);
            }
        }
        return ordered;
    }

    private PackCatalog.Entry findPackEntry(String packId) {
        for (PackCatalog.Entry entry : snapshot.packs) {
            if (entry.id.equals(packId)) {
                return entry;
            }
        }
        return null;
    }

    private void onInjectionChanged(CompoundButton button, boolean enabled) {
        if (binding) {
            return;
        }
        saveControl(ArcDarkControl.readLocal(this).withInjectionEnabled(enabled));
    }

    private void onPackEnabledChanged(String packId, boolean enabled) {
        if (binding || snapshot == null) {
            return;
        }
        List<String> order = new ArrayList<>(snapshot.control.activePackOrder);
        if (enabled) {
            if (!order.contains(packId)) {
                order.add(packId);
            }
        } else {
            order.remove(packId);
        }
        saveControl(snapshot.control.withActivePackOrder(order));
    }

    private void movePack(String packId, int direction) {
        if (snapshot == null) {
            return;
        }
        List<String> order = new ArrayList<>(snapshot.control.activePackOrder);
        int index = order.indexOf(packId);
        int nextIndex = index + direction;
        if (index < 0 || nextIndex < 0 || nextIndex >= order.size()) {
            return;
        }
        String moving = order.remove(index);
        order.add(nextIndex, moving);
        saveControl(snapshot.control.withActivePackOrder(order));
    }

    private void saveControl(ArcDarkControl.Control control) {
        try {
            ArcDarkControl.writeLocal(this, control);
            Toast.makeText(this, "Saved. Fully close Arcaea, then use Open Arcaea here.", Toast.LENGTH_SHORT).show();
        } catch (Exception exception) {
            Toast.makeText(this, "Unable to save control state", Toast.LENGTH_SHORT).show();
        }
        refreshStatus();
    }

    private void ensureControlFile() {
        File file = ArcDarkControl.controlFile(this);
        if (file.isFile()) {
            return;
        }
        try {
            ArcDarkControl.writeLocal(this, ArcDarkControl.defaults());
        } catch (Exception ignored) {
            // The UI can still run with in-memory defaults.
        }
    }

    private void openImportPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/zip",
                "application/octet-stream",
                "application/x-zip-compressed"
        });
        startActivityForResult(intent, REQUEST_IMPORT_ZIP);
    }

    private void copyDiagnostics(View ignored) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || snapshot == null) {
            Toast.makeText(this, "Diagnostics unavailable", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Arc Dark diagnostics", snapshot.diagnostics));
        Toast.makeText(this, "Diagnostics copied", Toast.LENGTH_SHORT).show();
    }

    private void openTargetApp(Uri importUri) {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(ArcDarkConstants.TARGET_PACKAGE);
        if (launchIntent == null) {
            Toast.makeText(this, "Arcaea is not installed", Toast.LENGTH_SHORT).show();
            return;
        }
        ArcDarkControl.Control control = ArcDarkControl.readLocal(this);
        launchIntent.putExtra(ArcDarkRuntimeControl.EXTRA_INJECTION_ENABLED, control.injectionEnabled);
        launchIntent.putExtra(ArcDarkRuntimeControl.EXTRA_ACTIVE_PACK_ID, control.primaryPackId());
        launchIntent.putExtra(
                ArcDarkRuntimeControl.EXTRA_ACTIVE_PACK_ORDER,
                control.activePackOrder.toArray(new String[0])
        );
        if (importUri != null) {
            launchIntent.putExtra(ArcDarkRuntimeControl.EXTRA_IMPORT_PACK_URI, importUri);
            launchIntent.setClipData(ClipData.newUri(getContentResolver(), "Arc Dark ZIP pack", importUri));
            launchIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        startActivity(launchIntent);
    }

    private void alignPanelHeights(View left, View right) {
        int height = left.getHeight();
        if (height <= 0) {
            return;
        }
        android.view.ViewGroup.LayoutParams params = right.getLayoutParams();
        if (params != null && params.height != height) {
            params.height = height;
            right.setLayoutParams(params);
        }
    }

    private String formatPackOrder(List<String> packOrder) {
        if (packOrder.isEmpty()) {
            return "Original";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < packOrder.size(); i++) {
            if (i > 0) {
                builder.append(" > ");
            }
            builder.append(packOrder.get(i));
        }
        return builder.toString();
    }

    private GradientDrawable rounded(int fillColor, int strokeColor, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(radius);
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams withTopMargin(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = topMargin;
        return params;
    }

    private LinearLayout.LayoutParams weightedButton() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    private LinearLayout.LayoutParams weightedButtonWithLeftMargin() {
        LinearLayout.LayoutParams params = weightedButton();
        params.leftMargin = dp(10);
        return params;
    }

    private LinearLayout.LayoutParams smallButtonParams() {
        return new LinearLayout.LayoutParams(dp(82), LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class StatusSnapshot {
        final ArcDarkControl.Control control;
        final String overrideCount;
        final boolean targetInstalled;
        final String targetRootPath;
        final List<PackCatalog.Entry> packs;
        final String packSummary;
        final String diagnostics;

        private StatusSnapshot(
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

        static StatusSnapshot capture(MainActivity activity) {
            ArcDarkControl.Control control = ArcDarkControl.readLocal(activity);
            String moduleVersion = readModuleVersion(activity);
            String overrideCount = readOverrideCount(activity);
            boolean targetInstalled = isPackageInstalled(activity, ArcDarkConstants.TARGET_PACKAGE);
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
                    + "\nModule package: " + activity.getPackageName()
                    + "\nModule version: " + moduleVersion
                    + "\nTarget package: " + ArcDarkConstants.TARGET_PACKAGE
                    + "\nTarget installed: " + targetInstalled
                    + "\nInjection enabled: " + control.injectionEnabled
                    + "\nActive pack order: " + activeOrder
                    + "\nBundled overrides: " + overrideCount
                    + "\nKnown packs: " + packs.size()
                    + "\nControl file: " + ArcDarkControl.controlFile(activity).getAbsolutePath()
                    + "\nTarget control: " + new File(targetRoot, ArcDarkConstants.CONTROL_FILE_NAME).getAbsolutePath()
                    + "\nTarget root: " + targetRoot.getAbsolutePath();
            return new StatusSnapshot(
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
                            "Not detected at the estimated runtime path",
                            false,
                            false
                    ));
                }
            }
        }

        @SuppressWarnings("deprecation")
        private static String readModuleVersion(MainActivity activity) {
            try {
                PackageInfo info = getPackageInfo(activity, activity.getPackageName());
                long code = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                        ? info.getLongVersionCode()
                        : info.versionCode;
                return info.versionName + " (" + code + ")";
            } catch (PackageManager.NameNotFoundException exception) {
                return "Unavailable";
            }
        }

        private static String readOverrideCount(MainActivity activity) {
            try {
                JSONObject root = new JSONObject(readUtf8(activity.getAssets().open(INDEX_ASSET)));
                JSONArray entries = root.getJSONArray("entries");
                return String.valueOf(entries.length());
            } catch (Exception exception) {
                return "Unavailable";
            }
        }

        private static boolean isPackageInstalled(MainActivity activity, String packageName) {
            try {
                getPackageInfo(activity, packageName);
                return true;
            } catch (PackageManager.NameNotFoundException exception) {
                return false;
            }
        }

        @SuppressWarnings("deprecation")
        private static PackageInfo getPackageInfo(MainActivity activity, String packageName)
                throws PackageManager.NameNotFoundException {
            PackageManager manager = activity.getPackageManager();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return manager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0));
            }
            return manager.getPackageInfo(packageName, 0);
        }

        private static String readUtf8(InputStream input) throws Exception {
            try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        }
    }
}
