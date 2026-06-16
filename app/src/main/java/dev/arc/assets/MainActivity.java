package dev.arc.assets;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String TARGET_PACKAGE = "moe.low.arc";
    private static final String INDEX_ASSET = "arc_overrides/index.json";

    private TextView versionValue;
    private TextView overrideValue;
    private TextView targetValue;
    private TextView abiValue;
    private TextView diagnosticsValue;
    private Button openTargetButton;
    private StatusSnapshot snapshot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        root.setPadding(dp(22), dp(24), dp(22), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        root.addView(createHeader());
        root.addView(createStatusPanel(), withTopMargin(dp(20)));
        root.addView(createNotePanel(), withTopMargin(dp(14)));
        root.addView(createActionButton("Copy diagnostics", this::copyDiagnostics), withTopMargin(dp(18)));
        openTargetButton = createActionButton("Open Arcaea", view -> openTargetApp());
        root.addView(openTargetButton, withTopMargin(dp(10)));
        root.addView(createActionButton("Refresh status", view -> refreshStatus()), withTopMargin(dp(10)));
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
        mark.setTextSize(20);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(rounded(Color.parseColor("#182331"), Color.parseColor("#2C3E50"), dp(14)));
        header.addView(mark, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.setPadding(dp(14), 0, 0, 0);

        TextView title = new TextView(this);
        title.setText("Arc Dark");
        title.setTextColor(Color.parseColor("#F4F7FB"));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(28);
        titleBox.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("LSPosed asset override module");
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

    private View createStatusPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));
        panel.setBackground(rounded(Color.parseColor("#151B24"), Color.parseColor("#273242"), dp(8)));

        panel.addView(sectionTitle("Status"));
        versionValue = addStatusRow(panel, "Module version");
        addStaticRow(panel, "Module package", getPackageName());
        addStaticRow(panel, "LSPosed scope", TARGET_PACKAGE);
        overrideValue = addStatusRow(panel, "Asset overrides");
        targetValue = addStatusRow(panel, "Target app");
        abiValue = addStatusRow(panel, "Device ABI");
        return panel;
    }

    private View createNotePanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(15), dp(18), dp(15));
        panel.setBackground(rounded(Color.parseColor("#101820"), Color.parseColor("#244746"), dp(8)));

        TextView title = sectionTitle("LSPosed");
        panel.addView(title);

        TextView note = new TextView(this);
        note.setText("Enable this module for moe.low.arc. Restart Arcaea after changing scope or installing a new build.");
        note.setTextColor(Color.parseColor("#C8D4DF"));
        note.setTextSize(14);
        note.setLineSpacing(dp(2), 1f);
        panel.addView(note, withTopMargin(dp(8)));

        diagnosticsValue = new TextView(this);
        diagnosticsValue.setTextColor(Color.parseColor("#8FA0AF"));
        diagnosticsValue.setTextSize(12);
        diagnosticsValue.setLineSpacing(dp(2), 1f);
        panel.addView(diagnosticsValue, withTopMargin(dp(12)));
        return panel;
    }

    private TextView addStatusRow(LinearLayout panel, String label) {
        LinearLayout row = createRow(label);
        TextView value = rowValue();
        value.setText("Checking");
        row.addView(value, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));
        panel.addView(row, withTopMargin(dp(12)));
        return value;
    }

    private void addStaticRow(LinearLayout panel, String label, String valueText) {
        LinearLayout row = createRow(label);
        TextView value = rowValue();
        value.setText(valueText);
        row.addView(value, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));
        panel.addView(row, withTopMargin(dp(12)));
    }

    private LinearLayout createRow(String labelText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);

        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextColor(Color.parseColor("#91A0AE"));
        label.setTextSize(13);
        row.addView(label, new LinearLayout.LayoutParams(dp(126), LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private TextView rowValue() {
        TextView value = new TextView(this);
        value.setTextColor(Color.parseColor("#F4F7FB"));
        value.setTextSize(14);
        value.setGravity(Gravity.END);
        value.setLineSpacing(dp(2), 1f);
        return value;
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(Color.parseColor("#66E2D5"));
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(13);
        return title;
    }

    private Button createActionButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(15);
        button.setTextColor(Color.parseColor("#F4F7FB"));
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(48));
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setBackground(rounded(Color.parseColor("#1D2A38"), Color.parseColor("#344658"), dp(7)));
        button.setOnClickListener(listener);
        return button;
    }

    private void refreshStatus() {
        snapshot = StatusSnapshot.capture(this);
        versionValue.setText(snapshot.moduleVersion);
        overrideValue.setText(snapshot.overrideCount);
        targetValue.setText(snapshot.targetInstalled ? "Installed" : "Not installed");
        targetValue.setTextColor(snapshot.targetInstalled
                ? Color.parseColor("#80E6A2")
                : Color.parseColor("#FFB87A"));
        abiValue.setText(snapshot.abis);
        openTargetButton.setEnabled(snapshot.targetInstalled);
        openTargetButton.setAlpha(snapshot.targetInstalled ? 1f : 0.55f);
        diagnosticsValue.setText(snapshot.diagnostics);
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
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(TARGET_PACKAGE);
        if (launchIntent == null) {
            Toast.makeText(this, "Arcaea is not installed", Toast.LENGTH_SHORT).show();
            return;
        }
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class StatusSnapshot {
        final String moduleVersion;
        final String overrideCount;
        final boolean targetInstalled;
        final String abis;
        final String diagnostics;

        private StatusSnapshot(
                String moduleVersion,
                String overrideCount,
                boolean targetInstalled,
                String abis,
                String diagnostics
        ) {
            this.moduleVersion = moduleVersion;
            this.overrideCount = overrideCount;
            this.targetInstalled = targetInstalled;
            this.abis = abis;
            this.diagnostics = diagnostics;
        }

        static StatusSnapshot capture(MainActivity activity) {
            String moduleVersion = readModuleVersion(activity);
            String overrideCount = readOverrideCount(activity);
            boolean targetInstalled = isPackageInstalled(activity, TARGET_PACKAGE);
            String abis = Arrays.toString(Build.SUPPORTED_ABIS);
            String checkedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            String diagnostics = "Arc Dark diagnostics"
                    + "\nChecked: " + checkedAt
                    + "\nModule package: " + activity.getPackageName()
                    + "\nModule version: " + moduleVersion
                    + "\nTarget package: " + TARGET_PACKAGE
                    + "\nTarget installed: " + targetInstalled
                    + "\nAsset overrides: " + overrideCount
                    + "\nDevice ABI: " + abis;
            return new StatusSnapshot(moduleVersion, overrideCount, targetInstalled, abis, diagnostics);
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
