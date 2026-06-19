package dev.arc.assets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class PackPathSecurityTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void externalPackIdRejectsDirectoryAliases() {
        assertFalse(ArcDarkControl.isExternalPackId("."));
        assertFalse(ArcDarkControl.isExternalPackId(".."));
        assertEquals(ArcDarkConstants.DEFAULT_PACK_ID, ArcDarkControl.sanitizePackId(".."));
    }

    @Test(expected = IllegalStateException.class)
    public void packDirRejectsPacksRootAlias() throws Exception {
        File root = temporaryFolder.newFolder("root");

        ArcDarkPaths.packDir(root, ".");
    }

    @Test(expected = IllegalStateException.class)
    public void packDirRejectsParentTraversal() throws Exception {
        File root = temporaryFolder.newFolder("root");

        ArcDarkPaths.packDir(root, "..");
    }
}
