package com.fcareer.manager;

import com.fcareer.manager.ManagerModels.Background;
import com.fcareer.manager.ManagerModels.Category;
import com.fcareer.manager.ManagerModels.Difficulty;
import com.fcareer.manager.ManagerModels.Licence;
import com.fcareer.manager.ManagerModels.Priority;
import com.fcareer.manager.ManagerRules.Candidate;
import com.fcareer.manager.ManagerRules.Interest;
import com.fcareer.manager.ManagerRules.Selection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MGR-a madde 3–4 (verified:false, kaynak: "FM esinli, kalibre edilmedi"): menajer itibarı, kulübün itibar ihtiyacı, ilgi aralıkları, zorluk,
 * beklenti metni ve 6 kategorili seçim (her kategori bir kulüp, tekrar yok, mümkünse farklı lig; aday yoksa kart yok, nedeni yazar).
 */
class ManagerRulesTest {

    private final ManagerRules rules = new ManagerRules(new ManagerProperties());

    @Test
    void reputationIsBackgroundBasePlusLicenceCappedAtHundred() {
        assertThat(rules.reputation(Background.AMATEUR, Licence.NONE)).isEqualTo(5);
        assertThat(rules.reputation(Background.PRO_LOCAL, Licence.NATIONAL_B)).isEqualTo(34);
        assertThat(rules.reputation(Background.PRO_NATIONAL, Licence.INTERNATIONAL_A)).isEqualTo(67);
        assertThat(rules.reputation(Background.WORLD_CLASS, Licence.PRO)).isEqualTo(100);
        assertThat(rules.reputation(Background.SEMI_PRO, Licence.NATIONAL_C)).isEqualTo(17);
        assertThat(rules.band(5)).isEqualTo("Tanınmıyor");
        assertThat(rules.band(34)).isEqualTo("Yerel tanınırlık");
        assertThat(rules.band(67)).isEqualTo("Uluslararası tanınırlık");
        assertThat(rules.band(100)).isEqualTo("Dünya çapında");
    }

    @Test
    void interestRanges() {
        assertThat(rules.interest(40, 30)).as("ihtiyaç ≤ itibar + 10").isEqualTo(Interest.INTERESTED);
        assertThat(rules.interest(10, 30)).isEqualTo(Interest.INTERESTED);
        assertThat(rules.interest(41, 30)).as("(itibar + 10, itibar + 25]").isEqualTo(Interest.DREAM);
        assertThat(rules.interest(55, 30)).isEqualTo(Interest.DREAM);
        assertThat(rules.interest(56, 30)).isEqualTo(Interest.NONE);
    }

    @Test
    void needIsThePercentileOfWeightedPrestigeCorrectedByLeagueLevel() {
        Map<Long, Integer> need = rules.needs(List.of(
                new ManagerRules.Prestige(1, 10, 10, 1), new ManagerRules.Prestige(2, 5, 1, 1),
                new ManagerRules.Prestige(3, 6, 1, 3), new ManagerRules.Prestige(4, 1, 1, 4)));
        assertThat(need.get(1L)).isEqualTo(100);
        assertThat(need.get(4L)).isZero();
        assertThat(need.get(2L)).as("lig seviyesi düzeltmesi: 3. seviyenin yurt içi prestij 6'sı 1. seviyenin 5'ini geçmez").isGreaterThan(need.get(3L));
    }

    @Test
    void difficultyRule() {
        // Kadro ligin üst üçte birinde, beklenti makul, deneyimli menajer → Kolay.
        assertThat(rules.difficulty(1, 18, false, 50, 50)).isEqualTo(Difficulty.EASY);
        // Kadro ortada, prestijli kulüp (yüksek beklenti) → Orta.
        assertThat(rules.difficulty(9, 18, true, 50, 50)).isEqualTo(Difficulty.MEDIUM);
        // Kadro alt üçte birde, hayal kulübü, tanınmayan menajer → Zor.
        assertThat(rules.difficulty(17, 18, true, 45, 20)).isEqualTo(Difficulty.HARD);
    }

    /** Kaba sıra: ilgi aralığından ihtiyacı itibara en yakın poolSize kulüp + hayal aralığından ayrı kota (yakınlar havuzu doldurup hayali dışlamasın). */
    @Test
    void thePoolKeepsAQuotaForDreamClubs() {
        Map<Long, Integer> needs = new java.util.HashMap<>();
        for (long id = 1; id <= 60; id++) {
            needs.put(id, (int) (40 + id % 10));          // 40–49: itibar 46'ya yakın, ilgi aralığında
        }
        needs.put(100L, 65);                               // hayal (56, 71]
        needs.put(101L, 70);
        needs.put(102L, 90);                               // aralık dışı
        List<Long> pool = rules.pool(needs, 46, 40, 10);
        assertThat(pool).hasSize(42).contains(100L, 101L).doesNotContain(102L);
    }

    @Test
    void expectationText() {
        assertThat(rules.expectation(70, 40)).isEqualTo("Yönetim hızlı başarı bekler.");
        assertThat(rules.expectation(50, 55)).isEqualTo("Makul beklenti, zaman tanınır.");
        assertThat(rules.expectation(30, 52)).isEqualTo("Yönetim sabırlı ama taraftar şüpheli; ilk sezon kritik.");
    }

    private static Candidate c(int id, int league, int need, double fit) {
        return new Candidate(id, "Kulüp " + id, league, "Lig " + league, need, fit, 5, 0, 5, 20, 68.0, 1_000_000L, false, need, Difficulty.MEDIUM);
    }

    private static List<Candidate> pool() {
        List<Candidate> list = new ArrayList<>();
        list.add(c(1, 1, 45, 80));                                                                         // en uyumlu
        list.add(new Candidate(2, "Gençler", 2, "Lig 2", 40, 60, 9, 6, 8, 20, 64.0, 500_000L, false, 40, Difficulty.MEDIUM));   // gençlik
        list.add(new Candidate(3, "Batan", 3, "Lig 3", 48, 55, 4, 0, 18, 20, 61.0, 800_000L, false, 15, Difficulty.HARD));      // kurtarma
        list.add(new Candidate(4, "Zengin", 4, "Lig 4", 44, 58, 4, 0, 7, 20, 66.0, 9_000_000L, true, 44, Difficulty.MEDIUM));   // bütçe
        list.add(new Candidate(5, "Güvenli", 5, "Lig 5", 52, 57, 4, 0, 2, 20, 70.0, 900_000L, false, 52, Difficulty.EASY));    // güvenli
        list.add(new Candidate(6, "Hayal", 6, "Lig 6", 70, 75, 4, 0, 3, 20, 72.0, 2_000_000L, false, 70, Difficulty.HARD));    // hayal (50+10 < 70 ≤ 75)
        return list;
    }

    @Test
    void eachCategoryPicksItsClubAndClubsDoNotRepeat() {
        Selection s = rules.select(pool(), 50, List.of(), Difficulty.MEDIUM);
        Map<Category, Long> picked = new java.util.EnumMap<>(Category.class);
        s.cards().forEach((card) -> picked.put(card.category(), card.candidate().clubId()));
        assertThat(picked).containsEntry(Category.BEST_FIT, 1L).containsEntry(Category.YOUTH_PROJECT, 2L).containsEntry(Category.RESCUE, 3L)
                .containsEntry(Category.STRONG_BUDGET, 4L).containsEntry(Category.SAFE, 5L).containsEntry(Category.DREAM, 6L);
        assertThat(new HashSet<>(s.cards().stream().map((x) -> x.candidate().clubId()).toList())).hasSize(s.cards().size());
        assertThat(s.missing()).isEmpty();
    }

    @Test
    void theSameClubIsNotUsedTwiceAndAMissingCategoryHasAReason() {
        // Tek kulüp: yalnız ilk kategoriye girer; diğerleri ya kriter dışı ya da kullanılmış → kart yok, nedeni var.
        Selection s = rules.select(List.of(c(1, 1, 45, 80)), 50, List.of(), Difficulty.MEDIUM);
        assertThat(s.cards()).hasSize(1);
        assertThat(s.cards().get(0).category()).isEqualTo(Category.BEST_FIT);
        assertThat(s.missing()).extracting(ManagerRules.Missing::category).contains(Category.DREAM, Category.YOUTH_PROJECT);
        assertThat(s.missing()).allMatch((m) -> !m.reason().isBlank());
        assertThat(rules.select(List.of(), 50, List.of(), Difficulty.MEDIUM).cards()).isEmpty();
    }

    @Test
    void differentLeaguesArePreferredWhenPossible() {
        List<Candidate> sameLeague = new ArrayList<>(List.of(c(1, 1, 45, 80), c(7, 1, 46, 79), c(8, 9, 47, 78)));
        Selection s = rules.select(sameLeague, 50, List.of(Priority.QUICK_SUCCESS), Difficulty.MEDIUM);
        assertThat(s.cards().stream().map((x) -> x.candidate().leagueId()).distinct().count()).isEqualTo(s.cards().size());
    }

    @Test
    void prioritiesDecideTheCategoryOrder() {
        assertThat(rules.order(List.of())).first().isEqualTo(Category.BEST_FIT);
        assertThat(rules.order(List.of(Priority.QUICK_SUCCESS))).first().isEqualTo(Category.SAFE);
        assertThat(rules.order(List.of(Priority.YOUTH, Priority.FINANCIAL))).startsWith(Category.YOUTH_PROJECT, Category.STRONG_BUDGET);
        assertThat(rules.order(List.of(Priority.RESCUE))).hasSize(6).first().isEqualTo(Category.RESCUE);
    }

    @Test
    void theDifficultyPreferenceWeighsTheChoice() {
        Candidate easy = new Candidate(1, "Kolay", 1, "Lig 1", 45, 70, 5, 0, 1, 20, 70.0, 1L, false, 45, Difficulty.EASY);
        Candidate hard = new Candidate(2, "Zor", 2, "Lig 2", 45, 72, 5, 0, 18, 20, 60.0, 1L, false, 45, Difficulty.HARD);
        assertThat(rules.select(List.of(easy, hard), 50, List.of(), Difficulty.EASY).cards().get(0).candidate().clubId()).isEqualTo(1);
        assertThat(rules.select(List.of(easy, hard), 50, List.of(), Difficulty.HARD).cards().get(0).candidate().clubId()).isEqualTo(2);
    }

    @Test
    void evidenceLinesUseRealNumbers() {
        // Karar: kulüp değeri ve tahmini bütçe birlikte; üst çeyrek kulüp değerine göre.
        Candidate x = new Candidate(1, "Kulüp", 1, "EFL League One", 45, 70, 5, 5, 4, 24, 68.4, 12_000_000L, true, 45, Difficulty.MEDIUM, 4_730_000_000L);
        assertThat(rules.evidence(x)).containsExactly("Kadro gücü ligde 4. (ort. OVR 68)", "Kulüp değeri €4,7 Mr · tahmini bütçe ~€12,0 Mn, ligin üst çeyreği",
                "21 yaş altı 5 yüksek potansiyelli oyuncu");
        Candidate small = new Candidate(2, "Küçük", 1, "Lig", 45, 70, 5, 0, 4, 24, 60.0, 480_000L, false, 45, Difficulty.MEDIUM, 5_500_000L);
        assertThat(rules.evidence(small).get(1)).isEqualTo("Kulüp değeri €5,5 Mn · tahmini bütçe ~€480 Bin");
        Candidate unknown = new Candidate(3, "Bilinmez", 1, "Lig", 45, 70, 5, 0, 4, 24, 60.0, null, false, 45, Difficulty.MEDIUM, null);
        assertThat(rules.evidence(unknown).get(1)).isEqualTo("Kulüp değeri ve bütçe bilinmiyor");
    }

    /** Karar: "Bütçesi güçlü" kulüp değerine göre sıralanır (liginde kulüp değeri üst çeyrekte olanlar arasında en değerli). */
    @Test
    void strongBudgetIsRankedByClubWorth() {
        Candidate rich = new Candidate(1, "Değerli", 1, "Lig 1", 45, 60, 4, 0, 6, 20, 66.0, 2_000_000L, true, 45, Difficulty.MEDIUM, 900_000_000L);
        Candidate fit = new Candidate(2, "Uyumlu", 2, "Lig 2", 45, 85, 4, 0, 6, 20, 66.0, 9_000_000L, true, 45, Difficulty.MEDIUM, 300_000_000L);
        ManagerRules.Selection s = rules.select(List.of(rich, fit), 50, List.of(Priority.FINANCIAL), Difficulty.MEDIUM);
        assertThat(s.cards().get(0).category()).isEqualTo(Category.STRONG_BUDGET);
        assertThat(s.cards().get(0).candidate().clubId()).as("uyum değil kulüp değeri").isEqualTo(1);
    }
}
