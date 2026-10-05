package com.fcareer.fitengine.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum WeaponState {
    GREEN(0),
    YELLOW(1),
    RED(2);

    private final int severity;

    public WeaponState worse(WeaponState other) {
        return other.severity > severity ? other : this;
    }
}
