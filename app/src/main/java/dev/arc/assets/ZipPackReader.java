package dev.arc.assets;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class ZipPackReader {
    private static final long MAX_FILE_BYTES = 512L * 1024L * 1024L;
    private static final long MAX_TOTAL_BYTES = 2L * 1024L * 1024L * 1024L;
    private static final String MANIFEST_ENTRY = "pack.json";

    private ZipPackReader() {
    }

    static PackManifest readManifest(InputStream input) throws Exception {
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

    static List<AssetOverride> extractAssets(InputStream input, File tmpDir, PackManifest manifest)
            throws Exception {
        List<AssetOverride> entries = new ArrayList<>();
        long totalBytes = 0L;
        boolean sawManifest = false;
        boolean sawCover = manifest.cover.length() == 0;

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
                    if (manifest.isExpectedCover(name) || !isSafeAssetDirectory(name, manifest)) {
                        throw new IllegalArgumentException("Unsupported ZIP entry: " + entry.getName());
                    }
                    zip.closeEntry();
                    continue;
                }
                if (manifest.isExpectedCover(name)) {
                    File output = resolveInside(tmpDir, name);
                    DigestingCopyResult copyResult = copyWithDigest(zip, output);
                    totalBytes += copyResult.size;
                    if (totalBytes > MAX_TOTAL_BYTES) {
                        throw new IllegalArgumentException("Pack is too large");
                    }
                    sawCover = true;
                    zip.closeEntry();
                    continue;
                }
                if (!isSafeAssetEntry(name, manifest)) {
                    throw new IllegalArgumentException("Unsupported ZIP entry: " + entry.getName());
                }

                File output = resolveInside(tmpDir, name);
                DigestingCopyResult copyResult = copyWithDigest(zip, output);
                totalBytes += copyResult.size;
                if (copyResult.size > MAX_FILE_BYTES) {
                    throw new IllegalArgumentException("Pack file is too large: " + name);
                }
                if (totalBytes > MAX_TOTAL_BYTES) {
                    throw new IllegalArgumentException("Pack is too large");
                }
                entries.add(new AssetOverride(
                        manifest.mapAssetPath(name),
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
        if (!sawCover) {
            throw new IllegalArgumentException("Pack is missing cover file: " + manifest.cover);
        }
        return entries;
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

    private static DigestingCopyResult copyWithDigest(InputStream input, File output) throws Exception {
        File parent = output.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("Unable to create " + parent);
        }

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
        return new DigestingCopyResult(size, ArcDarkFileOps.hex(digest.digest()));
    }

    private static String normalizeZipEntryName(String name) {
        return name == null ? "" : name.replace('\\', '/');
    }

    private static boolean isSafeAssetDirectory(String name, PackManifest manifest) {
        String value = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        if (value.length() == 0) {
            return false;
        }
        if ("assets".equals(value)) {
            return true;
        }
        try {
            manifest.mapAssetPath(value + "/__directory_placeholder__");
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isSafeAssetEntry(String name, PackManifest manifest) {
        try {
            manifest.mapAssetPath(name);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static File resolveInside(File root, String path) throws Exception {
        try {
            return ArcDarkFileOps.resolveInside(root, path);
        } catch (IllegalStateException exception) {
            throw new IllegalStateException("ZIP entry escapes pack root: " + path);
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
