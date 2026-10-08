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
}
