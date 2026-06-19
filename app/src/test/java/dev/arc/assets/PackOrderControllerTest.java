package dev.arc.assets;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public final class PackOrderControllerTest {
    @Test
    public void setPackEnabledAddsAndRemovesPack() {
        ArcDarkControl.Control control = new ArcDarkControl.Control(
                true,
                Collections.<String>emptyList()
        );

        ArcDarkControl.Control enabled =
                PackOrderController.setPackEnabled(control, ArcDarkConstants.TEST_PACK_ID, true);
        ArcDarkControl.Control disabled =
                PackOrderController.setPackEnabled(enabled, ArcDarkConstants.TEST_PACK_ID, false);

        assertEquals(
                Collections.singletonList(ArcDarkConstants.TEST_PACK_ID),
                enabled.activePackOrder
        );
        assertEquals(Collections.<String>emptyList(), disabled.activePackOrder);
    }

    @Test
    public void setPackEnabledDoesNotDuplicateExistingPack() {
        ArcDarkControl.Control control = new ArcDarkControl.Control(
                true,
                Collections.singletonList(ArcDarkConstants.TEST_PACK_ID)
        );

        ArcDarkControl.Control next =
                PackOrderController.setPackEnabled(control, ArcDarkConstants.TEST_PACK_ID, true);

        assertEquals(Collections.singletonList(ArcDarkConstants.TEST_PACK_ID), next.activePackOrder);
    }

    @Test
    public void movePackReordersWithinBoundsOnly() {
        ArcDarkControl.Control control = new ArcDarkControl.Control(
                true,
                Arrays.asList(ArcDarkConstants.TEST_PACK_ID, "sample_pack")
        );

        ArcDarkControl.Control moved =
                PackOrderController.movePack(control, "sample_pack", -1);
        ArcDarkControl.Control unchanged =
                PackOrderController.movePack(moved, "sample_pack", -1);

        assertEquals(
                Arrays.asList("sample_pack", ArcDarkConstants.TEST_PACK_ID),
                moved.activePackOrder
        );
        assertEquals(moved.activePackOrder, unchanged.activePackOrder);
    }

    @Test
    public void withPackAtFrontMovesExistingPackWithoutDuplicate() {
        ArcDarkControl.Control control = new ArcDarkControl.Control(
                true,
                Arrays.asList("sample_pack", ArcDarkConstants.TEST_PACK_ID)
        );

        ArcDarkControl.Control next = PackOrderController.withPackAtFront(control, ArcDarkConstants.TEST_PACK_ID);

        assertEquals(
                Arrays.asList(ArcDarkConstants.TEST_PACK_ID, "sample_pack"),
                next.activePackOrder
        );
    }
}
