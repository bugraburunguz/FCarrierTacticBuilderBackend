package com.fcareer.manager;

import com.fcareer.fitengine.model.TeamSetup;
import com.fcareer.manager.ManagerModels.Background;
import com.fcareer.manager.ManagerModels.Category;
import com.fcareer.manager.ManagerModels.Difficulty;
import com.fcareer.manager.ManagerModels.Licence;
import com.fcareer.manager.ManagerModels.Priority;
import com.fcareer.manager.ManagerModels.Style;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * MGR-a madde 3–4: itibar, kulüp ihtiyacı, ilgi, zorluk, beklenti ve 6 kategorili seçim. Saf kurallar (veri ManagerRepository'den, uyum
 * ClubFitCalculator'dan); eşikler ManagerProperties'te, hepsi verified:false ("FM esinli, kalibre edilmedi").
 */
@Component
@RequiredArgsConstructor
public class ManagerRules {

    private static final int MAX_REPUTATION = 100;
    private static final List<Category> DEFAULT_ORDER = List.of(Category.BEST_FIT, Category.YOUTH_PROJECT, Category.RESCUE, Category.STRONG_BUDGET,
            Category.SAFE, Category.DREAM);
    private static final Map<Priority, Category> PRIORITY_CATEGORY = Map.of(Priority.YOUTH, Category.YOUTH_PROJECT, Priority.QUICK_SUCCESS, Category.SAFE,
            Priority.FINANCIAL, Category.STRONG_BUDGET, Priority.RESCUE, Category.RESCUE);
    private static final double MILLION = 1_000_000d;
    private static final double THOUSAND = 1_000d;

    private final ManagerProperties properties;

    public enum Interest { INTERESTED, DREAM, NONE }

    /** Kulübün ihtiyaç hesabı girdisi: prestijler 1–10, lig seviyesi 1 (en üst) ve üstü. */
    public record Prestige(long clubId, int domestic, int international, int leagueLevel) {
    }

    /**
     * Aday: need 0–100 (ihtiyaç), squadFit Takım gücü, youthDevelopment 0–10, youngCount (≤ 21 yaş, POT − OVR ≥ 10), squadRank ligdeki kadro gücü
     * sırası (1 en güçlü), leagueSize, avgOvr (en iyi 11), budgetEur (tahmini), budgetTopQuartile (liginde üst çeyrek), strengthPct kadro gücünün
     * aynı cinsiyetteki yüzdeliği, difficulty.
     */
    public record Candidate(long clubId, String clubName, Integer leagueId, String league, int need, double squadFit, int youthDevelopment, int youngCount,
                            int squadRank, int leagueSize, double avgOvr, Long budgetEur, boolean budgetTopQuartile, int strengthPct, Difficulty difficulty) {
    }

    public record Picked(Category category, Candidate candidate) {
    }

    public record Missing(Category category, String reason) {
    }

    public record Selection(List<Picked> cards, List<Missing> missing) {
    }

    public int reputation(Background background, Licence licence) {
        int base = properties.getBackgroundBase().getOrDefault(background.name(), 0);
        int bonus = properties.getLicenceBonus().getOrDefault(licence.name(), 0);
        return Math.min(MAX_REPUTATION, base + bonus);
    }

    public String band(int reputation) {
        if (reputation < 20) {
            return "Tanınmıyor";
        }
        if (reputation < 40) {
            return "Yerel tanınırlık";
        }
        if (reputation < 60) {
            return "Ulusal tanınırlık";
        }
        return reputation < 85 ? "Uluslararası tanınırlık" : "Dünya çapında";
    }

    public Interest interest(int need, int reputation) {
        if (need <= reputation + properties.getInterestMargin()) {
            return Interest.INTERESTED;
        }
        return need <= reputation + properties.getDreamMargin() ? Interest.DREAM : Interest.NONE;
    }

    /** Kulüp ihtiyacı: ağırlıklı prestij − lig seviyesi düzeltmesi; verilen kulüpler (aynı cinsiyet) arasında yüzdelik sıra 0–100 (eşitlerde ortalama). */
    public Map<Long, Integer> needs(List<Prestige> clubs) {
        Map<Long, Double> score = new HashMap<>();
        clubs.forEach((c) -> score.put(c.clubId(), properties.getDomesticWeight() * c.domestic() + properties.getInternationalWeight() * c.international()
                - properties.getLeagueLevelPenalty() * (Math.max(1, c.leagueLevel()) - 1)));
        return percentiles(score);
    }

    /** Değerlerin yüzdelik sırası 0–100 (eşitlerde ortalama sıra). */
    public static Map<Long, Integer> percentiles(Map<Long, Double> values) {
        List<Double> sorted = values.values().stream().sorted().toList();
        int n = sorted.size();
        Map<Long, Integer> out = new HashMap<>();
        values.forEach((id, v) -> {
            int below = (int) sorted.stream().filter((x) -> x < v).count();
            int equal = (int) sorted.stream().filter((x) -> x.equals(v)).count();
            double rank = below + (equal - 1) / 2.0;
            out.put(id, n <= 1 ? 100 : (int) Math.round(rank / (n - 1) * 100));
        });
        return out;
    }

    /**
     * Zorluk puanı: kadro gücünün ligdeki yeri (üst üçte bir 0, orta 1, alt 2) + prestijli kulüpte kadro üst üçte birde değilse (beklenti baskısı) 1 +
     * hayal kulübü (ihtiyaç > itibar + interestMargin) 1 + deneyimsiz menajer (itibar < inexperiencedBelow) 1. ≤ easyMax Kolay, ≥ hardMin Zor.
     */
    public Difficulty difficulty(int squadRank, int leagueSize, boolean prestigious, int need, int reputation) {
        int third = (int) Math.ceil(Math.max(1, leagueSize) / 3.0);
        int points = squadRank <= third ? 0 : squadRank <= 2 * third ? 1 : 2;
        if (prestigious && squadRank > third) {
            points++;
        }
        if (need > reputation + properties.getInterestMargin()) {
            points++;
        }
        if (reputation < properties.getInexperiencedBelow()) {
            points++;
        }
        return points <= properties.getEasyMax() ? Difficulty.EASY : points >= properties.getHardMin() ? Difficulty.HARD : Difficulty.MEDIUM;
    }

    /** Beklenti metni: itibar ≫ ihtiyaç → hızlı başarı; ≈ → makul; ≪ → sabırlı ama şüpheli. */
    public String expectation(int reputation, int need) {
        int gap = reputation - need;
        if (gap >= properties.getExpectationGap()) {
            return "Yönetim hızlı başarı bekler.";
        }
        return gap <= -properties.getExpectationGap() ? "Yönetim sabırlı ama taraftar şüpheli; ilk sezon kritik." : "Makul beklenti, zaman tanınır.";
    }

    /**
     * Aday havuzu (kaba sıra): ilgi aralığından ihtiyacı itibara en yakın poolSize kulüp + hayal aralığından en yakın dreamPoolSize kulüp. Hayal
     * ayrı kotada: yoksa itibara yakın kulüpler havuzu doldurup hayal kartını hep boş bırakır.
     */
    public List<Long> pool(Map<Long, Integer> needs, int reputation, int poolSize, int dreamPoolSize) {
        Comparator<Map.Entry<Long, Integer>> closest = Comparator.comparingInt((Map.Entry<Long, Integer> e) -> Math.abs(e.getValue() - reputation))
                .thenComparing(Map.Entry::getKey);
        List<Long> out = new ArrayList<>(needs.entrySet().stream().filter((e) -> interest(e.getValue(), reputation) == Interest.INTERESTED)
                .sorted(closest).limit(poolSize).map(Map.Entry::getKey).toList());
        needs.entrySet().stream().filter((e) -> interest(e.getValue(), reputation) == Interest.DREAM).sorted(closest).limit(dreamPoolSize)
                .map(Map.Entry::getKey).forEach(out::add);
        return out;
    }

    /** Kategori sırası: seçilen öncelikler önce (sırasıyla), sonra varsayılan sıra. */
    public List<Category> order(List<Priority> priorities) {
        Set<Category> out = new LinkedHashSet<>();
        (Objects.isNull(priorities) ? List.<Priority>of() : priorities).forEach((p) -> out.add(PRIORITY_CATEGORY.get(p)));
        out.addAll(DEFAULT_ORDER);
        return List.copyOf(out);
    }

    /** Tarz → build-up ve derinlik (Takım gücü hesabı); yüksek pres savunma yaklaşımı HIGH (Takım gücüne girmez, bilgi). */
    public static TeamSetup setup(Style style) {
        return switch (Objects.requireNonNullElse(style, Style.BALANCED)) {
            case POSSESSION -> new TeamSetup("Short", 50);
            case COUNTER -> new TeamSetup("Counter", 35);
            case HIGH_PRESS -> new TeamSetup("Balanced", 70);
            case BALANCED -> new TeamSetup("Balanced", 50);
        };
    }

    /**
     * 6 kategori, her biri bir kulüp: sırayla seçilir; aynı kulüp iki kartta olmaz, mümkünse kullanılmamış ligden. Puan uyum (Takım gücü) + zorluk
     * tercihi ağırlığı. Kategoride aday yoksa kart çıkmaz, nedeni yazar.
     */
    public Selection select(List<Candidate> candidates, int reputation, List<Priority> priorities, Difficulty preferred) {
        List<Picked> picked = new ArrayList<>();
        List<Missing> missing = new ArrayList<>();
        Set<Long> usedClubs = new HashSet<>();
        Set<Integer> usedLeagues = new HashSet<>();
        Map<Category, Predicate<Candidate>> rules = new EnumMap<>(Category.class);
        Predicate<Candidate> interested = (c) -> interest(c.need(), reputation) == Interest.INTERESTED;
        rules.put(Category.BEST_FIT, interested);
        rules.put(Category.YOUTH_PROJECT, interested.and((c) -> c.youthDevelopment() >= properties.getYouthMinDevelopment() && c.youngCount() > 0));
        rules.put(Category.RESCUE, interested.and((c) -> c.need() - c.strengthPct() >= properties.getRescueGap()));
        rules.put(Category.STRONG_BUDGET, interested.and(Candidate::budgetTopQuartile));
        rules.put(Category.SAFE, interested.and((c) -> Math.abs(c.need() - reputation) <= properties.getSafeMargin()
                && c.squadRank() <= (int) Math.ceil(Math.max(1, c.leagueSize()) / 3.0)));
        rules.put(Category.DREAM, (c) -> interest(c.need(), reputation) == Interest.DREAM);
        Map<Category, ToDoubleFunction<Candidate>> score = new EnumMap<>(Category.class);
        ToDoubleFunction<Candidate> fit = (c) -> c.squadFit() + preference(c.difficulty(), preferred);
        DEFAULT_ORDER.forEach((cat) -> score.put(cat, fit));
        score.put(Category.YOUTH_PROJECT, (c) -> c.youthDevelopment() * 2 + c.youngCount() * 3 + preference(c.difficulty(), preferred));
        score.put(Category.RESCUE, (c) -> c.need() - c.strengthPct() + preference(c.difficulty(), preferred));
        for (Category category : order(priorities)) {
            List<Candidate> eligible = candidates.stream().filter(rules.get(category)).toList();
            List<Candidate> free = eligible.stream().filter((c) -> !usedClubs.contains(c.clubId())).toList();
            List<Candidate> newLeague = free.stream().filter((c) -> Objects.isNull(c.leagueId()) || !usedLeagues.contains(c.leagueId())).toList();
            Optional<Candidate> best = (newLeague.isEmpty() ? free : newLeague).stream().max(Comparator.comparingDouble(score.get(category)));
            if (best.isEmpty()) {
                missing.add(new Missing(category, eligible.isEmpty() ? reason(category) : "Uyan kulüpler başka kartlarda gösteriliyor."));
                continue;
            }
            picked.add(new Picked(category, best.get()));
            usedClubs.add(best.get().clubId());
            if (Objects.nonNull(best.get().leagueId())) {
                usedLeagues.add(best.get().leagueId());
            }
        }
        return new Selection(picked, missing);
    }

    private double preference(Difficulty actual, Difficulty preferred) {
        if (Objects.isNull(preferred) || Objects.isNull(actual)) {
            return 0;
        }
        int distance = Math.abs(actual.ordinal() - preferred.ordinal());
        return distance == 0 ? properties.getDifficultyBonus() : distance >= 2 ? -properties.getDifficultyBonus() : 0;
    }

    private static String reason(Category category) {
        return switch (category) {
            case BEST_FIT -> "İtibar aralığında ve filtrelerde kulüp yok.";
            case YOUTH_PROJECT -> "Altyapı gelişimi yüksek ve genç yetenekli kulüp yok.";
            case RESCUE -> "Kadrosu itibarının belirgin gerisinde kalmış kulüp yok.";
            case STRONG_BUDGET -> "Bütçesi liginin üst çeyreğinde olan kulüp yok.";
            case SAFE -> "İtibarına yakın ve kadrosu ligin üst üçte birinde olan kulüp yok.";
            case DREAM -> "İtibarının biraz üstünde (hayal) kulüp yok.";
        };
    }

    /** 3 kanıt satırı, gerçek veriden: kadro gücü sırası, tahmini bütçe, genç yetenek sayısı. */
    public List<String> evidence(Candidate c) {
        String squad = String.format(Locale.ROOT, "Kadro gücü ligde %d. (ort. OVR %d)", c.squadRank(), Math.round(c.avgOvr()));
        String budget = Objects.isNull(c.budgetEur()) ? "Tahmini transfer bütçesi bilinmiyor"
                : "Tahmini transfer bütçesi " + money(c.budgetEur()) + (c.budgetTopQuartile() ? ", ligin üst çeyreği" : "");
        String youth = c.youngCount() > 0 ? properties.getYoungMaxAge() + " yaş altı " + c.youngCount() + " yüksek potansiyelli oyuncu"
                : properties.getYoungMaxAge() + " yaş altı yüksek potansiyelli oyuncu yok";
        return List.of(squad, budget, youth);
    }

    private static String money(long eur) {
        if (eur >= MILLION) {
            return String.format(new Locale("tr"), "€%.1f Mn", eur / MILLION);
        }
        return String.format(new Locale("tr"), "€%d Bin", Math.round(eur / THOUSAND));
    }
}
