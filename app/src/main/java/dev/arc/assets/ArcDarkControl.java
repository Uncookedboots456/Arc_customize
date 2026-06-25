package dev.arc.assets;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ArcDarkControl {
    static final String KEY_INJECTION_ENABLED = "injection_enabled";
    static final String KEY_ACTIVE_PACK_ID = "active_pack_id";
    static final String KEY_ACTIVE_PACK_ORDER = "active_pack_order";
    private static final String REMOVED_TEST_PACK_ID = "test_pkg";

    private ArcDarkControl() {
    }

    static Control defaults() {
        return new Control(true, Collections.<String>emptyList());
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
        return readJson(ArcDarkFileOps.readUtf8(file));
    }

    static Control readJson(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        List<String> packOrder;
        if (root.has(KEY_ACTIVE_PACK_ORDER)) {
            packOrder = sanitizePackOrder(root.optJSONArray(KEY_ACTIVE_PACK_ORDER));
            if (packOrder.isEmpty() && root.has(KEY_ACTIVE_PACK_ID)) {
                String packId = sanitizePackId(root.optString(KEY_ACTIVE_PACK_ID, ""));
                if (packId.length() > 0) {
                    packOrder = Collections.singletonList(packId);
                }
            }
        } else {
            String packId = sanitizePackId(root.optString(KEY_ACTIVE_PACK_ID, ""));
            packOrder = packId.length() == 0
                    ? Collections.<String>emptyList()
                    : Collections.singletonList(packId);
        }
        return new Control(
                root.optBoolean(KEY_INJECTION_ENABLED, true),
                packOrder
        );
    }

    static void writeFile(File file, Control control) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("Unable to create " + parent);
        }

        JSONObject root = new JSONObject();
        root.put(KEY_INJECTION_ENABLED, control.injectionEnabled);
        root.put(KEY_ACTIVE_PACK_ID, control.primaryPackId());
        org.json.JSONArray order = new org.json.JSONArray();
        for (String packId : control.activePackOrder) {
            order.put(packId);
        }
        root.put(KEY_ACTIVE_PACK_ORDER, order);

        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        ArcDarkFileOps.writeUtf8(tmp, root.toString(2));
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
        if (isAllowedPackId(packId)) {
            return packId;
        }
        return "";
    }

    static boolean isAllowedPackId(String packId) {
        return isBuiltInPackId(packId)
                || isExternalPackId(packId);
    }

    static boolean isBuiltInPackId(String packId) {
        return ArcDarkConstants.PAIRUMU_DARK_PACK_ID.equals(packId);
    }

    static boolean isExternalPackId(String packId) {
        if (packId == null || packId.length() == 0 || packId.length() > 64) {
            return false;
        }
        if (ArcDarkConstants.DEFAULT_PACK_ID.equals(packId)
                || REMOVED_TEST_PACK_ID.equals(packId)
                || isBuiltInPackId(packId)
                || ".".equals(packId)
                || "..".equals(packId)
                || packId.endsWith(".tmp")
                || packId.endsWith(".tmp-import")
                || packId.endsWith(".backup-import")) {
            return false;
        }
        for (int i = 0; i < packId.length(); i++) {
            char c = packId.charAt(i);
            boolean allowed = (c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '.'
                    || c == '_'
                    || c == '-';
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    static List<String> sanitizePackOrder(Iterable<String> packIds) {
        if (packIds == null) {
            return Collections.emptyList();
        }

        Set<String> deduped = new LinkedHashSet<>();
        for (String packId : packIds) {
            if (isAllowedPackId(packId)) {
                deduped.add(packId);
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(deduped));
    }

    private static List<String> sanitizePackOrder(org.json.JSONArray array) {
        if (array == null) {
            return Collections.emptyList();
        }

        List<String> values = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            values.add(array.optString(i, ""));
        }
        return sanitizePackOrder(values);
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
        final List<String> activePackOrder;

        Control(boolean injectionEnabled, List<String> activePackOrder) {
            this.injectionEnabled = injectionEnabled;
            this.activePackOrder = sanitizePackOrder(activePackOrder);
        }

        Control withInjectionEnabled(boolean enabled) {
            return new Control(enabled, activePackOrder);
        }

        Control withActivePackId(String packId) {
            String sanitized = sanitizePackId(packId);
            return sanitized.length() == 0
                    ? new Control(injectionEnabled, Collections.<String>emptyList())
                    : new Control(injectionEnabled, Collections.singletonList(sanitized));
        }

        Control withActivePackOrder(List<String> packOrder) {
            return new Control(injectionEnabled, packOrder);
        }

        Control withPackAtFront(String packId) {
            if (!isAllowedPackId(packId)) {
                return this;
            }
            List<String> next = new ArrayList<>();
            next.add(packId);
            for (String existing : activePackOrder) {
                if (!packId.equals(existing)) {
                    next.add(existing);
                }
            }
            return new Control(injectionEnabled, next);
        }

        String primaryPackId() {
            return activePackOrder.isEmpty() ? "" : activePackOrder.get(0);
        }
    }
}
