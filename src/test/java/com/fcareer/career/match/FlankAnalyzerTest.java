package com.fcareer.career.match;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.career.match.FlankAnalyzer.FlankSlot;
import com.fcareer.career.match.MatchCenterModels.FlankMatchup;
import com.fcareer.career.match.MatchCenterModels.FlankSide;
import com.fcareer.career.match.MatchCenterModels.FlankVerdict;
import com.fcareer.career.match.MatchCenterModels.Flanks;
import com.fcareer.career.match.MatchCenterModels.TacticalNote;
import com.fcareer.common.service.MessageService;
import org.springframework.context.support.ResourceBundleMessageSource;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.tacticanalysis.RoleMovements;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FlankAnalyzerTest {

    private static final RoleMovements MOVEMENTS = new RoleMovements(new ObjectMapper());

    private final MatchProperties properties = new MatchProperties();
    private final FlankAnalyzer analyzer = new FlankAnalyzer(properties, MOVEMENTS, new MessageService(messageSource()));

    private static ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    @Test
    void anExposedFlankWritesATurkishNoteNamingSidesPlayersAndNumbers() {
        Flanks flanks = analyzer.analyze(ours("winger_attack", "fullback_balanced", 70), theirs(strongRight())).orElseThrow();

        List<TacticalNote> notes = analyzer.buildNotes(flanks);

        assertThat(notes).extracting(TacticalNote::code).containsExactly("FLANK_EXPOSED");
        String text = notes.get(0).text();
        assertThat(text).startsWith("Sol kanadımız açık").contains("sağ kanadında", "Sağ Kanat", "9 gol+asist", "Bizim Sol Kanat", "Winger", "Attack",
                "Bizim Sol Bek", String.valueOf(flanks.left().theirs().threat()), String.valueOf(flanks.left().ours().defensiveCover()));
        assertThat(text).doesNotContain("{");
    }

    @Test
    void anUnknownGoalAssistCountIsLeftOutOfTheNote() {
        List<FlankSlot> theirs = theirs(List.of(
                slot("RB", "Sağ Bek", "fullback_balanced", attrs(84, 85, 78, 80, 78, 70, 72, 70), 0),
                slot("RM", "Sağ Kanat", "winger_attack", attrs(94, 93, 90, 84, 90, 35, 30, 35), 0)));

        List<TacticalNote> notes = analyzer.buildNotes(analyzer.analyze(ours("winger_attack", "fullback_balanced", 70), theirs).orElseThrow());

        assertThat(notes).extracting(TacticalNote::code).containsExactly("FLANK_EXPOSED");
        assertThat(notes.get(0).text()).doesNotContain("gol+asist").contains("Winger (Attack) rolüyle").doesNotContain("odağıyla");
    }

    @Test
    void anExposedFlankWithACounterChanceMentionsTheSpaceBehindTheFullback() {
        List<FlankSlot> theirs = theirs(List.of(
                slot("RB", "Bindiren Bek", "fullback_balanced", attrs(88, 88, 75, 84, 78, 60, 58, 60), 0),
                slot("RM", "Sağ Kanat", "winger_attack", attrs(92, 90, 86, 80, 86, 35, 30, 35), 9)));

        List<TacticalNote> notes = analyzer.buildNotes(analyzer.analyze(ours("winger_attack", "fullback_balanced", 70), theirs).orElseThrow());

        assertThat(notes).extracting(TacticalNote::code).containsExactly("FLANK_EXPOSED_COUNTER");
        assertThat(notes.get(0).text()).contains("Bindiren Bek", "boşluğa koş").doesNotContain("{");
    }

    @Test
    void aCounterChanceWritesACounterNote() {
        List<FlankSlot> theirs = theirs(List.of(
                slot("RB", "Bindiren Bek", "fullback_balanced", attrs(88, 88, 75, 84, 78, 60, 58, 60), 0),
                slot("RM", "Zayıf Kanat", "winger_balanced", attrs(62, 60, 58, 60, 60, 40, 40, 40), 0)));

        List<TacticalNote> notes = analyzer.buildNotes(analyzer.analyze(ours("winger_attack", "fullback_defend", 82), theirs).orElseThrow());

        assertThat(notes).extracting(TacticalNote::code).containsExactly("FLANK_COUNTER");
        assertThat(notes.get(0).text()).startsWith("Sol kanatta fırsat").contains("Bindiren Bek", "Bizim Sol Kanat").doesNotContain("{");
    }

    @Test
    void balancedFlanksWriteNoNotes() {
        Flanks flanks = analyzer.analyze(ours("winger_balanced", "fullback_defend", 82), List.of(
                slot("RB", "Sağ Bek", "fullback_defend", attrs(60, 60, 55, 55, 58, 75, 75, 75), 0),
                slot("RM", "Sağ Kanat", "wide_midfielder_defend", attrs(62, 62, 60, 60, 60, 60, 60, 60), 0))).orElseThrow();

        assertThat(analyzer.buildNotes(flanks)).isEmpty();
    }

    @Test
    void anAttackingLeftWingAgainstAStrongAttackingRightSideIsExposed() {
        Flanks flanks = analyzer.analyze(ours("winger_attack", "fullback_balanced", 70), theirs(strongRight())).orElseThrow();

        FlankMatchup left = flanks.left();
        assertThat(left.side()).isEqualTo(FlankSide.LEFT);
        assertThat(left.verdict()).isEqualTo(FlankVerdict.EXPOSED);
        assertThat(left.theirs().players()).extracting("position").containsExactlyInAnyOrder("RB", "RM");
        assertThat(left.ours().players()).extracting("position").containsExactlyInAnyOrder("LB", "LW");
        assertThat(left.theirs().keyThreat().name()).isEqualTo("Sağ Kanat");
        assertThat(left.theirs().keyThreat().goalsAssists()).isEqualTo(9);
        assertThat(left.theirs().threat()).isGreaterThanOrEqualTo(properties.getPlan().getFlank().getExposedThreatMin());
        assertThat(left.ours().players()).filteredOn((p) -> p.position().equals("LW")).singleElement()
                .satisfies((p) -> {
                    assertThat(p.role()).isEqualTo("Winger (Attack)");
                    assertThat(p.focus()).isEqualTo("Attack");
                });
    }

    @Test
    void aDefensiveLeftSideAgainstTheSameThreatIsNotExposed() {
        Flanks flanks = analyzer.analyze(ours("winger_balanced", "fullback_defend", 82), theirs(strongRight())).orElseThrow();

        assertThat(flanks.left().verdict()).isNotEqualTo(FlankVerdict.EXPOSED);
        assertThat(flanks.left().ours().defensiveCover()).isGreaterThan(analyzer.analyze(ours("winger_attack", "fullback_balanced", 70), theirs(strongRight()))
                .orElseThrow().left().ours().defensiveCover());
        assertThat(flanks.left().ours().attackLean()).isLessThan(properties.getPlan().getFlank().getExposedOurLeanMin());
    }

    @Test
    void anOverlappingFullbackWithAWeakWingerInFrontGivesOurFastWingerACounterChance() {
        List<FlankSlot> theirs = theirs(List.of(
                slot("RB", "Bindiren Bek", "fullback_balanced", attrs(88, 88, 75, 84, 78, 60, 58, 60), 0),
                slot("RM", "Zayıf Kanat", "winger_balanced", attrs(62, 60, 58, 60, 60, 40, 40, 40), 0)));

        FlankMatchup left = analyzer.analyze(ours("winger_attack", "fullback_defend", 82), theirs).orElseThrow().left();

        assertThat(left.verdict()).isEqualTo(FlankVerdict.OPPORTUNITY);
        assertThat(left.counter()).isTrue();
    }

    @Test
    void exposedWinsOverACounterChanceButKeepsTheCounterFlag() {
        List<FlankSlot> theirs = theirs(List.of(
                slot("RB", "Bindiren Bek", "fullback_balanced", attrs(88, 88, 75, 84, 78, 60, 58, 60), 0),
                slot("RM", "Sağ Kanat", "winger_attack", attrs(92, 90, 86, 80, 86, 35, 30, 35), 9)));

        FlankMatchup left = analyzer.analyze(ours("winger_attack", "fullback_balanced", 70), theirs).orElseThrow().left();

        assertThat(left.verdict()).isEqualTo(FlankVerdict.EXPOSED);
        assertThat(left.counter()).isTrue();
    }

    @Test
    void sameInputGivesTheSameNumbers() {
        Flanks first = analyzer.analyze(ours("winger_attack", "fullback_balanced", 70), theirs(strongRight())).orElseThrow();
        Flanks second = analyzer.analyze(ours("winger_attack", "fullback_balanced", 70), theirs(strongRight())).orElseThrow();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void thresholdsComeFromConfig() {
        properties.getPlan().getFlank().setExposedThreatMin(101);

        FlankMatchup left = analyzer.analyze(ours("winger_attack", "fullback_balanced", 70), theirs(strongRight())).orElseThrow().left();

        assertThat(left.verdict()).isNotEqualTo(FlankVerdict.EXPOSED);
    }

    @Test
    void aSideWithoutWidePlayersHasNoMatchupAndNoPlayersMeansNoFlanks() {
        Flanks flanks = analyzer.analyze(ours("winger_attack", "fullback_balanced", 70), List.of(
                slot("RB", "Sağ Bek", "fullback_balanced", attrs(80, 80, 70, 72, 70, 70, 70, 70), 0),
                slot("RM", "Sağ Kanat", "winger_balanced", attrs(80, 80, 70, 72, 70, 40, 40, 40), 0))).orElseThrow();

        assertThat(flanks.left()).isNotNull();
        assertThat(flanks.right()).as("rakipte sol kanat oyuncusu yok").isNull();
        assertThat(analyzer.analyze(List.of(), theirs(strongRight()))).isEqualTo(Optional.empty());
        assertThat(analyzer.analyze(ours("winger_attack", "fullback_balanced", 70), List.of())).isEqualTo(Optional.empty());
    }

    private static List<FlankSlot> strongRight() {
        return List.of(
                slot("RB", "Sağ Bek", "fullback_balanced", attrs(84, 85, 78, 80, 78, 70, 72, 70), 2),
                slot("RM", "Sağ Kanat", "winger_attack", attrs(92, 90, 86, 80, 86, 35, 30, 35), 9));
    }

    private static List<FlankSlot> theirs(List<FlankSlot> right) {
        List<FlankSlot> all = new java.util.ArrayList<>(right);
        all.add(slot("LB", "Sol Bek", "fullback_balanced", attrs(70, 70, 60, 62, 64, 72, 74, 70), 0));
        all.add(slot("LM", "Sol Kanat", "winger_balanced", attrs(75, 74, 70, 70, 72, 40, 40, 40), 0));
        all.add(slot("CB", "Stoper", "defender_defend", attrs(60, 60, 40, 40, 50, 82, 84, 80), 0));
        return all;
    }

    private static List<FlankSlot> ours(String wingerRole, String fullbackRole, int fullbackDefence) {
        return List.of(
                slot("LW", "Bizim Sol Kanat", wingerRole, attrs(88, 89, 84, 76, 84, 35, 32, 34), 0),
                slot("LB", "Bizim Sol Bek", fullbackRole, attrs(76, 76, 66, 70, 70, fullbackDefence, fullbackDefence, fullbackDefence), 0),
                slot("RB", "Bizim Sağ Bek", "fullback_balanced", attrs(74, 74, 64, 68, 68, 74, 74, 74), 0),
                slot("RW", "Bizim Sağ Kanat", "winger_balanced", attrs(80, 80, 76, 72, 78, 40, 40, 40), 0),
                slot("ST", "Forvet", "advanced_forward_attack", attrs(80, 80, 78, 60, 80, 30, 30, 30), 0));
    }

    private static Map<Attr, Integer> attrs(int acceleration, int sprint, int dribbling, int crossing, int ballControl, int awareness, int tackle, int interceptions) {
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        attrs.put(Attr.ACCELERATION, acceleration);
        attrs.put(Attr.SPRINT_SPEED, sprint);
        attrs.put(Attr.DRIBBLING, dribbling);
        attrs.put(Attr.CROSSING, crossing);
        attrs.put(Attr.BALL_CONTROL, ballControl);
        attrs.put(Attr.DEF_AWARENESS, awareness);
        attrs.put(Attr.STANDING_TACKLE, tackle);
        attrs.put(Attr.INTERCEPTIONS, interceptions);
        return attrs;
    }

    private static FlankSlot slot(String position, String name, String roleId, Map<Attr, Integer> attrs, int goalsAssists) {
        FitPlayer player = FitPlayer.builder().id((long) Math.abs((name + position).hashCode())).name(name).overall(80).positions(List.of(position)).attrs(attrs)
                .playstyles(Map.of()).build();
        int cut = roleId.lastIndexOf('_');
        String base = roleId.substring(0, cut);
        String focus = switch (roleId.substring(cut + 1)) {
            case "attack" -> "Attack";
            case "defend" -> "Defend";
            default -> "Balanced";
        };
        String roleName = switch (base) {
            case "winger" -> "Winger";
            case "fullback" -> "Fullback";
            default -> base;
        } + " (" + focus + ")";
        FitRole role = FitRole.builder().id(roleId).baseRole(base).name(roleName).focus(focus).positions(List.of(position)).build();
        return new FlankSlot(position, player, role, goalsAssists);
    }
}
