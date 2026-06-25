package dev.arc.assets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class PackCatalogTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void listReadsImportedPackMetadata() throws Exception {
        File root = temporaryFolder.newFolder("root");
        File packDir = ArcDarkPaths.packDir(root, "sample_pack");
        File metadataDir = new File(packDir, "arc_overrides");
        assertTrue(metadataDir.mkdirs());
        ArcDarkFileOps.writeUtf8(new File(packDir, "pack.json"), "{"
                + "\"formatVersion\":1,"
                + "\"id\":\"sample_pack\","
                + "\"name\":\"Sample Pack\","
                + "\"version\":\"2026.06\","
                + "\"description\":\"Small pack\","
                + "\"author\":\"Arc Dark Team\","
                + "\"cover\":\"cover.png\""
                + "}");
        ArcDarkFileOps.writeUtf8(new File(packDir, "cover.png"), "png");
        ArcDarkFileOps.writeUtf8(new File(metadataDir, "index.json"), "{\"entries\":[]}");

        PackCatalog.Entry entry = find(PackCatalog.list(root), "sample_pack");

        assertNotNull(entry);
        assertEquals("Sample Pack", entry.name);
        assertEquals("2026.06", entry.version);
        assertEquals("Small pack", entry.description);
        assertEquals("Arc Dark Team", entry.author);
        assertEquals(new File(packDir, "cover.png"), entry.coverFile);
    }

    @Test
    public void listDoesNotExposeDefaultBundledPack() throws Exception {
        File root = temporaryFolder.newFolder("root");

        PackCatalog.Entry entry = find(PackCatalog.list(root), ArcDarkConstants.DEFAULT_PACK_ID);

        assertNull(entry);
    }

    @Test
    public void listDoesNotExposeRemovedTestPack() throws Exception {
        File root = temporaryFolder.newFolder("root");
        File packDir = ArcDarkPaths.packDir(root, "test_pkg");
        File metadataDir = new File(packDir, "arc_overrides");
        assertTrue(metadataDir.mkdirs());
        ArcDarkFileOps.writeUtf8(new File(packDir, "pack.json"), "{"
                + "\"formatVersion\":1,"
                + "\"id\":\"test_pkg\","
                + "\"name\":\"Removed Test Pack\""
                + "}");
        ArcDarkFileOps.writeUtf8(new File(metadataDir, "index.json"), "{\"entries\":[]}");

        PackCatalog.Entry entry = find(PackCatalog.list(root), "test_pkg");

        assertNull(entry);
    }

    @Test
    public void listExposesBuiltInPairumuPack() throws Exception {
        File root = temporaryFolder.newFolder("root");
        File cover = temporaryFolder.newFile("cover.jpg");

        PackCatalog.Entry entry = find(PackCatalog.list(root, cover), ArcDarkConstants.PAIRUMU_DARK_PACK_ID);

        assertNotNull(entry);
        assertEquals(ArcDarkConstants.PAIRUMU_DARK_PACK_NAME, entry.name);
        assertEquals(ArcDarkConstants.PAIRUMU_DARK_PACK_VERSION, entry.version);
        assertEquals(cover, entry.coverFile);
        assertTrue(entry.available);
        assertTrue(entry.builtIn);
    }

    @Test
    public void listDoesNotExposeExternalPackWithBuiltInId() throws Exception {
        File root = temporaryFolder.newFolder("root");
        File packDir = ArcDarkPaths.packDir(root, ArcDarkConstants.PAIRUMU_DARK_PACK_ID);
        File metadataDir = new File(packDir, "arc_overrides");
        assertTrue(metadataDir.mkdirs());
        ArcDarkFileOps.writeUtf8(new File(packDir, "pack.json"), "{"
                + "\"formatVersion\":1,"
                + "\"id\":\"" + ArcDarkConstants.PAIRUMU_DARK_PACK_ID + "\","
                + "\"name\":\"External Collision\""
                + "}");
        ArcDarkFileOps.writeUtf8(new File(metadataDir, "index.json"), "{\"entries\":[]}");

        PackCatalog.Entry entry = find(PackCatalog.list(root), ArcDarkConstants.PAIRUMU_DARK_PACK_ID);

        assertNotNull(entry);
        assertEquals(ArcDarkConstants.PAIRUMU_DARK_PACK_NAME, entry.name);
        assertTrue(entry.builtIn);
    }

    private static PackCatalog.Entry find(List<PackCatalog.Entry> entries, String id) {
        for (PackCatalog.Entry entry : entries) {
            if (entry.id.equals(id)) {
                return entry;
            }
        }
        return null;
    }
}
