package dev.arc.assets;

import android.content.Context;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class ImportedPackInstaller {
    private static final long MAX_FILE_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_TOTAL_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final String MANIFEST_ENTRY = "pack.json";
    private static final String ASSET_PREFIX = "assets/";

    private ImportedPackInstaller() {
    }

    static PackManifest readManifest(Context context, Uri uri) throws Exception {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IllegalArgumentException("Unable to open selected pack");
            }
            return readManifest(input);
        }
    }

    static ImportResult install(Context context, File root, Uri uri) throws Exception {
        PackManifest manifest = readManifest(context, uri);
        File packsDir = ArcDarkPaths.packsDir(root);
        if (!packsDir.isDirectory() && !packsDir.mkdirs()) {
            throw new IllegalStateException("Unable to create " + packsDir);
        }

        File tmpDir = new File(packsDir, manifest.id + ".tmp-import");
        File finalDir = ArcDarkPaths.packDir(root, manifest.id);
        File backupDir = new File(packsDir, manifest.id + ".backup-import");
        deleteRecursively(tmpDir);
        deleteRecursively(backupDir);
        if (!tmpDir.mkdirs()) {
            throw new IllegalStateException("Unable to create " + tmpDir);
        }

        List<AssetOverride> entries;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IllegalArgumentException("Unable to open selected pack");
            }
            entries = extractAssets(input, tmpDir);
        } catch (Exception exception) {
            deleteRecursively(tmpDir);
            throw exception;
        }

        if (entries.isEmpty()) {
            deleteRecursively(tmpDir);
            throw new IllegalArgumentException("Pack contains no assets/ files");
        }

        SimpleDateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String importedAt = utcFormat.format(new Date());
        writeGeneratedMetadata(tmpDir, manifest, entries, importedAt);
        replacePack(tmpDir, finalDir, backupDir);
        return new ImportResult(manifest, finalDir, entries.size());
    }

    private static PackManifest readManifest(InputStream input) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = normalizeZipEntryName(entry.getName());
                if (!MANIFEST_ENTRY.equals(name)) {
                    zip.closeEntry();
                    continue;
                }
                if (entry.isDirectory()) {
                    throw new IllegalArgumentException("pack.json must be a file");
                }
                return PackManifest.readRequired(readEntryBytes(zip, 64 * 1024));
            }
        }
        throw new IllegalArgumentException("Pack is missing root pack.json");
    }

    private static InputStream readEntryBytes(InputStream input, int maxBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        int total = 0;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IllegalArgumentException("pack.json is too large");
            }
            out.write(buffer, 0, read);
        }
        return new java.io.ByteArrayInputStream(out.toByteArray());
    }

    private static List<AssetOverride> extractAssets(InputStream input, File tmpDir) throws Exception {
        List<AssetOverride> entries = new ArrayList<>();
        long totalBytes = 0L;
        boolean sawManifest = false;

        try (ZipInputStream zip = new ZipInputStream(input)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = normalizeZipEntryName(entry.getName());
                if (MANIFEST_ENTRY.equals(name)) {
                    sawManifest = true;
                    zip.closeEntry();
                    continue;
                }
                if (entry.isDirectory()) {
                    if (!isSafeAssetDirectory(name)) {
                        throw new IllegalArgumentException("Unsupported ZIP entry: " + entry.getName());
                    }
                    zip.closeEntry();
                    continue;
                }
                if (!isSafeAssetEntry(name)) {
                    throw new IllegalArgumentException("Unsupported ZIP entry: " + entry.getName());
                }

                File output = resolveInside(tmpDir, name);
                File parent = output.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    throw new IllegalStateException("Unable to create " + parent);
                }

                DigestingCopyResult copyResult = copyWithDigest(zip, output);
                totalBytes += copyResult.size;
                if (copyResult.size > MAX_FILE_BYTES) {
                    throw new IllegalArgumentException("Pack file is too large: " + name);
                }
                if (totalBytes > MAX_TOTAL_BYTES) {
                    throw new IllegalArgumentException("Pack is too large");
                }
                entries.add(new AssetOverride(
                        name,
                        name,
                        copyResult.size,
                        copyResult.sha256,
                        true
                ));
                zip.closeEntry();
            }
        }

        if (!sawManifest) {
            throw new IllegalArgumentException("Pack is missing root pack.json");
        }
        return entries;
    }

    private static void writeGeneratedMetadata(
            File packDir,
            PackManifest manifest,
            List<AssetOverride> entries,
            String importedAt
    ) throws Exception {
        writeUtf8(new File(packDir, MANIFEST_ENTRY), manifest.toJson(entries.size(), importedAt).toString(2));

        File metadataDir = new File(packDir, "arc_overrides");
        if (!metadataDir.isDirectory() && !metadataDir.mkdirs()) {
            throw new IllegalStateException("Unable to create " + metadataDir);
        }

        JSONArray entryArray = new JSONArray();
        for (AssetOverride entry : entries) {
            JSONObject item = new JSONObject();
            item.put("assetPath", entry.assetPath);
            item.put("modulePath", entry.modulePath);
            item.put("size", entry.size);
            item.put("sha256", entry.sha256);
            item.put("materialize", entry.materialize);
            entryArray.put(item);
        }

        JSONObject index = new JSONObject();
        index.put("targetPackage", ArcDarkConstants.TARGET_PACKAGE);
        index.put("source", "zip_import");
        index.put("packId", manifest.id);
        index.put("generatedAtUtc", importedAt);
        index.put("entries", entryArray);
        writeUtf8(new File(metadataDir, "index.json"), index.toString(2));

        JSONObject summary = new JSONObject();
        summary.put("source", "zip_import");
        summary.put("packId", manifest.id);
        summary.put("includedAssets", entries.size());
        summary.put("generatedAtUtc", importedAt);
        summary.put("includedPolicy", "Only safe root assets/ files are imported.");
        writeUtf8(new File(metadataDir, "summary.json"), summary.toString(2));
    }

    private static DigestingCopyResult copyWithDigest(InputStream input, File output) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long size = 0L;
        try (FileOutputStream out = new FileOutputStream(output)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                size += read;
                if (size > MAX_FILE_BYTES) {
                    throw new IllegalArgumentException("Pack file exceeds size limit");
                }
                digest.update(buffer, 0, read);
                out.write(buffer, 0, read);
            }
        }
        return new DigestingCopyResult(size, hex(digest.digest()));
    }

    private static void replacePack(File tmpDir, File finalDir, File backupDir) {
        if (finalDir.exists() && !finalDir.renameTo(backupDir)) {
            throw new IllegalStateException("Unable to move old pack to backup: " + finalDir);
        }
        if (!tmpDir.renameTo(finalDir)) {
            if (backupDir.exists() && !backupDir.renameTo(finalDir)) {
                backupDir.deleteOnExit();
            }
            throw new IllegalStateException("Unable to install imported pack: " + finalDir);
        }
        deleteRecursively(backupDir);
    }

    private static String normalizeZipEntryName(String name) {
        return name == null ? "" : name.replace('\\', '/');
    }

    private static boolean isSafeAssetDirectory(String name) {
        String value = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        if (value.length() == 0) {
            return false;
        }
        return isSafeAssetEntry(value + "/__directory_placeholder__");
    }

    private static boolean isSafeAssetEntry(String name) {
        if (name.length() == 0 || name.indexOf(':') != -1 || !name.startsWith(ASSET_PREFIX)) {
            return false;
        }
        String[] segments = name.split("/", -1);
        for (String segment : segments) {
            if (segment.length() == 0 || ".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    private static File resolveInside(File root, String path) throws Exception {
        File canonicalRoot = root.getCanonicalFile();
        File file = new File(canonicalRoot, path).getCanonicalFile();
        String rootPath = canonicalRoot.getPath();
        String filePath = file.getPath();
        if (!filePath.equals(rootPath) && !filePath.startsWith(rootPath + File.separator)) {
            throw new IllegalStateException("ZIP entry escapes pack root: " + path);
        }
        return file;
    }

    private static void writeUtf8(File file, String content) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("Unable to create " + parent);
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static void deleteRecursively(File file) {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }

    static final class ImportResult {
        final PackManifest manifest;
        final File packDir;
        final int assetCount;

        ImportResult(PackManifest manifest, File packDir, int assetCount) {
            this.manifest = manifest;
            this.packDir = packDir;
            this.assetCount = assetCount;
        }
    }

    private static final class DigestingCopyResult {
        final long size;
        final String sha256;

        DigestingCopyResult(long size, String sha256) {
            this.size = size;
            this.sha256 = sha256;
        }
    }
}
