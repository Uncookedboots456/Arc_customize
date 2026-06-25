package dev.arc.assets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public final class ArcDarkControlTest {
    @Test
    public void defaultsUseOriginalAssets() {
        ArcDarkControl.Control control = ArcDarkControl.defaults();

        assertTrue(control.injectionEnabled);
        assertEquals(Collections.emptyList(), control.activePackOrder);
    }

    @Test
    public void readJsonIgnoresRemovedTestPackId() throws Exception {
        ArcDarkControl.Control control = ArcDarkControl.readJson(
                "{"
                        + "\"injection_enabled\":false,"
                        + "\"active_pack_id\":\"test_pkg\""
                        + "}"
        );

        assertFalse(control.injectionEnabled);
        assertEquals(Collections.emptyList(), control.activePackOrder);
    }

    @Test
    public void readJsonDoesNotFallBackToRemovedTestPackIdWhenOrderIsInvalid() throws Exception {
        ArcDarkControl.Control control = ArcDarkControl.readJson(
                "{"
                        + "\"active_pack_id\":\"test_pkg\","
                        + "\"active_pack_order\":[\"bad/slash\"]"
                        + "}"
        );

        assertEquals(Collections.emptyList(), control.activePackOrder);
    }

    @Test
    public void readJsonSanitizesAndDedupesPackOrder() throws Exception {
        ArcDarkControl.Control control = ArcDarkControl.readJson(
                "{"
                        + "\"active_pack_order\":["
                        + "\"bad/slash\","
                        + "\"" + ArcDarkConstants.DEFAULT_PACK_ID + "\","
                        + "\"" + ArcDarkConstants.PAIRUMU_DARK_PACK_ID + "\","
                        + "\"sample_pack\","
                        + "\"pending.tmp\""
                        + "]"
                        + "}"
        );

        assertEquals(
                Arrays.asList(ArcDarkConstants.PAIRUMU_DARK_PACK_ID, "sample_pack"),
                control.activePackOrder
        );
    }

    @Test
    public void builtInPackIdIsAllowedButNotExternal() {
        assertTrue(ArcDarkControl.isAllowedPackId(ArcDarkConstants.PAIRUMU_DARK_PACK_ID));
        assertTrue(ArcDarkControl.isBuiltInPackId(ArcDarkConstants.PAIRUMU_DARK_PACK_ID));
        assertFalse(ArcDarkControl.isExternalPackId(ArcDarkConstants.PAIRUMU_DARK_PACK_ID));
    }

    @Test
    public void removedTestPackIdIsNotAllowedOrExternal() {
        assertFalse(ArcDarkControl.isAllowedPackId("test_pkg"));
        assertFalse(ArcDarkControl.isExternalPackId("test_pkg"));
    }
}
