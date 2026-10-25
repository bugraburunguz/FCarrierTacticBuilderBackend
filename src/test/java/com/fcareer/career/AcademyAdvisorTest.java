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

    @Test
    void textSaysItIsAnEstimateAndAppliedInTheGame() {
        assertThat(AcademyAdvisor.promoteText("Kyle Preston", "CM", 66.4, b("Smith", 65.2)))
                .isEqualTo("Kyle Preston · 1 sezon sonra tahmini CM gücü 66, yedek Smith 65. Bu bir tahmin; A takıma alma oyunda yapılır.");
        assertThat(AcademyAdvisor.promoteText("Kyle Preston", "CM", 50, null))
                .isEqualTo("Kyle Preston · CM için yedek yok; 1 sezon sonra tahmini gücü 50. Bu bir tahmin; A takıma alma oyunda yapılır.");
    }
}
