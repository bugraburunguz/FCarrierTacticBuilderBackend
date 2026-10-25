package com.fcareer.career;

import com.fcareer.career.academy.AcademyAdvisor;
import com.fcareer.career.academy.AcademyAdvisor.Backup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ACADEMY-b madde 4: "A takıma al" önerisi. Slotun yedeği, o mevkide oynayabilen kadro oyuncularının Güç sırasında taktikteki slot sayısından sonraki
 * oyuncudur (tek CB slotunda 2., iki CB slotunda 3.). Gencin 1 sezon sonraki tahmini Güç'ü yedeği geçiyorsa ya da yedek yoksa öneri çıkar.
 */
class AcademyAdvisorTest {

    private static Backup b(String name, double strength) {
        return new Backup(name, strength);
    }

    @Test
    void backupIsThePlayerAfterTheTacticSlots() {
        List<Backup> squad = List.of(b("A", 60), b("B", 72), b("C", 65));
        assertThat(AcademyAdvisor.backup(squad, 1)).isEqualTo(b("C", 65));
        assertThat(AcademyAdvisor.backup(squad, 2)).isEqualTo(b("A", 60));
        assertThat(AcademyAdvisor.backup(squad, 3)).isNull();
    }

    @Test
    void promoteWhenProjectionBeatsBackupOrThereIsNoBackup() {
        assertThat(AcademyAdvisor.promotes(66.0, b("C", 65))).isTrue();
        assertThat(AcademyAdvisor.promotes(65.0, b("C", 65))).as("eşitlik yetmez").isFalse();
        assertThat(AcademyAdvisor.promotes(50.0, null)).as("yedek yok").isTrue();
    }

    /** ACADEMY-b: Hazır = "A takıma al" önerisi var; Bekliyor = altyapıda en az eşik kadar ay ve son iki içe aktarmada OVR artışı yok; diğerleri Gelişiyor. */
    @Test
    void statusRule() {
        assertThat(AcademyAdvisor.status(true, 2, List.of(55, 54), 12)).isEqualTo("READY");
        assertThat(AcademyAdvisor.status(false, 13, List.of(55, 55), 12)).isEqualTo("WAITING");
        assertThat(AcademyAdvisor.status(false, 13, List.of(54, 55), 12)).as("düşüş de artış değil").isEqualTo("WAITING");
        assertThat(AcademyAdvisor.status(false, 13, List.of(56, 55), 12)).as("arttı").isEqualTo("GROWING");
        assertThat(AcademyAdvisor.status(false, 11, List.of(55, 55), 12)).as("eşik altı").isEqualTo("GROWING");
        assertThat(AcademyAdvisor.status(false, 20, List.of(55), 12)).as("tek içe aktarma: bilinmiyor").isEqualTo("GROWING");
        assertThat(AcademyAdvisor.status(false, null, List.of(55, 55), 12)).isEqualTo("GROWING");
    }

    /** "Takımdaki yeri": kaçıncı sezonda (1..3) eşiği geçer; geçmezse null, eşik yoksa 1 (boş yer). */
    @Test
    void seasonToPass() {
        assertThat(AcademyAdvisor.seasonToPass(List.of(60.0, 64.0, 69.0), 63.0)).isEqualTo(2);
        assertThat(AcademyAdvisor.seasonToPass(List.of(60.0, 61.0, 62.0), 63.0)).isNull();
        assertThat(AcademyAdvisor.seasonToPass(List.of(60.0, 61.0, 62.0), null)).isEqualTo(1);
    }

    @Test
    void textSaysItIsAnEstimateAndAppliedInTheGame() {
        assertThat(AcademyAdvisor.promoteText("Kyle Preston", "CM", 66.4, b("Smith", 65.2)))
                .isEqualTo("Kyle Preston · 1 sezon sonra tahmini CM gücü 66, yedek Smith 65. Bu bir tahmin; A takıma alma oyunda yapılır.");
        assertThat(AcademyAdvisor.promoteText("Kyle Preston", "CM", 50, null))
                .isEqualTo("Kyle Preston · CM için yedek yok; 1 sezon sonra tahmini gücü 50. Bu bir tahmin; A takıma alma oyunda yapılır.");
    }
}
