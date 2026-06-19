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
    public void readJsonSupportsLegacyActivePackId() throws Exception {
        ArcDarkControl.Control control = ArcDarkControl.readJson(
                "{"
                        + "\"injection_enabled\":false,"
                        + "\"active_pack_id\":\"" + ArcDarkConstants.TEST_PACK_ID + "\""
                        + "}"
        );

        assertFalse(control.injectionEnabled);
        assertEquals(Collections.singletonList(ArcDarkConstants.TEST_PACK_ID), control.activePackOrder);
    }

    @Test
    public void readJsonFallsBackToLegacyActivePackIdWhenOrderIsInvalid() throws Exception {
        ArcDarkControl.Control control = ArcDarkControl.readJson(
                "{"
                        + "\"active_pack_id\":\"" + ArcDarkConstants.TEST_PACK_ID + "\","
                        + "\"active_pack_order\":[\"bad/slash\"]"
                        + "}"
        );

        assertEquals(Collections.singletonList(ArcDarkConstants.TEST_PACK_ID), control.activePackOrder);
    }

    @Test
    public void readJsonSanitizesAndDedupesPackOrder() throws Exception {
        ArcDarkControl.Control control = ArcDarkControl.readJson(
                "{"
                        + "\"active_pack_order\":["
                        + "\"bad/slash\","
                        + "\"" + ArcDarkConstants.DEFAULT_PACK_ID + "\","
                        + "\"sample_pack\","
                        + "\"pending.tmp\""
                        + "]"
                        + "}"
        );

        assertEquals(
                Collections.singletonList("sample_pack"),
                control.activePackOrder
        );
    }
}
