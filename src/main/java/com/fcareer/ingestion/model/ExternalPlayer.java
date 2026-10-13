package com.fcareer.ingestion.model;

import com.fcareer.fitengine.model.Attr;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ExternalPlayer(
        long externalId,
        String fullName,
        LocalDate dob,
        String nationality,
        Integer heightCm,
        Integer weightKg,
        String preferredFoot,
        String leagueName,
        Long clubExternalId,
        String clubName,
        int overall,
        List<String> positions,
        Integer weakFoot,
        Integer skillMoves,
        Map<Attr, Integer> attrs,
        Map<String, Integer> playstyles,
        int gender,
        /** Sitenin resmî yüz istatistikleri (pac, sho, pas, dri, def, phy); yoksa null. */
        List<Integer> faceStats) {
}
