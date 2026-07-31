package dev.arc.assets;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

final class PackCatalog {
    private PackCatalog() {
    }

    static List<Entry> list(File root) {
        return list(root, null);
    }

    static List<Entry> list(File root, File builtInCoverFile) {
        List<Entry> entries = new ArrayList<>();

        entries.add(new Entry(
                ArcDarkConstants.PAIRUMU_DARK_PACK_ID,
                ArcDarkConstants.PAIRUMU_DARK_PACK_NAME,
                ArcDarkConstants.PAIRUMU_DARK_PACK_VERSION,
                "Index-only pack · cover loaded from installed game",
                "",
                builtInCoverFile != null && builtInCoverFile.isFile() ? builtInCoverFile : null,
                true,
                true
        ));

        File packsDir = ArcDarkPaths.packsDir(root);
        File[] packDirs = packsDir.listFiles();
        if (packDirs == null) {
            return entries;
        }

        List<Entry> imported = new ArrayList<>();
        for (File packDir : packDirs) {
            if (!packDir.isDirectory()) {
                continue;
            }
            String packId = packDir.getName();
            if (!ArcDarkControl.isExternalPackId(packId)) {
                continue;
            }
            File manifestFile = new File(packDir, "pack.json");
            File indexFile = new File(packDir, "arc_overrides/index.json");
            if (!manifestFile.isFile() || !indexFile.isFile()) {
                continue;
            }
            PackManifest manifest = PackManifest.readLenient(manifestFile, packId, packId);
            File coverFile = manifest.cover.length() == 0 ? null : new File(packDir, manifest.cover);
            if (coverFile != null && !coverFile.isFile()) {
                coverFile = null;
            }
            imported.add(new Entry(
                    packId,
                    manifest.name,
                    manifest.version,
                    manifest.description,
                    manifest.author,
                    coverFile,
                    true,
                    false
            ));
        }
        Collections.sort(imported, new Comparator<Entry>() {
            @Override
            public int compare(Entry left, Entry right) {
                return left.id.compareTo(right.id);
            }
        });
        entries.addAll(imported);
        return entries;
    }

    static final class Entry {
        final String id;
        final String name;
        final String version;
        final String description;
        final String author;
        final File coverFile;
        final boolean available;
        final boolean builtIn;

        Entry(
                String id,
                String name,
                String version,
                String description,
                String author,
                File coverFile,
                boolean available,
                boolean builtIn
        ) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.description = description;
            this.author = author;
            this.coverFile = coverFile;
            this.available = available;
            this.builtIn = builtIn;
        }
    }
}
