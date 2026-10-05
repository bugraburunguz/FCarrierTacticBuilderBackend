package com.fcareer.fitengine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class Weapon {
    private final Attr attr;
    private final int hardMin;
    private final int target;
}
