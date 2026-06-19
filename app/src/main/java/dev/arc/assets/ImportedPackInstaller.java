package dev.arc.assets;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

final class ImportedPackInstaller {
    private ImportedPackInstaller() {
    }

    static PackManifest readManifest(Context context, Uri uri) throws Exception {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IllegalArgumentException("Unable to open selected pack");
            }
            return ZipPackReader.readManifest(input);
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
        ArcDarkFileOps.deleteRecursively(tmpDir);
        ArcDarkFileOps.deleteRecursively(backupDir);
        if (!tmpDir.mkdirs()) {
            throw new IllegalStateException("Unable to create " + tmpDir);
        }

        List<AssetOverride> entries;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IllegalArgumentException("Unable to open selected pack");
            }
            entries = ZipPackReader.extractAssets(input, tmpDir, manifest);
        } catch (Exception exception) {
            ArcDarkFileOps.deleteRecursively(tmpDir);
            throw exception;
        }

        if (entries.isEmpty()) {
            ArcDarkFileOps.deleteRecursively(tmpDir);
            throw new IllegalArgumentException("Pack contains no assets/ files");
        }

        PackMetadataWriter.writeImportedMetadata(tmpDir, manifest, entries, importedAtUtc());
        ArcDarkFileOps.replaceDirectory(tmpDir, finalDir, backupDir);
        return new ImportResult(manifest, finalDir, entries.size());
    }

    private static String importedAtUtc() {
        SimpleDateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        return utcFormat.format(new Date());
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
}
