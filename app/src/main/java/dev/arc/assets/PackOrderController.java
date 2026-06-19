package dev.arc.assets;

import java.util.ArrayList;
import java.util.List;

final class PackOrderController {
    private PackOrderController() {
    }

    static ArcDarkControl.Control setPackEnabled(
            ArcDarkControl.Control control,
            String packId,
            boolean enabled
    ) {
        List<String> order = new ArrayList<>(control.activePackOrder);
        if (enabled) {
            if (!order.contains(packId)) {
                order.add(packId);
            }
        } else {
            order.remove(packId);
        }
        return control.withActivePackOrder(order);
    }

    static ArcDarkControl.Control movePack(
            ArcDarkControl.Control control,
            String packId,
            int direction
    ) {
        List<String> order = new ArrayList<>(control.activePackOrder);
        int index = order.indexOf(packId);
        int nextIndex = index + direction;
        if (index < 0 || nextIndex < 0 || nextIndex >= order.size()) {
            return control;
        }
        String moving = order.remove(index);
        order.add(nextIndex, moving);
        return control.withActivePackOrder(order);
    }

    static ArcDarkControl.Control withPackAtFront(ArcDarkControl.Control control, String packId) {
        return control.withPackAtFront(packId);
    }
}
