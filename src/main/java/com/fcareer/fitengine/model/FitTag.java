package com.fcareer.fitengine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class FitTag {
    private final String id;
    private final String position;
    private final String labelTr;
    private final List<Attr> weaponAttrs;
    private final List<String> playstyleWeapons;
    private final int expectedMin;
    private final String roleVote;
    private final List<String> conflicts;
    private final String nlgTr;

    public Attr primaryWeapon() {
        return weaponAttrs.get(0);
    }
}
