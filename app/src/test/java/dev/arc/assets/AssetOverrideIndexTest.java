package dev.arc.assets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Test;

public final class AssetOverrideIndexTest {
    @Test
    public void findNormalizesAssetsPrefix() throws Exception {
        AssetOverrideIndex index = loadIndex(
                entry("assets/img/note.png", "img/note.png", 3, "aaa")
        );

        assertNotNull(index.find("img/note.png"));
        assertNotNull(index.find("/assets/img/note.png"));
    }

    @Test
    public void mergeKeepsFirstIndexPriority() throws Exception {
        AssetOverrideIndex highPriority = loadIndex(
                entry("assets/img/track.png", "img/track-a.png", 1, "first")
        );
        AssetOverrideIndex lowPriority = loadIndex(
                entry("assets/img/track.png", "img/track-b.png", 1, "second")
        );

        AssetOverrideIndex merged = AssetOverrideIndex.merge(Arrays.asList(highPriority, lowPriority));

        assertEquals("first", merged.find("img/track.png").sha256);
        assertEquals("img/track-a.png", merged.find("img/track.png").modulePath);
    }

    @Test(expected = IllegalArgumentException.class)
    public void loadRejectsUnsafeIndexPath() throws Exception {
        loadIndex(entry("../img/note.png", "img/note.png", 3, "aaa"));
    }

    private static AssetOverrideIndex loadIndex(String entries) throws Exception {
        String json = "{\"entries\":[" + entries + "]}";
        return AssetOverrideIndex.load(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    private static String entry(String assetPath, String modulePath, long size, String sha256) {
        return "{"
                + "\"assetPath\":\"" + assetPath + "\","
                + "\"modulePath\":\"" + modulePath + "\","
                + "\"size\":" + size + ","
                + "\"sha256\":\"" + sha256 + "\","
                + "\"materialize\":true"
                + "}";
    }
}
