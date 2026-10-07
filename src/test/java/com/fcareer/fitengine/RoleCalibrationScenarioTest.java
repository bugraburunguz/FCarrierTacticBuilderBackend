package com.fcareer.fitengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.model.WeaponState;
import com.fcareer.fitengine.model.response.RoleFitResponse;
import com.fcareer.roles.mapper.RoleMapper;
import com.fcareer.roles.seed.ReferenceSeedBundle;
import com.fcareer.roles.seed.RoleLibraryGenerator;
import com.fcareer.roles.seed.SeedReader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RoleCalibrationScenarioTest {

    private static Map<String, FitRole> v1;
    private static Map<String, FitRole> v2;

    @BeforeAll
    static void load() {
        SeedReader reader = new SeedReader(new ObjectMapper());
        ReferenceSeedBundle bundle = reader.read();
        RoleLibraryGenerator generator = new RoleLibraryGenerator();
        RoleMapper mapper = new RoleMapper();
        v1 = generator.generate(bundle.roles(), bundle.weighting()).stream().map(mapper::toFitRole)
                .collect(Collectors.toMap(FitRole::getId, r -> r));
        v2 = generator.generate(bundle.roles(), bundle.weighting(), reader.readCalibration()).stream().map(mapper::toFitRole)
                .collect(Collectors.toMap(FitRole::getId, r -> r));
    }

    private static FitRole role(Map<String, FitRole> library, String baseRole, String position) {
        return library.values().stream()
                .filter(r -> r.getBaseRole().equals(baseRole) && r.getPositions().contains(position))
                .min(Comparator.comparing(FitRole::getId)).orElseThrow();
    }

    private static RoleFitResponse fit(Map<String, FitRole> library, FitPlayer player, String baseRole, String position) {
        return FitFixtures.ROLE_FIT.calculate(player, role(library, baseRole, position), null, position);
    }

    private static FitFixtures.PlayerBuilder base(String name, String... positions) {
        return FitFixtures.player(1, name, positions).all(66);
    }

    @Test
    void v1MidfielderIsNotAGreenBallPlayingDefender() {
        FitPlayer midfielder = base("CM profili", "CM").attr(Attr.DEF_AWARENESS, 50).attr(Attr.SHORT_PASSING, 85)
                .attr(Attr.LONG_PASSING, 82).attr(Attr.VISION, 82).attr(Attr.STANDING_TACKLE, 52).attr(Attr.INTERCEPTIONS, 55).build();
        for (Map<String, FitRole> library : List.of(v1, v2)) {
            RoleFitResponse result = fit(library, midfielder, "ball_playing_defender", "CB");
            assertThat(result.getBadge()).isEqualTo(WeaponState.RED);
            assertThat(result.getProjectedRating()).isLessThanOrEqualTo(60);
        }
    }

    @Test
    void v2RealCentreBackIsAGoodBallPlayingDefender() {
        FitPlayer centreBack = base("Pasli stoper", "CB").attr(Attr.DEF_AWARENESS, 82).attr(Attr.STANDING_TACKLE, 80)
                .attr(Attr.INTERCEPTIONS, 78).attr(Attr.STRENGTH, 78).attr(Attr.HEADING, 76).attr(Attr.SHORT_PASSING, 76)
                .attr(Attr.LONG_PASSING, 74).attr(Attr.VISION, 68).attr(Attr.COMPOSURE, 72).attr(Attr.REACTIONS, 74).attr(Attr.ACCELERATION, 70).attr(Attr.SPRINT_SPEED, 72).build();
        RoleFitResponse result = fit(v2, centreBack, "ball_playing_defender", "CB");
        assertThat(result.getBadge()).as(result.getReasons().toString()).isNotEqualTo(WeaponState.RED);
        assertThat(result.getScore()).isGreaterThan(60);
    }

    @Test
    void v2BoxCrasherRewardsLateRunnersOverPureDestroyers() {
        FitPlayer runner = base("Dalan 8", "CM", "CDM").attr(Attr.LONG_SHOTS, 80).attr(Attr.POSITIONING, 78).attr(Attr.STAMINA, 80)
                .attr(Attr.SHOT_POWER, 78).attr(Attr.FINISHING, 68).attr(Attr.DEF_AWARENESS, 65).attr(Attr.STANDING_TACKLE, 64)
                .attr(Attr.INTERCEPTIONS, 64).build();
        FitPlayer destroyer = base("Saf yikici", "CDM").attr(Attr.LONG_SHOTS, 40).attr(Attr.POSITIONING, 42).attr(Attr.SHOT_POWER, 60)
                .attr(Attr.DEF_AWARENESS, 82).attr(Attr.STANDING_TACKLE, 82).attr(Attr.INTERCEPTIONS, 82).attr(Attr.STRENGTH, 80).build();
        assertThat(fit(v2, runner, "box_crasher", "CDM").getScore()).isGreaterThan(fit(v2, destroyer, "box_crasher", "CDM").getScore());
    }

    @Test
    void v2TargetAndPoacherSeparateAerialFromFinishing() {
        FitPlayer header = base("Hava", "ST").attr(Attr.HEADING, 85).attr(Attr.STRENGTH, 82).attr(Attr.JUMPING, 80).attr(Attr.FINISHING, 66).build();
        FitPlayer finisher = base("Bitirici", "ST").attr(Attr.FINISHING, 88).attr(Attr.POSITIONING, 86).attr(Attr.COMPOSURE, 80).attr(Attr.HEADING, 60).build();
        assertThat(fit(v2, header, "target_forward", "ST").getScore()).isGreaterThan(fit(v2, finisher, "target_forward", "ST").getScore());
        assertThat(fit(v2, finisher, "poacher", "ST").getScore()).isGreaterThan(fit(v2, header, "poacher", "ST").getScore());
    }

    @Test
    void v2SamePlayerIsRatedDifferentlyAsCentreBackAndPlaymaker() {
        FitPlayer defensive = base("Savunmaci", "CB", "CAM").attr(Attr.DEF_AWARENESS, 85).attr(Attr.STANDING_TACKLE, 84)
                .attr(Attr.STRENGTH, 82).attr(Attr.VISION, 50).attr(Attr.DRIBBLING, 50).build();
        assertThat(fit(v2, defensive, "defender", "CB").getScore()).isGreaterThan(fit(v2, defensive, "classic_10", "CAM").getScore());
    }

    @Test
    void v2PaceDoesNotInflateALowFinishingInsideForward() {
        FitPlayer fast = base("Hizli kanat", "RW").attr(Attr.ACCELERATION, 90).attr(Attr.SPRINT_SPEED, 90).attr(Attr.FINISHING, 55)
                .attr(Attr.DRIBBLING, 74).attr(Attr.BALL_CONTROL, 72).build();
        assertThat(fit(v2, fast, "inside_forward", "RW").getBadge()).isNotEqualTo(WeaponState.GREEN);
    }

    @Test
    void writesBeforeAfterReport() throws IOException {
        List<FitPlayer> players = List.of(
                base("Saf stoper", "CB").attr(Attr.DEF_AWARENESS, 84).attr(Attr.STANDING_TACKLE, 83).attr(Attr.STRENGTH, 82).attr(Attr.HEADING, 80).build(),
                base("Pasli stoper", "CB").attr(Attr.DEF_AWARENESS, 80).attr(Attr.STANDING_TACKLE, 76).attr(Attr.SHORT_PASSING, 78).attr(Attr.LONG_PASSING, 78).attr(Attr.VISION, 72).build(),
                base("Orta saha CB'de", "CB").attr(Attr.DEF_AWARENESS, 50).attr(Attr.SHORT_PASSING, 85).attr(Attr.VISION, 82).build(),
                base("Yikici 6", "CDM").attr(Attr.DEF_AWARENESS, 82).attr(Attr.INTERCEPTIONS, 84).attr(Attr.STANDING_TACKLE, 82).attr(Attr.STRENGTH, 80).build(),
                base("Dalan 8", "CM", "CDM").attr(Attr.LONG_SHOTS, 80).attr(Attr.POSITIONING, 78).attr(Attr.STAMINA, 82).attr(Attr.DEF_AWARENESS, 66).attr(Attr.STANDING_TACKLE, 64).attr(Attr.INTERCEPTIONS, 66).build(),
                base("Oyun kurucu CM", "CM").attr(Attr.VISION, 84).attr(Attr.SHORT_PASSING, 86).attr(Attr.LONG_PASSING, 84).attr(Attr.BALL_CONTROL, 82).build(),
                base("Yaratici 10", "CAM").attr(Attr.VISION, 86).attr(Attr.BALL_CONTROL, 85).attr(Attr.DRIBBLING, 82).attr(Attr.SHORT_PASSING, 84).attr(Attr.COMPOSURE, 80).build(),
                base("Golcu 10", "CAM").attr(Attr.FINISHING, 82).attr(Attr.POSITIONING, 84).attr(Attr.ACCELERATION, 80).attr(Attr.BALL_CONTROL, 78).build(),
                base("Hizli kanat", "RW").attr(Attr.ACCELERATION, 92).attr(Attr.SPRINT_SPEED, 92).attr(Attr.DRIBBLING, 82).attr(Attr.CROSSING, 70).attr(Attr.FINISHING, 62).build(),
                base("Bitirici kanat", "RW").attr(Attr.FINISHING, 84).attr(Attr.DRIBBLING, 84).attr(Attr.AGILITY, 82).attr(Attr.CURVE, 80).attr(Attr.ACCELERATION, 82).build(),
                base("Hucum beki", "RB").attr(Attr.CROSSING, 82).attr(Attr.STAMINA, 86).attr(Attr.ACCELERATION, 82).attr(Attr.SPRINT_SPEED, 82).attr(Attr.DEF_AWARENESS, 70).attr(Attr.STANDING_TACKLE, 68).build(),
                base("Hava forvet", "ST").attr(Attr.HEADING, 86).attr(Attr.STRENGTH, 84).attr(Attr.JUMPING, 82).attr(Attr.FINISHING, 72).build(),
                base("Bitirici forvet", "ST").attr(Attr.FINISHING, 88).attr(Attr.POSITIONING, 86).attr(Attr.COMPOSURE, 82).attr(Attr.ACCELERATION, 78).build(),
                base("Dusen forvet", "ST").attr(Attr.VISION, 80).attr(Attr.SHORT_PASSING, 80).attr(Attr.BALL_CONTROL, 84).attr(Attr.DRIBBLING, 82).attr(Attr.FINISHING, 76).build());
        StringBuilder report = new StringBuilder("# Role calibration v2 - once/sonra (ilk 3 rol, skor)\n\n| Oyuncu | Mevki | v1 | v2 |\n|---|---|---|---|\n");
        for (FitPlayer player : players) {
            String position = player.getPositions().get(0);
            report.append("| ").append(player.getName()).append(" | ").append(position).append(" | ")
                    .append(top(v1, player, position)).append(" | ").append(top(v2, player, position)).append(" |\n");
        }
        Path out = Path.of("target", "role-calibration-report.md");
        Files.writeString(out, report.toString());
        assertThat(Files.exists(out)).isTrue();
    }

    private static String top(Map<String, FitRole> library, FitPlayer player, String position) {
        List<String> rows = new ArrayList<>();
        library.values().stream().filter(r -> r.getPositions().contains(position))
                .map(r -> Map.entry(r, FitFixtures.ROLE_FIT.calculate(player, r, null, position)))
                .sorted((a, b) -> Double.compare(b.getValue().getScore(), a.getValue().getScore()))
                .limit(3)
                .forEach(e -> rows.add(String.format(Locale.ROOT, "%s %.0f %s", e.getKey().getName(), e.getValue().getScore(), e.getValue().getBadge().name().charAt(0))));
        return String.join("; ", rows);
    }
}
