package dev.arc.assets;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class ArcDarkControl {
    static final String KEY_INJECTION_ENABLED = "injection_enabled";
    static final String KEY_ACTIVE_PACK_ID = "active_pack_id";

    private ArcDarkControl() {
    }

    static Control defaults() {
        return new Control(true, ArcDarkConstants.DEFAULT_PACK_ID);
    }

    static Control readLocal(Context context) {
        repairPrivateStorage(context);
        try {
            return readFile(controlFile(context));
        } catch (Exception ignored) {
            return defaults();
        }
    }

    static void writeLocal(Context context, Control control) throws Exception {
        repairPrivateStorage(context);
        writeFile(controlFile(context), control);
        restrictFileToOwner(controlFile(context), false);
    }

    static Control readFile(File file) throws Exception {
        if (!file.isFile()) {
            return defaults();
        }
        return readJson(readUtf8(new FileInputStream(file)));
    }

    static Control readJson(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        return new Control(
                root.optBoolean(KEY_INJECTION_ENABLED, true),
                sanitizePackId(root.optString(KEY_ACTIVE_PACK_ID, ArcDarkConstants.DEFAULT_PACK_ID))
        );
    }

    static void writeFile(File file, Control control) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("Unable to create " + parent);
        }

        JSONObject root = new JSONObject();
        root.put(KEY_INJECTION_ENABLED, control.injectionEnabled);
        root.put(KEY_ACTIVE_PACK_ID, sanitizePackId(control.activePackId));

        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("Unable to replace " + file);
        }
        if (!tmp.renameTo(file)) {
            throw new IllegalStateException("Unable to move " + tmp + " to " + file);
        }
    }

    static File controlFile(Context context) {
        return new File(context.getFilesDir(), ArcDarkConstants.CONTROL_FILE_NAME);
    }

    static String sanitizePackId(String packId) {
        if (ArcDarkConstants.TEST_PACK_ID.equals(packId)) {
            return ArcDarkConstants.TEST_PACK_ID;
        }
        return ArcDarkConstants.DEFAULT_PACK_ID;
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

    private static void repairPrivateStorage(Context context) {
        restrictFileToOwner(new File(context.getApplicationInfo().dataDir), true);
        restrictFileToOwner(context.getFilesDir(), true);
        File stalePrefs = new File(context.getApplicationInfo().dataDir, "shared_prefs");
        if (stalePrefs.exists()) {
            restrictFileToOwner(stalePrefs, true);
            File stalePrefsFile = new File(stalePrefs, "arc_dark_control.xml");
            if (stalePrefsFile.exists()) {
                restrictFileToOwner(stalePrefsFile, false);
            }
        }
        File file = controlFile(context);
        if (file.exists()) {
            restrictFileToOwner(file, false);
        }
    }

    private static void restrictFileToOwner(File file, boolean executable) {
        if (!file.exists()) {
            return;
        }
        file.setReadable(false, false);
        file.setWritable(false, false);
        file.setExecutable(false, false);
        file.setReadable(true, true);
        file.setWritable(true, true);
        if (executable) {
            file.setExecutable(true, true);
        }
    }

    static final class Control {
        final boolean injectionEnabled;
        final String activePackId;

        Control(boolean injectionEnabled, String activePackId) {
            this.injectionEnabled = injectionEnabled;
            this.activePackId = sanitizePackId(activePackId);
        }

        Control withInjectionEnabled(boolean enabled) {
            return new Control(enabled, activePackId);
        }

        Control withActivePackId(String packId) {
            return new Control(injectionEnabled, packId);
        }
    }
}
