package com.fcareer.tacticcode.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum EaBuildUp {
    BALANCED(2, 0),
    COUNTER(3, 1),
    SHORT_PASSING(0, 2);

    private final int fieldValue;
    private final int checksumValue;
}
