package com.fcareer.tacticcode.model;

import java.util.List;

public record EaTactic(String formationId, EaBuildUp buildUp, int lineHeight, List<EaSlot> slots) {

    public record EaSlot(int roleId, int focusIndex) {
    }
}
