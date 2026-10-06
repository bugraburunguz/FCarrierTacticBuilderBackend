package com.fcareer.tacticcode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.tacticcode.model.EaBuildUp;
import com.fcareer.tacticcode.model.EaTactic;
import com.fcareer.tacticcode.model.EaTactic.EaSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EaTacticCodecTest {

    private static final String F442_DEFAULT = "1.0,3.0,11.0,11.0,7.0,23.0,18.0,18.0,27.0,41.0,41.0";

    private EaTacticCodec codec;

    @BeforeEach
    void setUp() {
        codec = new EaTacticCodec(new ObjectMapper());
        codec.load();
    }

    private static EaTactic tactic(String formation, EaBuildUp buildUp, int lineHeight, String roles) {
        List<EaSlot> slots = Arrays.stream(roles.split(",")).map(r -> {
            String[] p = r.split("\\.");
            return new EaSlot(Integer.parseInt(p[0]), Integer.parseInt(p[1]));
        }).toList();
        return new EaTactic(formation, buildUp, lineHeight, slots);
    }

    private static int[] digits(String csv) {
        return Arrays.stream(csv.split(",")).mapToInt(Integer::parseInt).toArray();
    }

    @Test
    void encodesTheCodesThatCameFromTheGameExactly() {
        assertThat(codec.encode(tactic("f442", EaBuildUp.BALANCED, 50, F442_DEFAULT))).isEqualTo("n4BxAr3vxFDn");
        assertThat(codec.encode(tactic("f442", EaBuildUp.BALANCED, 60, F442_DEFAULT))).isEqualTo("x4L7K1B57Pdy");
        assertThat(codec.encode(tactic("f442", EaBuildUp.BALANCED, 30, F442_DEFAULT))).isEqualTo("T4tdsXjbdxPQ");
        assertThat(codec.encode(tactic("f442", EaBuildUp.BALANCED, 10, F442_DEFAULT))).isEqualTo("@4ZJYDPHJdb6");
        assertThat(codec.encode(tactic("f442", EaBuildUp.BALANCED, 70, F442_DEFAULT))).isEqualTo("75VFU@LDFZ39");
        assertThat(codec.encode(tactic("f442", EaBuildUp.BALANCED, 87, F442_DEFAULT))).isEqualTo("M5mW$QcUWqQQ");
        assertThat(codec.encode(tactic("f442", EaBuildUp.BALANCED, 95, F442_DEFAULT))).isEqualTo("U5uetYkceyYZ");
        assertThat(codec.encode(tactic("f442", EaBuildUp.BALANCED, 99, F442_DEFAULT))).isEqualTo("Y5yixcogi28e");
        assertThat(codec.encode(tactic("f442", EaBuildUp.COUNTER, 50, F442_DEFAULT))).isEqualTo("o4CyBs4wyGE4");
        assertThat(codec.encode(tactic("f442", EaBuildUp.COUNTER, 60, F442_DEFAULT))).isEqualTo("y4M8L2C68QeD");
        assertThat(codec.encode(tactic("f442", EaBuildUp.COUNTER, 30, F442_DEFAULT))).isEqualTo("U4uetYkceyQh");
    }

    @Test
    void encodesOtherFormationsAndRolesFromTheGame() {
        assertThat(codec.encode(tactic("f4411a", EaBuildUp.BALANCED, 60, "2.0,3.0,11.0,11.0,7.0,26.0,18.0,18.0,30.0,32.0,43.1"))).isEqualTo("AJawHEA#K?tB");
        assertThat(codec.encode(tactic("f4231a", EaBuildUp.BALANCED, 50, "2.0,3.0,11.0,11.0,7.0,49.0,14.0,26.0,30.0,32.0,41.0"))).isEqualTo("vYHZ2fH45tMv");
        assertThat(codec.encode(tactic("f4213", EaBuildUp.SHORT_PASSING, 60, "2.0,3.1,11.0,11.0,7.1,49.0,16.0,31.1,36.0,41.0,39.0"))).isEqualTo("RZtRy3rYbN?y");
    }

    @Test
    void matchesRandomTacticsProducedByTheReferenceEncoder() {
        record Case(String formation, EaBuildUp buildUp, int lineHeight, String roles, String digits) {
        }
        List<Case> cases = List.of(
                new Case("f4222", EaBuildUp.BALANCED, 11, "45.0,3.2,48.0,48.1,10.0,16.2,14.1,34.1,34.2,42.2,41.1", "54,32,15,2,25,9,39,8,60,2,28,49"),
                new Case("f352", EaBuildUp.COUNTER, 40, "1.0,13.2,13.2,11.0,16.0,17.0,26.1,30.0,31.0,41.0,43.1", "17,17,44,33,58,1,28,33,11,57,29,32"),
                new Case("f4321", EaBuildUp.COUNTER, 38, "2.1,46.0,12.1,12.1,9.1,20.0,18.1,18.0,31.2,34.0,41.2", "52,48,12,2,13,24,40,32,23,34,49,2"),
                new Case("f433c", EaBuildUp.COUNTER, 8, "1.1,4.1,11.1,12.1,47.0,19.0,22.1,31.1,37.1,44.2,40.0", "17,16,42,27,16,55,44,9,36,21,29,28"),
                new Case("f5212", EaBuildUp.SHORT_PASSING, 14, "1.0,46.1,11.0,13.2,13.1,10.1,21.0,19.1,33.2,44.0,44.1", "49,32,12,49,7,58,60,1,62,33,45,12"),
                new Case("f4132", EaBuildUp.SHORT_PASSING, 38, "45.0,46.0,11.0,48.1,9.1,15.0,23.1,20.1,28.0,43.1,44.0", "40,0,0,6,34,18,56,52,54,54,38,6"),
                new Case("f451a", EaBuildUp.COUNTER, 40, "45.0,6.1,11.1,48.1,9.0,23.0,20.0,20.0,20.1,27.0,42.0", "56,24,18,62,38,4,10,2,14,2,6,7"),
                new Case("f3142", EaBuildUp.BALANCED, 10, "45.0,48.1,13.0,11.0,15.0,24.0,20.0,18.1,27.0,42.2,44.1", "34,40,60,34,61,56,20,40,12,46,0,29"),
                new Case("f5212", EaBuildUp.COUNTER, 92, "1.1,6.1,11.1,48.1,11.0,10.1,20.1,21.1,33.2,42.2,41.2", "9,34,36,23,36,56,18,15,31,19,53,30"),
                new Case("f442", EaBuildUp.COUNTER, 51, "2.1,46.1,11.0,12.0,47.1,25.0,20.0,20.1,30.1,42.0,42.1", "13,57,39,21,29,23,55,39,24,61,50,29"));
        for (Case c : cases) {
            assertThat(codec.digits(tactic(c.formation(), c.buildUp(), c.lineHeight(), c.roles())))
                    .as(c.formation() + " " + c.buildUp() + " " + c.lineHeight())
                    .isEqualTo(digits(c.digits()));
        }
    }

    @Test
    void decodesWhatItEncodes() {
        List<EaTactic> tactics = List.of(
                tactic("f442", EaBuildUp.COUNTER, 87, F442_DEFAULT),
                tactic("f4231a", EaBuildUp.SHORT_PASSING, 1, "2.1,46.0,11.0,12.0,47.1,49.0,14.2,26.1,30.1,32.0,41.2"),
                tactic("f523", EaBuildUp.BALANCED, 100, "1.1,6.0,11.1,13.2,48.0,10.1,18.1,22.2,35.1,44.2,39.2"));
        for (EaTactic expected : tactics) {
            Optional<EaTactic> decoded = codec.decode(codec.encode(expected));
            assertThat(decoded).as(expected.formationId()).isPresent();
            assertThat(decoded.get()).isEqualTo(expected);
        }
    }

    @Test
    void decodesTheCodesThatCameFromTheGame() {
        EaTactic result = codec.decode("RZtRy3rYbN?y").orElseThrow();
        assertThat(result.formationId()).isEqualTo("f4213");
        assertThat(result.buildUp()).isEqualTo(EaBuildUp.SHORT_PASSING);
        assertThat(result.lineHeight()).isEqualTo(60);
        assertThat(result.slots().get(1)).isEqualTo(new EaSlot(3, 1));
        assertThat(codec.decode("AJawHEA#K?tB").orElseThrow().formationId()).isEqualTo("f4411a");
        assertThat(codec.decode("vYHZ2fH45tMv").orElseThrow().formationId()).isEqualTo("f4231a");
    }

    @Test
    void rejectsGarbageInsteadOfGuessing() {
        assertThat(codec.decode("AAAAAAAAAAAA")).isEmpty();
        assertThatThrownBy(() -> codec.decode("short")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.decode("n4BxAr3vxFD!")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> codec.encode(tactic("f442", EaBuildUp.BALANCED, 50, "41.0,3.0,11.0,11.0,7.0,23.0,18.0,18.0,27.0,41.0,41.0")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
