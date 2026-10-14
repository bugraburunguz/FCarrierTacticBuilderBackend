package com.fcareer.tacticcode.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.tacticcode.model.EaBuildUp;
import com.fcareer.tacticcode.model.EaCodecData;
import com.fcareer.tacticcode.model.EaTactic;
import com.fcareer.tacticcode.model.EaTactic.EaSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TAC-07: oyun içi doğrulanmış vektörler. Kaynak: Buğra, 2026-10-14. T01-T10 oyunda kuruldu ve paylaşım kodu codec'in beklediğiyle BİREBİR aynı çıktı (14 §5).
 * Bu 10 kod, önceden doğrulanmamış 54 (mevki, rol, focus) kombinasyonunu ve 7 formasyon tabanını kapsar: 113/113 kombinasyon ve 29/29 formasyon doğrulanmıştır.
 */
class EaTacticCodecInGameVectorsTest {

    /** slots: "POS|Rol|Focus" ;-ayrılmış, 11 adet (formasyonun slot sırası: aynı mevkiler sağdan sola). */
    private record Vector(String id, String formationLabel, EaBuildUp buildUp, int lineHeight, String code, String slots) {
    }

    private static final List<Vector> VECTORS = List.of(
            new Vector("T01", "3-4-1-2", EaBuildUp.COUNTER, 50, "HxhPhVrvr$jX", "GK|Goalkeeper|Balanced;CB|Defender|Balanced;CB|Stopper|Aggressive;CB|Ball-Playing Defender|Build-Up;RM|Winger|Attack;CM|Holding|Ball-Winning;CM|Half-Winger|Balanced;LM|Wide Midfielder|Defend;CAM|Playmaker|Build-Up;ST|Advanced Forward|Versatile;ST|Poacher|Support"),
            new Vector("T02", "4-1-3-2", EaBuildUp.SHORT_PASSING, 70, "SBqUoF8iY4%&", "GK|Goalkeeper|Balanced;RB|Falseback|Balanced;CB|Wide Back|Defend;CB|Wide Back|Aggressive;LB|Falseback|Balanced;CDM|Holding|Roaming;RM|Wide Midfielder|Defend;CM|Half-Winger|Attack;LM|Wide Midfielder|Build-Up;ST|Target Forward|Attack;ST|Target Forward|Wide"),
            new Vector("T03", "4-1-4-1", EaBuildUp.BALANCED, 95, "hJ5L451tzC$m", "GK|Goalkeeper|Balanced;RB|Wingback|Balanced;CB|Wide Back|Support;CB|Defender|Defend;LB|Wingback|Support;CDM|Centre-Half|Defend;RM|Wide Midfielder|Build-Up;CM|Half-Winger|Support;CM|Box-to-Box|Balanced;LM|Wide Playmaker|Attack;ST|Advanced Forward|Attack"),
            new Vector("T04", "4-2-4", EaBuildUp.COUNTER, 25, "TgrZcfj$d1ng", "GK|Goalkeeper|Balanced;RB|Wingback|Support;CB|Defender|Defend;CB|Defender|Defend;LB|Attacking Wingback|Support;CM|Box-to-Box|Balanced;CM|Box-to-Box|Balanced;RW|Winger|Attack;ST|Advanced Forward|Attack;ST|Advanced Forward|Attack;LW|Winger|Attack"),
            new Vector("T05", "4-5-1", EaBuildUp.SHORT_PASSING, 50, "BhbLBJpVLpdh", "GK|Goalkeeper|Balanced;RB|Attacking Wingback|Support;CB|Defender|Defend;CB|Defender|Defend;LB|Attacking Wingback|Attack;RM|Wide Playmaker|Attack;CM|Box-to-Box|Balanced;LM|Wide Playmaker|Build-Up;CAM|Half-Winger|Balanced;CAM|Half-Winger|Attack;ST|Advanced Forward|Attack"),
            new Vector("T06", "5-3-2", EaBuildUp.BALANCED, 70, "ex5o3i5$o#ag", "GK|Goalkeeper|Balanced;RB|Attacking Wingback|Attack;CB|Defender|Defend;CB|Defender|Defend;CB|Defender|Defend;LB|Inverted Wingback|Build-Up;CDM|Deep-Lying Playmaker|Roaming;CM|Box-to-Box|Balanced;CM|Box-to-Box|Balanced;ST|Advanced Forward|Attack;ST|Advanced Forward|Attack"),
            new Vector("T07", "5-4-1", EaBuildUp.COUNTER, 95, "xBN7H1R47f1G", "GK|Goalkeeper|Balanced;RB|Inverted Wingback|Build-Up;CB|Defender|Defend;CB|Defender|Defend;CB|Defender|Defend;LB|Inverted Wingback|Attack;RM|Winger|Balanced;CM|Box-to-Box|Balanced;CM|Box-to-Box|Balanced;LM|Winger|Balanced;ST|Advanced Forward|Attack"),
            new Vector("T08", "4-2-1-3", EaBuildUp.SHORT_PASSING, 25, "MZoYdgYUW8gp", "GK|Goalkeeper|Balanced;RB|Inverted Wingback|Attack;CB|Defender|Defend;CB|Defender|Defend;LB|Fullback|Balanced;CDM|Wide Half|Defend;CDM|Wide Half|Build-Up;CAM|Half-Winger|Roaming;RW|Winger|Versatile;ST|Advanced Forward|Attack;LW|Winger|Versatile"),
            new Vector("T09", "4-2-1-3", EaBuildUp.BALANCED, 50, "KZmUT8iSUomK", "GK|Goalkeeper|Balanced;RB|Fullback|Balanced;CB|Defender|Defend;CB|Defender|Defend;LB|Fullback|Balanced;CDM|Holding|Defend;CDM|Holding|Defend;CAM|Classic 10|Wide;RW|Wide Playmaker|Attack;ST|Advanced Forward|Attack;LW|Wide Playmaker|Attack"),
            new Vector("T10", "4-2-1-3", EaBuildUp.COUNTER, 70, "fZ7ngV3np9bx", "GK|Goalkeeper|Balanced;RB|Fullback|Balanced;CB|Defender|Defend;CB|Defender|Defend;LB|Fullback|Balanced;CDM|Holding|Defend;CDM|Holding|Defend;CAM|Classic 10|Versatile;RW|Wide Playmaker|Build-Up;ST|Advanced Forward|Attack;LW|Wide Playmaker|Build-Up"));

    private EaTacticCodec codec;

    @BeforeEach
    void setUp() {
        codec = new EaTacticCodec(new ObjectMapper());
        codec.load();
    }

    private EaTactic tactic(Vector v) {
        EaCodecData.Formation formation = codec.formations().stream().filter((f) -> f.label().equals(v.formationLabel())).findFirst().orElseThrow();
        List<EaSlot> slots = new ArrayList<>();
        String[] rows = v.slots().split(";");
        for (int i = 0; i < rows.length; i++) {
            String[] p = rows[i].split("[|]");
            assertThat(p[0]).as(v.id() + " slot " + i + " mevkisi formasyonla aynı").isEqualTo(formation.positions().get(i));
            EaCodecData.Role role = codec.roles().stream().filter((r) -> r.position().equals(p[0]) && norm(r.name()).equals(norm(p[1]))).findFirst()
                    .orElseThrow(() -> new AssertionError(v.id() + ": " + p[0] + " " + p[1]));
            int focus = role.focuses().stream().map(EaTacticCodecInGameVectorsTest::norm).toList().indexOf(norm(p[2]));
            assertThat(focus).as(v.id() + ": " + p[1] + " / " + p[2]).isNotNegative();
            slots.add(new EaSlot(role.id(), focus));
        }
        return new EaTactic(formation.id(), v.buildUp(), v.lineHeight(), slots);
    }

    private static String norm(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    @Test
    void allTenInGameCodesEncodeExactlyAsTheGameShowedThem() {
        assertThat(VECTORS).hasSize(10);
        for (Vector v : VECTORS) {
            assertThat(codec.encode(tactic(v))).as(v.id()).isEqualTo(v.code());
        }
    }

    @Test
    void allTenInGameCodesDecodeBackToTheSameTactic() {
        for (Vector v : VECTORS) {
            Optional<EaTactic> decoded = codec.decode(v.code());
            assertThat(decoded).as(v.id()).isPresent();
            assertThat(decoded.get()).as(v.id()).isEqualTo(tactic(v));
        }
    }

    @Test
    void theTenCodesCoverEveryFormationBaseThatWasUnverified() {
        Set<String> unverified = Set.of("3-4-1-2", "4-1-3-2", "4-1-4-1", "4-2-4", "4-5-1", "5-3-2", "5-4-1");
        assertThat(VECTORS.stream().map(Vector::formationLabel).toList()).containsAll(unverified);
    }

    /** 64 EasySBC kodu + 10 oyun içi T kodu: decode edilen taktiklerde görülen (mevki, rol.focus) ve formasyonlar, codec tablosunun TAMAMINI kapsar (113/113 ve 29/29). */
    @Test
    void theSeventyFourVerifiedCodesTogetherCoverEveryCombinationAndEveryFormation() throws Exception {
        List<String> codes = new ArrayList<>();
        try (InputStream in = new ClassPathResource("tactic-code/easysbc-vectors.json").getInputStream()) {
            new ObjectMapper().readTree(in).path("vectors").forEach((v) -> codes.add(v.path("code").asText()));
        }
        VECTORS.forEach((v) -> codes.add(v.code()));
        assertThat(codes).hasSize(74);
        Set<String> seen = new HashSet<>();
        Set<String> formations = new HashSet<>();
        for (String code : codes) {
            EaTactic t = codec.decode(code).orElseThrow(() -> new AssertionError(code));
            EaCodecData.Formation f = codec.formations().stream().filter((x) -> x.id().equals(t.formationId())).findFirst().orElseThrow();
            formations.add(f.id());
            for (int i = 0; i < t.slots().size(); i++) {
                seen.add(f.positions().get(i) + ":" + t.slots().get(i).roleId() + "." + t.slots().get(i).focusIndex());
            }
        }
        Set<String> all = new TreeSet<>();
        for (EaCodecData.Role r : codec.roles()) {
            for (int fi = 0; fi < r.focuses().size(); fi++) {
                if (codec.isSupported(r.position(), r.id(), fi)) {
                    all.add(r.position() + ":" + r.id() + "." + fi);
                }
            }
        }
        Set<String> missing = new TreeSet<>(all);
        missing.removeAll(seen);
        assertThat(missing).as("doğrulanmamış kombinasyonlar").isEmpty();
        assertThat(all).hasSize(113);
        assertThat(formations).hasSize(codec.formations().size()).hasSize(29);
    }

    @Test
    void theSeedStatesItsInGameVerification() throws Exception {
        try (InputStream in = new ClassPathResource("seed/ea-tactic-codec.json").getInputStream()) {
            var v = new ObjectMapper().readTree(in).path("verification");
            assertThat(v.path("verified").asBoolean()).isTrue();
            assertThat(v.path("combinations").path("verified").asInt()).isEqualTo(113);
            assertThat(v.path("combinations").path("total").asInt()).isEqualTo(113);
            assertThat(v.path("formations").path("verified").asInt()).isEqualTo(29);
            assertThat(v.path("formations").path("total").asInt()).isEqualTo(29);
            assertThat(v.path("source").asText()).contains("Buğra").contains("2026-10-14");
        }
    }
}
