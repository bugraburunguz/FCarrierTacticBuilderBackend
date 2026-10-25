package com.fcareer.fitengine;

import com.fcareer.career.service.CareerService;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.WeaponState;
import com.fcareer.fitengine.model.request.SetupCheckRequest;
import com.fcareer.fitengine.model.response.SetupCheck;
import com.fcareer.fitengine.service.SetupCheckService;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FIT-2 madde 7: setup-check sabitleri seed'e taşınırken davranış değişmez. Bu test taşımadan önceki çıktıyı (başlık, durum, ayrıntı, öneri, aday sırası) sabitler.
 */
class SetupCheckCharacterizationTest {

    private static final long CAREER = 7L;

    private static FitPlayer player(long id, String position, Map<Attr, Integer> attrs) {
        Map<Attr, Integer> all = new EnumMap<>(Attr.class);
        all.putAll(attrs);
        return FitPlayer.builder().id(id).name("P" + id).overall(70).positions(List.of(position)).attrs(all).playstyles(Map.of()).build();
    }

    private static Map<Attr, Integer> pace(int v) {
        return Map.of(Attr.ACCELERATION, v, Attr.SPRINT_SPEED, v);
    }

    private static final List<FitPlayer> SQUAD = List.of(
            player(1, "CB", Map.of(Attr.ACCELERATION, 70, Attr.SPRINT_SPEED, 74, Attr.HEADING, 70, Attr.JUMPING, 70, Attr.STRENGTH, 70, Attr.DEF_AWARENESS, 70,
                    Attr.SHORT_PASSING, 70, Attr.BALL_CONTROL, 64)),
            player(2, "CB", Map.of(Attr.ACCELERATION, 60, Attr.SPRINT_SPEED, 62, Attr.HEADING, 60, Attr.JUMPING, 60, Attr.STRENGTH, 60, Attr.DEF_AWARENESS, 60,
                    Attr.SHORT_PASSING, 60, Attr.BALL_CONTROL, 60)),
            player(3, "CB", Map.of(Attr.ACCELERATION, 50, Attr.SPRINT_SPEED, 50, Attr.HEADING, 80, Attr.JUMPING, 80, Attr.STRENGTH, 80, Attr.DEF_AWARENESS, 66)),
            player(4, "CDM", Map.of(Attr.SHORT_PASSING, 72, Attr.BALL_CONTROL, 70)),
            player(5, "LB", Map.of(Attr.SHORT_PASSING, 66, Attr.BALL_CONTROL, 66)),
            player(6, "GK", Map.of(Attr.GK_KICKING, 64)),
            player(7, "RW", pace(80)),
            player(8, "ST", pace(77)),
            player(9, "LM", pace(75)));

    private static SetupCheckService service() {
        CareerService careers = mock(CareerService.class);
        when(careers.squadPlayerIds(any(), any())).thenReturn(new CareerService.CareerSquad(List.of(), (short) 27, 0L, (short) 0, 1, CAREER, List.of()));
        PlayerService players = mock(PlayerService.class);
        when(players.fitPlayers(anyList(), any(), any())).thenReturn(SQUAD);
        return SetupCheckFixture.service(careers, players);
    }

    private static List<SetupCheck> check(String buildUp, int depth) {
        SetupCheckRequest request = new SetupCheckRequest();
        request.setCareerId(CAREER);
        request.setBuildUp(buildUp);
        request.setDepth(depth);
        return service().check(1L, request);
    }

    private static String row(SetupCheck c) {
        return c.getTitle() + " | " + c.getStatus() + " | " + c.getDetail() + " | " + c.getAdvice() + " | "
                + c.getPlayers().stream().map(p -> p.getId() + ":" + p.getValue() + (p.isOk() ? "+" : "-")).toList();
    }

    @Test
    void aggressiveLineShortBuildUp() {
        assertThat(check("Short", 85)).extracting(SetupCheckCharacterizationTest::row).containsExactly(
                "Yüksek hat: stoperlerde hız | YELLOW | Yüksek/agresif hatta stoperler arkadaki boşluğu kapatabilmeli (ort. hızlanma+sprint ≥ 72). Uygun: 1 / gereken 2. "
                        + "| Savunma hattını biraz düşür ya da hızlı bir stoper ekle. | [1:72+, 2:61-, 3:50-]",
                "Kısa pasla çıkış: pas güvenliği | YELLOW | Defans ve pivotta topu kaybetmeden oynayabilen en az 4 oyuncu gerekir (ort. kısa pas+top kontrolü ≥ 66). Uygun: 3 / gereken 4. "
                        + "| Dengeli çıkışa geç ya da pası iyi bir defans/pivot oyuncusu ekle. | [4:71+, 1:67+, 5:66+, 2:60-, 3:0-]",
                "Kısa pasla çıkış: kaleci dağıtımı | YELLOW | Kaleci de kurulumda yer alır (kaleci vuruşu ≥ 65). Uygun: 0 / gereken 1. "
                        + "| Ayağı iyi bir kaleci dene ya da uzun topla çıkışı seç. | [6:64-]");
    }

    @Test
    void highLineUsesLowerPaceThreshold() {
        assertThat(check("Balanced", 65)).extracting(SetupCheckCharacterizationTest::row).containsExactly(
                "Yüksek hat: stoperlerde hız | YELLOW | Yüksek/agresif hatta stoperler arkadaki boşluğu kapatabilmeli (ort. hızlanma+sprint ≥ 66). Uygun: 1 / gereken 2. "
                        + "| Savunma hattını biraz düşür ya da hızlı bir stoper ekle. | [1:72+, 2:61-, 3:50-]");
    }

    @Test
    void lowBlockCounter() {
        assertThat(check("Counter", 35)).extracting(SetupCheckCharacterizationTest::row).containsExactly(
                "Alçak blok: hava hakimiyeti | GREEN | Geride beklerken stoperler ortaları kesmeli (ort. kafa, sıçrama, güç ≥ 68). Uygun: 2 / gereken 2. "
                        + "| Hava toplarında güçlü bir stoper ekle ya da hattı biraz yükselt. | [3:80+, 1:70+, 2:60-]",
                "Kontra: hızlı koşucular | YELLOW | Boşluğa koşacak en az 3 hızlı oyuncu gerekir (ort. hızlanma+sprint ≥ 76). Uygun: 2 / gereken 3. "
                        + "| Hızlı bir kanat/forvet ekle ya da dengeli çıkışa geç. | [7:80+, 8:77+, 9:75-]");
    }

    @Test
    void middleBlockBalanced() {
        assertThat(check("Balanced", 50)).extracting(SetupCheckCharacterizationTest::row).containsExactly(
                "Orta blok: savunma okuması | GREEN | Dengeli hatta stoperler pozisyon almayı bilmeli (savunma farkındalığı ≥ 65). Uygun: 2 / gereken 2. "
                        + "| Savunma farkındalığı yüksek bir stoper dene. | [1:70+, 3:66+, 2:60-]");
        assertThat(check("Balanced", 64)).hasSize(1).first().extracting(SetupCheck::getStatus).isEqualTo(WeaponState.GREEN);
        assertThat(check("Balanced", 36)).hasSize(1);
    }
}
