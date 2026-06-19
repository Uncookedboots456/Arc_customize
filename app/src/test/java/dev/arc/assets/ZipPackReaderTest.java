package dev.arc.assets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class ZipPackReaderTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void readManifestReadsRootPackJson() throws Exception {
        PackManifest manifest = ZipPackReader.readManifest(streamOf(zip(
                entry("pack.json", manifestJson())
        )));

        assertEquals("sample_pack", manifest.id);
        assertEquals("Sample Pack", manifest.name);
        assertEquals("", manifest.version);
        assertEquals("", manifest.description);
        assertEquals("", manifest.author);
        assertEquals("", manifest.cover);
        assertTrue(manifest.changeMap.isEmpty());
        assertEquals(PackManifest.FORMAT_VERSION, manifest.formatVersion);
    }

    @Test
    public void readManifestReadsOptionalMetadata() throws Exception {
        PackManifest manifest = ZipPackReader.readManifest(streamOf(zip(
                entry("pack.json", manifestJsonWithMetadata())
        )));

        assertEquals("Small pack", manifest.description);
        assertEquals("2026.06", manifest.version);
        assertEquals("Arc Dark Team", manifest.author);
        assertEquals("cover.png", manifest.cover);
        assertEquals("fonts", manifest.changeMap.get("Default Fonts"));
    }

    @Test
    public void extractAssetsWritesSafeAssetsAndIndexEntries() throws Exception {
        File root = temporaryFolder.newFolder("pack");

        List<AssetOverride> entries = ZipPackReader.extractAssets(streamOf(zip(
                entry("pack.json", manifestJson()),
                entry("assets/img/track.png", "abc")
        )), root, manifest());

        assertEquals(1, entries.size());
        assertEquals("assets/img/track.png", entries.get(0).assetPath);
        assertEquals("assets/img/track.png", entries.get(0).modulePath);
        assertEquals(3, entries.get(0).size);
        assertTrue(new File(root, "assets/img/track.png").isFile());
    }

    @Test
    public void extractAssetsAppliesChangeMapToTargetAssetPath() throws Exception {
        File root = temporaryFolder.newFolder("mapped");

        List<AssetOverride> entries = ZipPackReader.extractAssets(streamOf(zip(
                entry("pack.json", manifestJsonWithMetadata()),
                entry("cover.png", "png"),
                entry("assets/Default Fonts/font.ttf", "abc")
        )), root, manifest(manifestJsonWithMetadata()));

        assertEquals(1, entries.size());
        assertEquals("assets/fonts/font.ttf", entries.get(0).assetPath);
        assertEquals("assets/Default Fonts/font.ttf", entries.get(0).modulePath);
        assertTrue(new File(root, "assets/Default Fonts/font.ttf").isFile());
    }

    @Test
    public void extractAssetsCopiesCoverOutsideIndex() throws Exception {
        File root = temporaryFolder.newFolder("cover");

        List<AssetOverride> entries = ZipPackReader.extractAssets(streamOf(zip(
                entry("pack.json", manifestJsonWithMetadata()),
                entry("cover.png", "png"),
                entry("assets/img/track.png", "abc")
        )), root, manifest(manifestJsonWithMetadata()));

        assertEquals(1, entries.size());
        assertEquals("assets/img/track.png", entries.get(0).assetPath);
        assertTrue(new File(root, "cover.png").isFile());
    }

    @Test(expected = IllegalArgumentException.class)
    public void readManifestRejectsMissingPackJson() throws Exception {
        ZipPackReader.readManifest(streamOf(zip(entry("assets/img/track.png", "abc"))));
    }

    @Test(expected = IllegalArgumentException.class)
    public void readManifestRejectsUnsafeCover() throws Exception {
        ZipPackReader.readManifest(streamOf(zip(entry(
                "pack.json",
                "{"
                        + "\"formatVersion\":1,"
                        + "\"id\":\"sample_pack\","
                        + "\"name\":\"Sample Pack\","
                        + "\"cover\":\"../cover.png\""
                        + "}"
        ))));
    }

    @Test(expected = IllegalArgumentException.class)
    public void readManifestRejectsOversizedPackJson() throws Exception {
        StringBuilder oversized = new StringBuilder();
        for (int i = 0; i < 70 * 1024; i++) {
            oversized.append('x');
        }
        ZipPackReader.readManifest(streamOf(zip(entry("pack.json", oversized.toString()))));
    }

    @Test(expected = IllegalArgumentException.class)
    public void extractAssetsRejectsDirectoryTraversal() throws Exception {
        ZipPackReader.extractAssets(streamOf(zip(
                entry("pack.json", manifestJson()),
                entry("assets/../evil.png", "abc")
        )), temporaryFolder.newFolder("traversal"), manifest());
    }

    @Test(expected = IllegalArgumentException.class)
    public void extractAssetsRejectsUnknownAssetFolder() throws Exception {
        ZipPackReader.extractAssets(streamOf(zip(
                entry("pack.json", manifestJson()),
                entry("assets/unknown/file.png", "abc")
        )), temporaryFolder.newFolder("unknown"), manifest());
    }

    @Test(expected = IllegalArgumentException.class)
    public void extractAssetsRejectsNonAssetFile() throws Exception {
        ZipPackReader.extractAssets(streamOf(zip(
                entry("pack.json", manifestJson()),
                entry("img/track.png", "abc")
        )), temporaryFolder.newFolder("non-asset"), manifest());
    }

    private static ByteArrayInputStream streamOf(byte[] bytes) {
        return new ByteArrayInputStream(bytes);
    }

    private static ZipSource entry(String name, String content) {
        return new ZipSource(name, content.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] zip(ZipSource... sources) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (ZipSource source : sources) {
                zip.putNextEntry(new ZipEntry(source.name));
                zip.write(source.bytes);
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private static PackManifest manifest() throws Exception {
        return manifest(manifestJson());
    }

    private static PackManifest manifest(String json) throws Exception {
        return PackManifest.readRequired(streamOf(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static String manifestJson() {
        return "{"
                + "\"formatVersion\":1,"
                + "\"id\":\"sample_pack\","
                + "\"name\":\"Sample Pack\""
                + "}";
    }

    private static String manifestJsonWithMetadata() {
        return "{"
                + "\"formatVersion\":1,"
                + "\"id\":\"sample_pack\","
                + "\"name\":\"Sample Pack\","
                + "\"version\":\"2026.06\","
                + "\"description\":\"Small pack\","
                + "\"author\":\"Arc Dark Team\","
                + "\"cover\":\"cover.png\","
                + "\"Change\":{\"Default Fonts\":\"fonts\"}"
                + "}";
    }

    private static final class ZipSource {
        final String name;
        final byte[] bytes;

        ZipSource(String name, byte[] bytes) {
            this.name = name;
            this.bytes = bytes;
        }
    }
}
