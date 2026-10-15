package com.fcareer.ut;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.ut.model.UtCaptureModels.SbcChallengeView;
import com.fcareer.ut.model.UtCaptureModels.SbcRequirement;
import com.fcareer.ut.service.SbcChallengeParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SbcChallengeParserTest {

    private static final String MARQUEE = """
            {"challenges":[{"name":"Como 1907 v AS Roma","status":"NOT_STARTED","setId":36,"challengeId":67,"endTime":580410,"repeatable":false,"formation":"f451","timesCompleted":0,
            "elgReq":[
              {"type":"PLAYER_COUNT","eligibilitySlot":1,"eligibilityKey":2,"eligibilityValue":1},{"type":"SCOPE","eligibilitySlot":1,"eligibilityKey":13,"eligibilityValue":0},
              {"type":"CLUB_ID","eligibilitySlot":1,"eligibilityKey":12,"eligibilityValue":1745},{"type":"CLUB_ID","eligibilitySlot":1,"eligibilityKey":12,"eligibilityValue":52},
              {"type":"PLAYER_COUNT","eligibilitySlot":2,"eligibilityKey":2,"eligibilityValue":2},{"type":"NATION_ID","eligibilitySlot":2,"eligibilityKey":10,"eligibilityValue":27},{"type":"SCOPE","eligibilitySlot":2,"eligibilityKey":13,"eligibilityValue":0},
              {"type":"LEAGUE_COUNT","eligibilitySlot":3,"eligibilityKey":8,"eligibilityValue":4},{"type":"SCOPE","eligibilitySlot":3,"eligibilityKey":13,"eligibilityValue":1},
              {"type":"PLAYER_COUNT","eligibilitySlot":4,"eligibilityKey":2,"eligibilityValue":1},{"type":"PLAYER_LEVEL","eligibilitySlot":4,"eligibilityKey":17,"eligibilityValue":3},{"type":"SCOPE","eligibilitySlot":4,"eligibilityKey":13,"eligibilityValue":0},
              {"type":"PLAYER_QUALITY","eligibilitySlot":5,"eligibilityKey":3,"eligibilityValue":2},{"type":"SCOPE","eligibilitySlot":5,"eligibilityKey":13,"eligibilityValue":0},
              {"type":"SAME_CLUB_COUNT","eligibilitySlot":6,"eligibilityKey":6,"eligibilityValue":3},{"type":"SCOPE","eligibilitySlot":6,"eligibilityKey":13,"eligibilityValue":1},
              {"type":"TEAM_RATING_1_TO_100","eligibilitySlot":7,"eligibilityKey":19,"eligibilityValue":75},{"type":"SCOPE","eligibilitySlot":7,"eligibilityKey":13,"eligibilityValue":0},
              {"type":"CHEMISTRY_POINTS","eligibilitySlot":8,"eligibilityKey":35,"eligibilityValue":18},{"type":"SCOPE","eligibilitySlot":8,"eligibilityKey":13,"eligibilityValue":0}],
            "awards":[{"value":519,"type":"pack","count":1}],"type":"OPEN_CHALLENGE"}]}
            """;

    @Test
    void normalizesRequirementsGroupedBySlot() throws Exception {
        JsonNode body = new ObjectMapper().readTree(MARQUEE);
        Map<String, String> clubs = Map.of("1745", "Como", "52", "AS Roma");
        SbcChallengeView challenge = SbcChallengeParser.parse(36, "Marquee Matchups", body,
                (kind, id) -> kind.equals("CLUB") ? clubs.get(String.valueOf(id)) : null, Map.of("f451", "4-5-1")).get(0);

        assertThat(challenge.name()).isEqualTo("Como 1907 v AS Roma");
        assertThat(challenge.formationLabel()).isEqualTo("4-5-1");
        assertThat(challenge.awards()).hasSize(1);
        assertThat(challenge.requirements()).hasSize(8);

        SbcRequirement from = challenge.requirements().get(0);
        assertThat(from.kind()).isEqualTo("FROM");
        assertThat(from.op()).isEqualTo("MIN");
        assertThat(from.value()).isEqualTo(1);
        assertThat(from.refs()).extracting("name").containsExactly("Como", "AS Roma");

        SbcRequirement nation = challenge.requirements().get(1);
        assertThat(nation.refs()).singleElement().satisfies(r -> {
            assertThat(r.kind()).isEqualTo("NATION");
            assertThat(r.id()).isEqualTo(27);
            assertThat(r.name()).isNull();
        });

        assertThat(challenge.requirements().get(2)).satisfies(r -> {
            assertThat(r.kind()).isEqualTo("LEAGUE_COUNT");
            assertThat(r.op()).isEqualTo("MAX");
            assertThat(r.value()).isEqualTo(4);
        });
        assertThat(challenge.requirements().get(3)).satisfies(r -> {
            assertThat(r.kind()).isEqualTo("LEVEL_COUNT");
            assertThat(r.level()).isEqualTo(3);
            assertThat(r.value()).isEqualTo(1);
        });
        assertThat(challenge.requirements().get(4).kind()).isEqualTo("QUALITY");
        assertThat(challenge.requirements().get(5)).satisfies(r -> {
            assertThat(r.kind()).isEqualTo("SAME_CLUB");
            assertThat(r.op()).isEqualTo("MAX");
        });
        assertThat(challenge.requirements().get(6).kind()).isEqualTo("TEAM_RATING");
        assertThat(challenge.requirements().get(7).kind()).isEqualTo("CHEMISTRY");
    }
    private static java.util.List<SbcRequirement> parseElg(String elgReq) throws Exception {
        JsonNode body = new ObjectMapper().readTree("{\"challenges\":[{\"challengeId\":1,\"name\":\"t\",\"elgReq\":" + elgReq + "}]}");
        return SbcChallengeParser.parse(1, "s", body, (kind, id) -> null, Map.of()).get(0).requirements();
    }

    private static String req(String type, int slot, int value) {
        return "{\"type\":\"" + type + "\",\"eligibilitySlot\":" + slot + ",\"eligibilityValue\":" + value + "}";
    }

    @Test
    void mainTypeKeepsItsPlayerCount() throws Exception {
        java.util.List<SbcRequirement> result = parseElg("[" + req("PLAYER_RARITY_GROUP", 1, 4) + "," + req("PLAYER_COUNT", 1, 1) + "," + req("SCOPE", 1, 0) + "]");

        assertThat(result).singleElement().satisfies(r -> {
            assertThat(r.kind()).isEqualTo("RARITY_GROUP");
            assertThat(r.value()).isEqualTo(4);
            assertThat(r.count()).isEqualTo(1);
            assertThat(r.op()).isEqualTo("MIN");
        });
    }

    @Test
    void everyKnownEaTypeMapsToANamedKind() throws Exception {
        Map<String, String> expected = Map.of("PLAYER_RARITY", "RARITY", "PLAYER_MIN_OVR", "MIN_OVR", "PLAYER_EXACT_OVR", "EXACT_OVR", "PLAYER_MAX_OVR", "MAX_OVR",
                "PLAYER_TRADABILITY", "TRADABILITY", "TEAM_STAR_RATING", "TEAM_STAR_RATING", "ALL_PLAYERS_CHEMISTRY_POINTS", "CHEMISTRY_PER_PLAYER",
                "FIRST_OWNER_PLAYERS_COUNT", "FIRST_OWNER", "LEGEND_COUNT", "LEGEND_COUNT", "PLAYER_COUNT_COMBINED", "PLAYER_COUNT");
        for (Map.Entry<String, String> e : expected.entrySet()) {
            assertThat(parseElg("[" + req(e.getKey(), 1, 3) + "," + req("SCOPE", 1, 0) + "]")).as(e.getKey()).singleElement()
                    .satisfies(r -> assertThat(r.kind()).isEqualTo(e.getValue()));
        }
    }

    /** V3-05c-c: gerçek yakalamada görülen 16 elgReq tipinin her biri tanınır (yapı tipleri SCOPE/PLAYER_COUNT/PLAYER_LEVEL/*_ID ayrı testte). */
    @org.junit.jupiter.params.ParameterizedTest(name = "{0} → {1}")
    @org.junit.jupiter.params.provider.CsvSource({
            "PLAYER_ATTRIBUTE, PLAYER_ATTRIBUTE",
            "ACADEMY_PLAYER_SLOTTING, ACADEMY_SLOTTING",
            "PLAYER_QUALITY, QUALITY",
            "CHEMISTRY_POINTS, CHEMISTRY",
            "CLUB_COUNT, CLUB_COUNT",
            "LEAGUE_COUNT, LEAGUE_COUNT",
            "SAME_LEAGUE_COUNT, SAME_LEAGUE",
            "SAME_NATION_COUNT, SAME_NATION",
            "TEAM_RATING_1_TO_100, TEAM_RATING",
            "SAME_CLUB_COUNT, SAME_CLUB"})
    void observedMainTypesMapToNamedKinds(String eaType, String kind) throws Exception {
        assertThat(parseElg("[" + req(eaType, 1, 2) + "," + req("SCOPE", 1, 0) + "]")).singleElement().satisfies(r -> {
            assertThat(r.kind()).isEqualTo(kind);
            assertThat(r.value()).isEqualTo(2);
            assertThat(r.raw()).isNotEmpty();
        });
    }

    @Test
    void observedStructuralTypesBuildTheirRequirement() throws Exception {
        assertThat(parseElg("[" + req("PLAYER_COUNT", 1, 11) + "," + req("SCOPE", 1, 2) + "]")).singleElement().satisfies(r -> {
            assertThat(r.kind()).isEqualTo("PLAYER_COUNT");
            assertThat(r.op()).isEqualTo("EXACT");
        });
        assertThat(parseElg("[" + req("PLAYER_COUNT", 1, 1) + "," + req("PLAYER_LEVEL", 1, 3) + "]")).singleElement().satisfies(r -> assertThat(r.kind()).isEqualTo("LEVEL_COUNT"));
        for (String ref : java.util.List.of("CLUB_ID", "LEAGUE_ID", "NATION_ID")) {
            assertThat(parseElg("[" + req("PLAYER_COUNT", 1, 2) + "," + req(ref, 1, 77) + "]")).as(ref).singleElement().satisfies(r -> {
                assertThat(r.kind()).isEqualTo("FROM");
                assertThat(r.refs()).singleElement().satisfies(x -> assertThat(x.id()).isEqualTo(77));
            });
        }
    }

    @Test
    void unknownTypeIsKeptAsUnknownWithItsEaTypeAndRawValue() throws Exception {
        assertThat(parseElg("[" + req("SOMETHING_NEW", 1, 5) + "]")).singleElement().satisfies(r -> {
            assertThat(r.kind()).isEqualTo("UNKNOWN");
            assertThat(r.eaType()).isEqualTo("SOMETHING_NEW");
            assertThat(r.value()).isEqualTo(5);
        });
        // tanınmayan tip, aynı slottaki bilinen yapı tiplerinin yanında da düşmez
        assertThat(parseElg("[" + req("SOMETHING_NEW", 1, 5) + "," + req("PLAYER_COUNT", 1, 2) + "," + req("SCOPE", 1, 1) + "]")).singleElement()
                .satisfies(r -> assertThat(r.eaType()).isEqualTo("SOMETHING_NEW"));
    }

    @Test
    void rewardItemDataIsKeptThroughTheAllowlist() throws Exception {
        JsonNode body = new ObjectMapper().readTree("{\"challenges\":[{\"challengeId\":1,\"name\":\"t\",\"elgReq\":[],\"awards\":[{\"type\":\"item\",\"value\":50000777,\"count\":1,\"itemData\":"
                + RewardItemFixture.ITEM_DATA + "}]}]}");
        SbcChallengeView challenge = SbcChallengeParser.parse(1, "s", body, (kind, id) -> null, Map.of()).get(0);
        assertThat(challenge.awards()).singleElement().satisfies(a -> {
            assertThat(a.item().assetId()).isEqualTo(777L);
            assertThat(a.item().possiblePositions()).containsExactly("ST", "CF");
        });
    }

    @Test
    void secondMainTypeInTheSameSlotDoesNotOverwriteTheFirst() throws Exception {
        java.util.List<SbcRequirement> result = parseElg("[" + req("PLAYER_RARITY", 1, 3) + "," + req("PLAYER_MIN_OVR", 1, 80) + "," + req("PLAYER_COUNT", 1, 2) + "," + req("SCOPE", 1, 0) + "]");

        assertThat(result).extracting(SbcRequirement::kind).containsExactly("RARITY", "MIN_OVR");
        assertThat(result).extracting(SbcRequirement::count).containsExactly(2, 2);
    }

    @Test
    void unknownTypesKeepTheirRawFormAndEveryRequirementCarriesRaw() throws Exception {
        java.util.List<SbcRequirement> result = parseElg("[" + req("BRAND_NEW_KEY", 1, 9) + "," + req("SCOPE", 1, 0) + "]");

        assertThat(result).singleElement().satisfies(r -> {
            assertThat(r.kind()).isEqualTo("UNKNOWN");
            assertThat(r.raw()).hasSize(2);
            assertThat(r.raw().get(0).path("type").asText()).isEqualTo("BRAND_NEW_KEY");
        });
    }
}
