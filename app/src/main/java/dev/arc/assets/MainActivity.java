package dev.arc.assets;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int REQUEST_IMPORT_ZIP = 2001;
    private static final int WIDE_BREAKPOINT_DP = 840;

    private TextView injectionStatusValue;
    private TextView targetStatusValue;
    private TextView assetCountValue;
    private TextView rootPathValue;
    private TextView restartNoticeValue;
    private TextView enabledLayersValue;
    private TextView importedPacksValue;
    private TextView topLayerValue;
    private Switch injectionSwitch;
    private LinearLayout enabledPackList;
    private LinearLayout disabledPackList;
    private Button openTargetButton;
    private UiStatusSnapshot snapshot;
    private Palette palette;
    private boolean binding;
    private boolean chinese = true;
    private boolean lightTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ensureControlFile();
        rebuildContent();
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
            toast(text("selectedUnavailable"));
            return;
        }

        try {
            PackManifest manifest = ImportedPackInstaller.readManifest(this, uri);
            ArcDarkControl.Control next = PackOrderController.withPackAtFront(
                    ArcDarkControl.readLocal(this),
                    manifest.id
            );
            ArcDarkControl.writeLocal(this, next);
            refreshStatus();
            openTargetApp(uri);
        } catch (Exception exception) {
            toast(text("invalidZip"));
        }
    }

    private void rebuildContent() {
        setContentView(createContentView());
        refreshStatus();
    }

    private View createContentView() {
        palette = Palette.forMode(lightTheme);
        boolean wide = isWideLayout();

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(palette.background);

        LinearLayout root = vertical();
        int horizontalPadding = wide ? 32 : 22;
        root.setPadding(dp(horizontalPadding), dp(34), dp(horizontalPadding), dp(24));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        root.addView(createHeader(wide), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        main.setBaselineAligned(false);
        root.addView(main, withTopMargin(dp(24)));

        View injectionPanel = createInjectionPanel();
        View packPanel = createPackPanel(wide);
        if (wide) {
            main.addView(injectionPanel, new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    0.96f
            ));
            LinearLayout.LayoutParams packParams = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.04f
            );
            packParams.leftMargin = dp(18);
            main.addView(packPanel, packParams);
            main.post(() -> alignPanelHeights(injectionPanel, packPanel));
        } else {
            main.addView(injectionPanel);
            main.addView(packPanel, withTopMargin(dp(18)));
        }

        root.addView(createActionPanel(wide), withTopMargin(dp(18)));
        return scroll;
    }

    private View createHeader(boolean wide) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        header.setGravity(wide ? Gravity.CENTER_VERTICAL : Gravity.START);

        LinearLayout titleBox = vertical();
        TextView title = new TextView(this);
        title.setText("Arc customize");
        title.setTextColor(palette.text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(wide ? 34 : 30);
        titleBox.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText(text("subtitle"));
        subtitle.setTextColor(palette.muted);
        subtitle.setTextSize(16);
        titleBox.addView(subtitle, withTopMargin(dp(4)));

        header.addView(titleBox, new LinearLayout.LayoutParams(
                wide ? 0 : LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                wide ? 1f : 0f
        ));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        controls.setGravity(Gravity.START);
        controls.addView(createControlGroup(text("languageLabel"),
                createSegmentedControl(
                        createSegmentButton("EN", !chinese, view -> {
                            chinese = false;
                            rebuildContent();
                        }),
                        createSegmentButton("中文", chinese, view -> {
                            chinese = true;
                            rebuildContent();
                        })
                )));
        LinearLayout.LayoutParams themeParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        if (wide) {
            themeParams.leftMargin = dp(14);
        } else {
            themeParams.topMargin = dp(10);
        }
        controls.addView(createControlGroup(text("themeLabel"),
                createSegmentedControl(
                        createSegmentButton(text("dark"), !lightTheme, view -> {
                            lightTheme = false;
                            rebuildContent();
                        }),
                        createSegmentButton(text("light"), lightTheme, view -> {
                            lightTheme = true;
                            rebuildContent();
                        })
                )), themeParams);

        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        if (!wide) {
            controlsParams.topMargin = dp(18);
        }
        header.addView(controls, controlsParams);
        return header;
    }

    private View createInjectionPanel() {
        LinearLayout panel = panel();
        panel.addView(sectionTitle(text("injection")));

        LinearLayout switchCard = new LinearLayout(this);
        switchCard.setOrientation(LinearLayout.HORIZONTAL);
        switchCard.setGravity(Gravity.CENTER_VERTICAL);
        switchCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        switchCard.setBackground(rounded(palette.surfaceContainer, palette.surfaceContainer, dp(22)));

        LinearLayout copy = vertical();
        TextView switchTitle = new TextView(this);
        switchTitle.setText(text("enableInjection"));
        switchTitle.setTextColor(palette.text);
        switchTitle.setTypeface(Typeface.DEFAULT_BOLD);
        switchTitle.setTextSize(23);
        copy.addView(switchTitle);

        TextView switchHelper = new TextView(this);
        switchHelper.setText(text("enableHint"));
        switchHelper.setTextColor(palette.dim);
        switchHelper.setTextSize(14);
        copy.addView(switchHelper, withTopMargin(dp(4)));
        switchCard.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        injectionSwitch = new Switch(this);
        injectionSwitch.setText("");
        injectionSwitch.setOnCheckedChangeListener(this::onInjectionChanged);
        switchCard.addView(injectionSwitch);
        panel.addView(switchCard, withTopMargin(dp(24)));

        LinearLayout statusList = vertical();
        statusList.setBackground(rounded(palette.surface, palette.outlineVariant, dp(22)));
        injectionStatusValue = addStatusRow(statusList, text("state"));
        targetStatusValue = addStatusRow(statusList, text("targetApp"));
        assetCountValue = addStatusRow(statusList, text("difference"));

        LinearLayout pathRow = vertical();
        pathRow.setPadding(dp(18), dp(14), dp(18), dp(16));
        TextView runtime = label(text("runtimePath"));
        pathRow.addView(runtime);
        rootPathValue = bodyText();
        rootPathValue.setMaxLines(2);
        rootPathValue.setEllipsize(TextUtils.TruncateAt.END);
        pathRow.addView(rootPathValue, withTopMargin(dp(6)));
        statusList.addView(pathRow);
        panel.addView(statusList, withTopMargin(dp(18)));

        restartNoticeValue = bodyText();
        restartNoticeValue.setTextColor(palette.dim);
        panel.addView(restartNoticeValue, withTopMargin(dp(14)));
        return panel;
    }

    private View createPackPanel(boolean wide) {
        LinearLayout panel = panel();

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(sectionTitle(text("materialPacks")), new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));
        Button refresh = compactButton(text("refresh"), view -> refreshStatus());
        head.addView(refresh, new LinearLayout.LayoutParams(dp(104), dp(46)));
        panel.addView(head);

        panel.addView(createSummaryChips(), withTopMargin(dp(14)));

        ScrollView listScroll = new LockingScrollView(this);
        listScroll.setFillViewport(false);
        listScroll.setVerticalScrollBarEnabled(true);
        listScroll.setScrollbarFadingEnabled(false);
        listScroll.setNestedScrollingEnabled(false);
        LinearLayout listContent = vertical();
        listScroll.addView(listContent, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        listContent.addView(sectionLabel(text("enabledPacks")));
        enabledPackList = vertical();
        listContent.addView(enabledPackList, withTopMargin(dp(8)));
        listContent.addView(sectionLabel(text("disabledPacks")), withTopMargin(dp(14)));
        disabledPackList = vertical();
        listContent.addView(disabledPackList, withTopMargin(dp(8)));

        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                wide ? dp(390) : dp(520)
        );
        listParams.topMargin = dp(16);
        panel.addView(listScroll, listParams);
        return panel;
    }

    private View createSummaryChips() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        enabledLayersValue = addSummaryChip(row, text("layers"));
        importedPacksValue = addSummaryChip(row, text("packs"));
        topLayerValue = addSummaryChip(row, text("top"));
        return row;
    }

    private TextView addSummaryChip(LinearLayout row, String labelText) {
        LinearLayout chip = vertical();
        chip.setPadding(dp(12), dp(9), dp(12), dp(9));
        chip.setBackground(rounded(palette.surface, palette.outlineVariant, dp(16)));

        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextColor(palette.muted);
        label.setTextSize(12);
        chip.addView(label);

        TextView value = new TextView(this);
        value.setTextColor(palette.text);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        value.setTextSize(16);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        chip.addView(value, withTopMargin(dp(2)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (row.getChildCount() > 0) {
            params.leftMargin = dp(8);
        }
        row.addView(chip, params);
        return value;
    }

    private View createActionPanel(boolean wide) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        Button refresh = createActionButton(text("refresh"), view -> refreshStatus());
        Button importZip = createActionButton(text("importZip"), view -> openImportPicker());
        Button copy = createActionButton(text("copyDiagnostics"), this::copyDiagnostics);
        openTargetButton = createActionButton(text("openArcaea"), view -> openTargetApp(null));

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

    private View createPackRow(PackCatalog.Entry entry, int activeIndex, int activeCount) {
        boolean active = activeIndex >= 0;
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setMinimumHeight(dp(96));
        row.setBackground(rounded(
                active ? palette.activeContainer : palette.surface,
                active ? palette.primary : palette.outlineVariant,
                dp(18)
        ));

        row.addView(createCoverSlot(entry, active), new LinearLayout.LayoutParams(dp(64), dp(64)));

        LinearLayout textBox = vertical();
        textBox.setPadding(dp(12), 0, dp(10), 0);
        TextView name = new TextView(this);
        name.setText(entry.name);
        name.setTextColor(palette.text);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextSize(18);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        textBox.addView(name);

        TextView meta = bodyText();
        meta.setText(formatPackMeta(entry));
        meta.setMaxLines(2);
        meta.setEllipsize(TextUtils.TruncateAt.END);
        textBox.addView(meta, withTopMargin(dp(3)));
        row.addView(textBox, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (active) {
            Button up = compactButton("↑", view -> movePack(entry.id, -1));
            up.setContentDescription(text("up"));
            up.setEnabled(activeIndex > 0);
            up.setAlpha(activeIndex > 0 ? 1f : 0.45f);
            Button disable = compactButton("×", view -> onPackEnabledChanged(entry.id, false));
            disable.setContentDescription(text("disablePack"));
            row.addView(up, new LinearLayout.LayoutParams(dp(58), dp(48)));
            LinearLayout.LayoutParams disableParams = new LinearLayout.LayoutParams(dp(58), dp(48));
            disableParams.leftMargin = dp(8);
            row.addView(disable, disableParams);
        } else {
            Button enable = compactButton(text("enable"), view -> onPackEnabledChanged(entry.id, true));
            boolean canEnable = entry.available;
            enable.setEnabled(canEnable);
            enable.setAlpha(canEnable ? 1f : 0.5f);
            row.addView(enable, new LinearLayout.LayoutParams(dp(84), dp(48)));
        }

        return row;
    }

    private View createCoverSlot(PackCatalog.Entry entry, boolean active) {
        FrameLayout slot = new FrameLayout(this);
        slot.setBackground(rounded(palette.surfaceContainerHigh, palette.outlineVariant, dp(16)));

        ImageView cover = createCoverView(entry.coverFile);
        if (cover != null) {
            slot.addView(cover, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
        } else {
            TextView fallback = new TextView(this);
            fallback.setText("IMG");
            fallback.setGravity(Gravity.CENTER);
            fallback.setTextColor(palette.dim);
            fallback.setTextSize(11);
            fallback.setTypeface(Typeface.DEFAULT_BOLD);
            slot.addView(fallback, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
            ));
        }

        if (active) {
            TextView badge = new TextView(this);
            badge.setText("✓");
            badge.setGravity(Gravity.CENTER);
            badge.setTextColor(palette.onPrimary);
            badge.setTextSize(15);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setBackground(rounded(palette.primary, palette.primary, dp(9)));
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.BOTTOM | Gravity.RIGHT);
            slot.addView(badge, badgeParams);
        }
        return slot;
    }

    private ImageView createCoverView(File coverFile) {
        if (coverFile == null || !coverFile.isFile()) {
            return null;
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(coverFile.getAbsolutePath(), bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = calculateInSampleSize(bounds, dp(72), dp(72));
        Bitmap bitmap = BitmapFactory.decodeFile(coverFile.getAbsolutePath(), options);
        if (bitmap == null) {
            return null;
        }

        ImageView image = new ImageView(this);
        image.setImageBitmap(bitmap);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(rounded(palette.surfaceContainerHigh, palette.outlineVariant, dp(16)));
        return image;
    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int inSampleSize = 1;
        int height = options.outHeight;
        int width = options.outWidth;
        while ((height / inSampleSize) > reqHeight * 2 || (width / inSampleSize) > reqWidth * 2) {
            inSampleSize *= 2;
        }
        return inSampleSize;
    }

    private String formatPackMeta(PackCatalog.Entry entry) {
        List<String> parts = new ArrayList<>();
        if (hasText(entry.version)) {
            parts.add("v" + entry.version.trim());
        }
        if (hasText(entry.author)) {
            parts.add(entry.author.trim());
        }
        if (hasText(entry.description)) {
            parts.add(entry.description.trim());
        }
        if (parts.isEmpty()) {
            return entry.builtIn ? text("builtInPack") : entry.id;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                builder.append(" · ");
            }
            builder.append(parts.get(i));
        }
        return builder.toString();
    }

    private boolean hasText(String value) {
        return value != null && value.trim().length() > 0;
    }

    private TextView addStatusRow(LinearLayout panel, String labelText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(14), dp(18), dp(14));

        TextView label = label(labelText);
        row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = valueText();
        row.addView(value, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        panel.addView(row);
        return value;
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(palette.primary);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(28);
        return title;
    }

    private TextView sectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(palette.dim);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextSize(12);
        return label;
    }

    private TextView label(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(palette.muted);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextSize(16);
        return label;
    }

    private TextView valueText() {
        TextView value = new TextView(this);
        value.setTextColor(palette.text);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        value.setTextSize(16);
        value.setGravity(Gravity.END);
        value.setSingleLine(true);
        value.setEllipsize(TextUtils.TruncateAt.END);
        return value;
    }

    private TextView bodyText() {
        TextView text = new TextView(this);
        text.setTextColor(palette.muted);
        text.setTextSize(14);
        text.setLineSpacing(dp(2), 1f);
        return text;
    }

    private Button createActionButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(15);
        button.setTextColor(palette.text);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(56));
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(rounded(palette.secondaryContainer, palette.outlineVariant, dp(18)));
        button.setOnClickListener(listener);
        return button;
    }

    private Button compactButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(13);
        button.setTextColor(palette.text);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setBackground(rounded(palette.surfaceContainerHigh, palette.outlineVariant, dp(14)));
        button.setOnClickListener(listener);
        return button;
    }

    private View createControlGroup(String label, View control) {
        LinearLayout group = vertical();
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextColor(palette.dim);
        labelView.setTypeface(Typeface.DEFAULT_BOLD);
        labelView.setTextSize(12);
        group.addView(labelView);
        group.addView(control, withTopMargin(dp(6)));
        return group;
    }

    private View createSegmentedControl(Button left, Button right) {
        LinearLayout control = new LinearLayout(this);
        control.setOrientation(LinearLayout.HORIZONTAL);
        control.setPadding(dp(4), dp(4), dp(4), dp(4));
        control.setBackground(rounded(palette.surfaceContainer, palette.outlineVariant, dp(24)));
        control.addView(left, new LinearLayout.LayoutParams(dp(92), dp(42)));
        control.addView(right, new LinearLayout.LayoutParams(dp(92), dp(42)));
        return control;
    }

    private Button createSegmentButton(String text, boolean active, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(active ? palette.onPrimaryContainer : palette.muted);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setBackground(rounded(active ? palette.primaryContainer : Color.TRANSPARENT, Color.TRANSPARENT, dp(20)));
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout panel() {
        LinearLayout panel = vertical();
        panel.setPadding(dp(22), dp(22), dp(22), dp(22));
        panel.setBackground(rounded(palette.surfaceContainerLow, palette.outlineVariant, dp(26)));
        return panel;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private void refreshStatus() {
        if (injectionSwitch == null) {
            return;
        }
        snapshot = UiStatusSnapshot.capture(this);
        binding = true;
        injectionSwitch.setChecked(snapshot.control.injectionEnabled);
        binding = false;

        injectionStatusValue.setText(snapshot.control.injectionEnabled ? text("enabled") : text("disabled"));
        injectionStatusValue.setTextColor(snapshot.control.injectionEnabled ? palette.success : palette.warning);
        targetStatusValue.setText(snapshot.targetInstalled ? text("installed") : text("notInstalled"));
        targetStatusValue.setTextColor(snapshot.targetInstalled ? palette.success : palette.warning);
        assetCountValue.setText(snapshot.overrideCount);
        rootPathValue.setText(snapshot.targetRootPath);
        restartNoticeValue.setText(text("applyNotice"));
        enabledLayersValue.setText(String.valueOf(snapshot.control.activePackOrder.size()));
        importedPacksValue.setText(String.valueOf(importedPackCount()));
        topLayerValue.setText(topLayerText());
        openTargetButton.setEnabled(snapshot.targetInstalled);
        openTargetButton.setAlpha(snapshot.targetInstalled ? 1f : 0.55f);
        rebuildPackList();
    }

    private int importedPackCount() {
        int imported = 0;
        for (PackCatalog.Entry entry : snapshot.packs) {
            if (!entry.builtIn) {
                imported++;
            }
        }
        return imported;
    }

    private String topLayerText() {
        if (snapshot.control.activePackOrder.isEmpty()) {
            return text("original");
        }
        PackCatalog.Entry entry = findPackEntry(snapshot.control.activePackOrder.get(0));
        return entry == null ? snapshot.control.activePackOrder.get(0) : entry.name;
    }

    private void rebuildPackList() {
        enabledPackList.removeAllViews();
        disabledPackList.removeAllViews();
        int activeCount = snapshot.control.activePackOrder.size();

        for (String packId : snapshot.control.activePackOrder) {
            PackCatalog.Entry entry = findPackEntry(packId);
            if (entry != null) {
                addPackRow(enabledPackList, createPackRow(entry, snapshot.control.activePackOrder.indexOf(entry.id), activeCount));
            }
        }

        for (PackCatalog.Entry entry : snapshot.packs) {
            if (snapshot.control.activePackOrder.indexOf(entry.id) < 0) {
                addPackRow(disabledPackList, createPackRow(entry, -1, activeCount));
            }
        }

        if (enabledPackList.getChildCount() == 0) {
            enabledPackList.addView(emptyText(text("noEnabledPacks")));
        }
        if (disabledPackList.getChildCount() == 0) {
            disabledPackList.addView(emptyText(text("noDisabledPacks")));
        }
    }

    private void addPackRow(LinearLayout list, View row) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(96)
        );
        if (list.getChildCount() > 0) {
            params.topMargin = dp(10);
        }
        list.addView(row, params);
    }

    private TextView emptyText(String value) {
        TextView text = bodyText();
        text.setText(value);
        text.setGravity(Gravity.CENTER);
        text.setPadding(dp(12), dp(18), dp(12), dp(18));
        text.setBackground(rounded(palette.surface, palette.outlineVariant, dp(18)));
        return text;
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
        saveControl(PackOrderController.setPackEnabled(snapshot.control, packId, enabled));
    }

    private void movePack(String packId, int direction) {
        if (snapshot == null) {
            return;
        }
        ArcDarkControl.Control next = PackOrderController.movePack(snapshot.control, packId, direction);
        if (next.activePackOrder.equals(snapshot.control.activePackOrder)) {
            return;
        }
        saveControl(next);
    }

    private void saveControl(ArcDarkControl.Control control) {
        try {
            ArcDarkControl.writeLocal(this, control);
            toast(text("saved"));
        } catch (Exception exception) {
            toast(text("unableSave"));
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
            toast(text("diagnosticsUnavailable"));
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Arc Dark diagnostics", snapshot.diagnostics));
        toast(text("diagnosticsCopied"));
    }

    private void openTargetApp(Uri importUri) {
        if (!ArcDarkLauncher.openTargetApp(this, importUri)) {
            toast(text("targetMissing"));
        }
    }

    private boolean isWideLayout() {
        Configuration configuration = getResources().getConfiguration();
        return configuration.screenWidthDp >= WIDE_BREAKPOINT_DP;
    }

    private void alignPanelHeights(View left, View right) {
        int targetHeight = Math.max(left.getHeight(), right.getHeight());
        if (targetHeight <= 0) {
            return;
        }
        setViewHeight(left, targetHeight);
        setViewHeight(right, targetHeight);
    }

    private void setViewHeight(View view, int height) {
        android.view.ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null && params.height != height) {
            params.height = height;
            view.setLayoutParams(params);
        }
    }

    private String text(String key) {
        if (chinese) {
            switch (key) {
                case "subtitle": return "Arcaea 材质修改";
                case "languageLabel": return "语言";
                case "themeLabel": return "主题";
                case "dark": return "深色";
                case "light": return "浅色";
                case "injection": return "注入";
                case "enableInjection": return "启用注入";
                case "enableHint": return "打开 Arcaea 时应用当前材质包。";
                case "state": return "状态";
                case "enabled": return "已启用";
                case "disabled": return "已关闭";
                case "targetApp": return "目标应用";
                case "installed": return "已安装";
                case "notInstalled": return "未安装";
                case "difference": return "当前修改素材";
                case "runtimePath": return "运行路径";
                case "materialPacks": return "材质包";
                case "layers": return "启用";
                case "packs": return "导入";
                case "top": return "顶层";
                case "original": return "原始资源";
                case "enabledPacks": return "已启用";
                case "disabledPacks": return "未启用";
                case "enable": return "启用";
                case "up": return "上移";
                case "disablePack": return "禁用";
                case "refresh": return "刷新";
                case "importZip": return "导入 ZIP";
                case "copyDiagnostics": return "复制诊断";
                case "openArcaea": return "打开 Arcaea";
                case "applyNotice": return "使用打开 Arcaea 来应用修改；后续重启会复用已应用状态。";
                case "saved": return "已保存。请完全关闭 Arcaea，再从这里打开。";
                case "unableSave": return "无法保存控制状态";
                case "targetMissing": return "未安装 Arcaea";
                case "invalidZip": return "无效的 Arc Dark ZIP 包";
                case "selectedUnavailable": return "所选文件不可用";
                case "diagnosticsUnavailable": return "诊断不可用";
                case "diagnosticsCopied": return "诊断已复制";
                case "builtInPack": return "当前修改素材";
                case "noEnabledPacks": return "暂无已启用材质包";
                case "noDisabledPacks": return "暂无未启用材质包";
                default: return key;
            }
        }

        switch (key) {
            case "subtitle": return "Arcaea material customization";
            case "languageLabel": return "Language";
            case "themeLabel": return "Theme";
            case "dark": return "Dark";
            case "light": return "Light";
            case "injection": return "Injection";
            case "enableInjection": return "Enable injection";
            case "enableHint": return "Applies active material packs when Arcaea opens.";
            case "state": return "State";
            case "enabled": return "Enabled";
            case "disabled": return "Disabled";
            case "targetApp": return "Target app";
            case "installed": return "Installed";
            case "notInstalled": return "Not installed";
            case "difference": return "Difference";
            case "runtimePath": return "Runtime path";
            case "materialPacks": return "Material packs";
            case "layers": return "Layers";
            case "packs": return "Packs";
            case "top": return "Top";
            case "original": return "Original";
            case "enabledPacks": return "Enabled";
            case "disabledPacks": return "Disabled";
            case "enable": return "Enable";
            case "up": return "Up";
            case "disablePack": return "Disable";
            case "refresh": return "Refresh";
            case "importZip": return "Import ZIP";
            case "copyDiagnostics": return "Copy diagnostics";
            case "openArcaea": return "Open Arcaea";
            case "applyNotice": return "Use Open Arcaea to apply changes. Later restarts reuse the applied state.";
            case "saved": return "Saved. Fully close Arcaea, then use Open Arcaea here.";
            case "unableSave": return "Unable to save control state";
            case "targetMissing": return "Arcaea is not installed";
            case "invalidZip": return "Invalid Arc Dark ZIP pack";
            case "selectedUnavailable": return "Selected file is unavailable";
            case "diagnosticsUnavailable": return "Diagnostics unavailable";
            case "diagnosticsCopied": return "Diagnostics copied";
            case "builtInPack": return "Difference";
            case "noEnabledPacks": return "No enabled packs";
            case "noDisabledPacks": return "No disabled packs";
            default: return key;
        }
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
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

    private static int color(String value) {
        return Color.parseColor(value);
    }

    private final class LockingScrollView extends ScrollView {
        private boolean lockParentScroll;
        private float lastY;
        private final int touchSlop;

        LockingScrollView(Context context) {
            super(context);
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                lastY = event.getY();
                lockParentScroll = canScrollVertically(-1) || canScrollVertically(1);
                getParent().requestDisallowInterceptTouchEvent(lockParentScroll);
            } else if (action == MotionEvent.ACTION_MOVE) {
                float deltaY = event.getY() - lastY;
                if (Math.abs(deltaY) > touchSlop) {
                    lockParentScroll = deltaY < 0
                            ? canScrollVertically(1)
                            : canScrollVertically(-1);
                    lastY = event.getY();
                }
                getParent().requestDisallowInterceptTouchEvent(lockParentScroll);
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                lockParentScroll = false;
                getParent().requestDisallowInterceptTouchEvent(false);
            }
            return super.dispatchTouchEvent(event);
        }
    }

    private static final class Palette {
        final int background;
        final int surface;
        final int surfaceContainerLow;
        final int surfaceContainer;
        final int surfaceContainerHigh;
        final int secondaryContainer;
        final int activeContainer;
        final int primary;
        final int onPrimary;
        final int primaryContainer;
        final int onPrimaryContainer;
        final int outlineVariant;
        final int text;
        final int muted;
        final int dim;
        final int success;
        final int warning;

        private Palette(
                int background,
                int surface,
                int surfaceContainerLow,
                int surfaceContainer,
                int surfaceContainerHigh,
                int secondaryContainer,
                int activeContainer,
                int primary,
                int onPrimary,
                int primaryContainer,
                int onPrimaryContainer,
                int outlineVariant,
                int text,
                int muted,
                int dim,
                int success,
                int warning
        ) {
            this.background = background;
            this.surface = surface;
            this.surfaceContainerLow = surfaceContainerLow;
            this.surfaceContainer = surfaceContainer;
            this.surfaceContainerHigh = surfaceContainerHigh;
            this.secondaryContainer = secondaryContainer;
            this.activeContainer = activeContainer;
            this.primary = primary;
            this.onPrimary = onPrimary;
            this.primaryContainer = primaryContainer;
            this.onPrimaryContainer = onPrimaryContainer;
            this.outlineVariant = outlineVariant;
            this.text = text;
            this.muted = muted;
            this.dim = dim;
            this.success = success;
            this.warning = warning;
        }

        static Palette forMode(boolean light) {
            if (light) {
                return new Palette(
                        color("#F7FAFD"),
                        color("#FFFFFF"),
                        color("#F1F5F8"),
                        color("#E8EEF3"),
                        color("#DFE7ED"),
                        color("#D5E4EF"),
                        color("#D9F4F6"),
                        color("#006A70"),
                        color("#FFFFFF"),
                        color("#8FF3FB"),
                        color("#002F34"),
                        color("#C1C7D0"),
                        color("#171C22"),
                        color("#42515D"),
                        color("#61717F"),
                        color("#006C5B"),
                        color("#9A4B00")
                );
            }
            return new Palette(
                    color("#0D1118"),
                    color("#111820"),
                    color("#18212C"),
                    color("#1D2734"),
                    color("#26313F"),
                    color("#344450"),
                    color("#0C3440"),
                    color("#7DE8EF"),
                    color("#00363B"),
                    color("#0F4B52"),
                    color("#B7FAFF"),
                    color("#414C58"),
                    color("#EDF4FB"),
                    color("#B7C6D4"),
                    color("#8593A2"),
                    color("#84F1D8"),
                    color("#FFD58F")
            );
        }
    }
}
