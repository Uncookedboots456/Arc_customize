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
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String INDEX_ASSET = "arc_overrides/index.json";
    private static final String APPLY_NOTICE =
            "Force stop or fully close Arcaea, then tap Open Arcaea here to apply changes. "
                    + "Later restarts reuse the applied state.";
    private static final int ID_DEFAULT_PACK = 1001;
    private static final int ID_TEST_PACK = 1002;

    private TextView injectionStatusValue;
    private TextView currentPackValue;
    private TextView targetStatusValue;
    private TextView assetCountValue;
    private TextView rootPathValue;
    private TextView testPackStatusValue;
    private TextView restartNoticeValue;
    private Switch injectionSwitch;
    private RadioGroup packGroup;
    private RadioButton defaultPackRadio;
    private RadioButton testPackRadio;
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
        root.addView(main, withTopMargin(dp(18)));

        if (wide) {
            main.addView(createInjectionPanel(), new LinearLayout.LayoutParams(
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
            main.addView(createPackPanel(), packParams);
        } else {
            main.addView(createInjectionPanel());
            main.addView(createPackPanel(), withTopMargin(dp(14)));
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
        currentPackValue = addStatusRow(panel, "Active pack");
        targetStatusValue = addStatusRow(panel, "Target app");
        assetCountValue = addStatusRow(panel, "Overrides");

        rootPathValue = bodyText();
        panel.addView(label("Runtime path"), withTopMargin(dp(12)));
        panel.addView(rootPathValue, withTopMargin(dp(4)));

        restartNoticeValue = bodyText();
        restartNoticeValue.setTextColor(Color.parseColor("#FFCF7A"));
        panel.addView(restartNoticeValue, withTopMargin(dp(12)));
        return panel;
    }

    private View createPackPanel() {
        LinearLayout panel = panel();
        panel.addView(sectionTitle("Material packs"));

        packGroup = new RadioGroup(this);
        packGroup.setOrientation(RadioGroup.VERTICAL);
        packGroup.setOnCheckedChangeListener(this::onPackChanged);

        defaultPackRadio = packRadio(ID_DEFAULT_PACK, ArcDarkConstants.DEFAULT_PACK_ID, "Bundled assets inside the module APK");
        testPackRadio = packRadio(ID_TEST_PACK, ArcDarkConstants.TEST_PACK_ID, "File pack generated under the target app media folder");
        packGroup.addView(defaultPackRadio);
        packGroup.addView(testPackRadio, withTopMargin(dp(8)));
        panel.addView(packGroup, withTopMargin(dp(10)));

        testPackStatusValue = bodyText();
        panel.addView(label("test_pkg"), withTopMargin(dp(14)));
        panel.addView(testPackStatusValue, withTopMargin(dp(4)));
        return panel;
    }

    private View createActionPanel(boolean wide) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        Button refresh = createActionButton("Refresh", view -> refreshStatus());
        Button copy = createActionButton("Copy diagnostics", this::copyDiagnostics);
        openTargetButton = createActionButton("Open Arcaea", view -> openTargetApp());

        if (wide) {
            panel.addView(refresh, weightedButton());
            panel.addView(copy, weightedButtonWithLeftMargin());
            panel.addView(openTargetButton, weightedButtonWithLeftMargin());
        } else {
            panel.addView(refresh);
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

    private RadioButton packRadio(int id, String title, String description) {
        RadioButton radio = new RadioButton(this);
        radio.setId(id);
        radio.setText(title + "\n" + description);
        radio.setTextColor(Color.parseColor("#DCE5EC"));
        radio.setTextSize(14);
        radio.setLineSpacing(dp(2), 1f);
        radio.setPadding(0, dp(4), 0, dp(4));
        return radio;
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

    private void refreshStatus() {
        snapshot = StatusSnapshot.capture(this);
        binding = true;
        injectionSwitch.setChecked(snapshot.control.injectionEnabled);
        packGroup.check(ArcDarkConstants.TEST_PACK_ID.equals(snapshot.control.activePackId)
                ? ID_TEST_PACK
                : ID_DEFAULT_PACK);
        binding = false;

        injectionStatusValue.setText(snapshot.control.injectionEnabled ? "Enabled" : "Disabled");
        injectionStatusValue.setTextColor(snapshot.control.injectionEnabled
                ? Color.parseColor("#80E6A2")
                : Color.parseColor("#FFB87A"));
        currentPackValue.setText(snapshot.control.activePackId);
        targetStatusValue.setText(snapshot.targetInstalled ? "Installed" : "Not installed");
        targetStatusValue.setTextColor(snapshot.targetInstalled
                ? Color.parseColor("#80E6A2")
                : Color.parseColor("#FFB87A"));
        assetCountValue.setText(snapshot.overrideCount);
        rootPathValue.setText(snapshot.targetRootPath);
        testPackStatusValue.setText(snapshot.testPackStatus);
        restartNoticeValue.setText(APPLY_NOTICE);
        openTargetButton.setEnabled(snapshot.targetInstalled);
        openTargetButton.setAlpha(snapshot.targetInstalled ? 1f : 0.55f);
    }

    private void onInjectionChanged(CompoundButton button, boolean enabled) {
        if (binding) {
            return;
        }
        saveControl(ArcDarkControl.readLocal(this).withInjectionEnabled(enabled));
    }

    private void onPackChanged(RadioGroup group, int checkedId) {
        if (binding) {
            return;
        }
        String packId = checkedId == ID_TEST_PACK
                ? ArcDarkConstants.TEST_PACK_ID
                : ArcDarkConstants.DEFAULT_PACK_ID;
        saveControl(ArcDarkControl.readLocal(this).withActivePackId(packId));
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

    private void copyDiagnostics(View ignored) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || snapshot == null) {
            Toast.makeText(this, "Diagnostics unavailable", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Arc Dark diagnostics", snapshot.diagnostics));
        Toast.makeText(this, "Diagnostics copied", Toast.LENGTH_SHORT).show();
    }

    private void openTargetApp() {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(ArcDarkConstants.TARGET_PACKAGE);
        if (launchIntent == null) {
            Toast.makeText(this, "Arcaea is not installed", Toast.LENGTH_SHORT).show();
            return;
        }
        ArcDarkControl.Control control = ArcDarkControl.readLocal(this);
        launchIntent.putExtra(ArcDarkRuntimeControl.EXTRA_INJECTION_ENABLED, control.injectionEnabled);
        launchIntent.putExtra(ArcDarkRuntimeControl.EXTRA_ACTIVE_PACK_ID, control.activePackId);
        startActivity(launchIntent);
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class StatusSnapshot {
        final ArcDarkControl.Control control;
        final String overrideCount;
        final boolean targetInstalled;
        final String targetRootPath;
        final String testPackStatus;
        final String diagnostics;

        private StatusSnapshot(
                ArcDarkControl.Control control,
                String overrideCount,
                boolean targetInstalled,
                String targetRootPath,
                String testPackStatus,
                String diagnostics
        ) {
            this.control = control;
            this.overrideCount = overrideCount;
            this.targetInstalled = targetInstalled;
            this.targetRootPath = targetRootPath;
            this.testPackStatus = testPackStatus;
            this.diagnostics = diagnostics;
        }

        static StatusSnapshot capture(MainActivity activity) {
            ArcDarkControl.Control control = ArcDarkControl.readLocal(activity);
            String moduleVersion = readModuleVersion(activity);
            String overrideCount = readOverrideCount(activity);
            boolean targetInstalled = isPackageInstalled(activity, ArcDarkConstants.TARGET_PACKAGE);
            File targetRoot = ArcDarkPaths.estimatedTargetRoot();
            File testPack = ArcDarkPaths.packDir(targetRoot, ArcDarkConstants.TEST_PACK_ID);
            String testPackStatus = new File(testPack, "pack.json").isFile()
                    ? "Detected at " + testPack.getAbsolutePath()
                    : "Generated by Arcaea on launch";
            String checkedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            String diagnostics = "Arc Dark diagnostics"
                    + "\nChecked: " + checkedAt
                    + "\nModule package: " + activity.getPackageName()
                    + "\nModule version: " + moduleVersion
                    + "\nTarget package: " + ArcDarkConstants.TARGET_PACKAGE
                    + "\nTarget installed: " + targetInstalled
                    + "\nInjection enabled: " + control.injectionEnabled
                    + "\nActive pack: " + control.activePackId
                    + "\nAsset overrides: " + overrideCount
                    + "\nControl file: " + ArcDarkControl.controlFile(activity).getAbsolutePath()
                    + "\nTarget control: " + new File(targetRoot, ArcDarkConstants.CONTROL_FILE_NAME).getAbsolutePath()
                    + "\nTarget root: " + targetRoot.getAbsolutePath();
            return new StatusSnapshot(
                    control,
                    overrideCount,
                    targetInstalled,
                    targetRoot.getAbsolutePath(),
                    testPackStatus,
                    diagnostics
            );
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
