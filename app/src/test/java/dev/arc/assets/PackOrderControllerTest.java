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
                PackOrderController.setPackEnabled(control, "sample_pack", true);
        ArcDarkControl.Control disabled =
                PackOrderController.setPackEnabled(enabled, "sample_pack", false);

        assertEquals(
                Collections.singletonList("sample_pack"),
                enabled.activePackOrder
        );
        assertEquals(Collections.<String>emptyList(), disabled.activePackOrder);
    }

    @Test
    public void setPackEnabledDoesNotDuplicateExistingPack() {
        ArcDarkControl.Control control = new ArcDarkControl.Control(
                true,
                Collections.singletonList("sample_pack")
        );

        ArcDarkControl.Control next =
                PackOrderController.setPackEnabled(control, "sample_pack", true);

        assertEquals(Collections.singletonList("sample_pack"), next.activePackOrder);
    }

    @Test
    public void movePackReordersWithinBoundsOnly() {
        ArcDarkControl.Control control = new ArcDarkControl.Control(
                true,
                Arrays.asList(ArcDarkConstants.PAIRUMU_DARK_PACK_ID, "sample_pack")
        );

        ArcDarkControl.Control moved =
                PackOrderController.movePack(control, "sample_pack", -1);
        ArcDarkControl.Control unchanged =
                PackOrderController.movePack(moved, "sample_pack", -1);

        assertEquals(
                Arrays.asList("sample_pack", ArcDarkConstants.PAIRUMU_DARK_PACK_ID),
                moved.activePackOrder
        );
        assertEquals(moved.activePackOrder, unchanged.activePackOrder);
    }

    @Test
    public void withPackAtFrontMovesExistingPackWithoutDuplicate() {
        ArcDarkControl.Control control = new ArcDarkControl.Control(
                true,
                Arrays.asList("sample_pack", ArcDarkConstants.PAIRUMU_DARK_PACK_ID)
        );

        ArcDarkControl.Control next = PackOrderController.withPackAtFront(control, ArcDarkConstants.PAIRUMU_DARK_PACK_ID);

        assertEquals(
                Arrays.asList(ArcDarkConstants.PAIRUMU_DARK_PACK_ID, "sample_pack"),
                next.activePackOrder
        );
    }
}
