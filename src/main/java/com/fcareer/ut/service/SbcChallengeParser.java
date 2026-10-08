package com.fcareer.ut.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fcareer.ut.model.UtCaptureModels.Reward;
import com.fcareer.ut.model.UtCaptureModels.SbcChallengeView;
import com.fcareer.ut.model.UtCaptureModels.SbcRef;
import com.fcareer.ut.model.UtCaptureModels.SbcRequirement;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.BiFunction;

/**
 * /sbs/setId/{id}/challenges yanıtındaki elgReq listesini normalleştirir.
 * Gözlenen düzen: aynı eligibilitySlot altındaki öğeler tek bir şartı oluşturur; SCOPE (0 en az, 1 en çok, 2 tam) şartın yönüdür;
 * PLAYER_COUNT + CLUB_ID/LEAGUE_ID/NATION_ID "bu kulüp/lig/ülkeden en az N oyuncu", PLAYER_COUNT + PLAYER_LEVEL "en az N oyuncu bu seviyede".
 */
public final class SbcChallengeParser {

    static final String SCOPE = "SCOPE";
    private static final int SCOPE_MAX = 1;
    private static final int SCOPE_EXACT = 2;

    private SbcChallengeParser() {
    }

    public static List<SbcChallengeView> parse(long setId, String setName, JsonNode body, BiFunction<String, Integer, String> refName,
                                               Map<String, String> formationLabels) {
        List<SbcChallengeView> result = new ArrayList<>();
        for (JsonNode challenge : body.path("challenges")) {
            List<Reward> awards = new ArrayList<>();
            challenge.path("awards").forEach(a -> awards.add(new Reward(a.path("type").asText(null), a.hasNonNull("value") ? a.get("value").asLong() : null, a.path("count").asInt(1))));
            String formation = challenge.path("formation").asText(null);
            result.add(new SbcChallengeView(setId, setName, challenge.path("challengeId").asLong(), challenge.path("name").asText(null),
                    challenge.path("status").asText(null), challenge.path("type").asText(null), formation, formationLabels.get(formation),
                    challenge.path("repeatable").asBoolean(), challenge.path("timesCompleted").asInt(), challenge.hasNonNull("endTime") ? challenge.get("endTime").asLong() : null,
                    awards, requirements(challenge.path("elgReq"), refName)));
        }
        return result;
    }

    static List<SbcRequirement> requirements(JsonNode elgReq, BiFunction<String, Integer, String> refName) {
        Map<Integer, List<JsonNode>> bySlot = new TreeMap<>();
        elgReq.forEach(r -> bySlot.computeIfAbsent(r.path("eligibilitySlot").asInt(), k -> new ArrayList<>()).add(r));
        List<SbcRequirement> result = new ArrayList<>();
        for (List<JsonNode> group : bySlot.values()) {
            String op = "MIN";
            String main = null;
            Integer mainValue = null;
            Integer count = null;
            Integer level = null;
            List<SbcRef> refs = new ArrayList<>();
            for (JsonNode r : group) {
                String type = r.path("type").asText();
                int value = r.path("eligibilityValue").asInt();
                switch (type) {
                    case SCOPE -> op = value == SCOPE_MAX ? "MAX" : value == SCOPE_EXACT ? "EXACT" : "MIN";
                    case "CLUB_ID" -> refs.add(new SbcRef("CLUB", value, refName.apply("CLUB", value)));
                    case "LEAGUE_ID" -> refs.add(new SbcRef("LEAGUE", value, refName.apply("LEAGUE", value)));
                    case "NATION_ID" -> refs.add(new SbcRef("NATION", value, refName.apply("NATION", value)));
                    case "PLAYER_COUNT" -> count = value;
                    case "PLAYER_LEVEL" -> level = value;
                    default -> {
                        main = type;
                        mainValue = value;
                    }
                }
            }
            result.add(requirement(op, main, mainValue, count, level, refs));
        }
        return result;
    }

    private static SbcRequirement requirement(String op, String main, Integer mainValue, Integer count, Integer level, List<SbcRef> refs) {
        if (Objects.isNull(main)) {
            if (!refs.isEmpty()) {
                return new SbcRequirement("FROM", op, count, null, refs);
            }
            if (Objects.nonNull(level)) {
                return new SbcRequirement("LEVEL_COUNT", op, count, level, List.of());
            }
            return new SbcRequirement("UNKNOWN", op, count, null, List.of());
        }
        String kind = switch (main) {
            case "TEAM_RATING_1_TO_100" -> "TEAM_RATING";
            case "CHEMISTRY_POINTS" -> "CHEMISTRY";
            case "SAME_CLUB_COUNT" -> "SAME_CLUB";
            case "SAME_LEAGUE_COUNT" -> "SAME_LEAGUE";
            case "SAME_NATION_COUNT" -> "SAME_NATION";
            case "CLUB_COUNT" -> "CLUB_COUNT";
            case "LEAGUE_COUNT" -> "LEAGUE_COUNT";
            case "NATION_COUNT" -> "NATION_COUNT";
            case "PLAYER_QUALITY" -> "QUALITY";
            default -> "UNKNOWN:" + main;
        };
        return new SbcRequirement(kind, op, mainValue, level, refs);
    }
}
