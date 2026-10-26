package com.fcareer.development.service;

import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Tek sezonluk büyüme (futureFit +1 sezon, E3-6). Tanım:
 *   sezon kazancı = round((POT − OVR) × yaşÇarpanı(yaş));  projectedRating = min(POT, OVR + kazanç);
 *   attribute artışı = round(kazanç × trainability), hız/çeviklik grubu 0,3×, diğerleri 1,0×, 99'da kırpılır.
 * Düşüş (GT-a, gruplar sırayla): fiziksel (hız, çeviklik, dayanıklılık, güç, sıçrama, denge) yaş ≥ declineFromAge, teknik (pas, dribling, şut, top
 *   kesme, kafa, kaleci) yaş ≥ technicalDeclineFromAge, zihinsel ve bitiricilik yaş ≥ mentalDeclineFromAge; her grup sezonda
 *   declineBase + declineStep×(yaş − başlangıç) puan düşer. OVR düşüşü = Σ grup × ovrPer{grup} (yaş kuralı; plan değiştirmez).
 *   Plan: plan attribute'ları planDeclineDelay yıl geç ve planDeclineFactor kadar yavaş düşer; aradaki fark plan dışı düşen attribute'lara dağılır
 *   (plan dağılımı değiştirir, toplamı değil).
 * POT ya da yaş bilinmiyorsa büyüme/düşüş yok (tahmin uydurulmaz). Katsayılar `verified:false` ilk kalibrasyondur.
 */
@Component
public class GrowthProjector {

    private static final int MAX_ATTR = 99;
    public static final Set<Attr> PHYSICAL = Set.of(Attr.ACCELERATION, Attr.SPRINT_SPEED, Attr.AGILITY, Attr.BALANCE, Attr.JUMPING, Attr.STAMINA, Attr.STRENGTH);
    /** GT-a: teknik düşüş grubu (pas, dribling, şut, top kesme, kafa, kaleci); geri kalan (zihinsel ve bitiricilik) en geç düşer. */
    public static final Set<Attr> TECHNICAL = Set.of(Attr.BALL_CONTROL, Attr.DRIBBLING, Attr.SHORT_PASSING, Attr.LONG_PASSING, Attr.CROSSING, Attr.CURVE,
            Attr.FK_ACCURACY, Attr.SHOT_POWER, Attr.LONG_SHOTS, Attr.VOLLEYS, Attr.PENALTIES, Attr.HEADING, Attr.STANDING_TACKLE, Attr.SLIDING_TACKLE,
            Attr.GK_DIVING, Attr.GK_HANDLING, Attr.GK_KICKING, Attr.GK_POSITIONING, Attr.GK_REFLEXES);

    /** GT-a: düşüş sırası — fiziksel önce, teknik sonra, zihinsel ve bitiricilik en geç. */
    public enum DeclineGroup { PHYSICAL, TECHNICAL, MENTAL }

    public static DeclineGroup group(Attr attr) {
        return PHYSICAL.contains(attr) ? DeclineGroup.PHYSICAL : TECHNICAL.contains(attr) ? DeclineGroup.TECHNICAL : DeclineGroup.MENTAL;
    }
    private static final Set<Attr> HARD_TO_TRAIN = Set.of(Attr.ACCELERATION, Attr.SPRINT_SPEED, Attr.AGILITY, Attr.BALANCE, Attr.JUMPING);
    private static final Set<Attr> GOALKEEPING = Set.of(Attr.GK_DIVING, Attr.GK_HANDLING, Attr.GK_KICKING, Attr.GK_POSITIONING, Attr.GK_REFLEXES);

    @Getter
    @Setter
    @ConfigurationProperties(prefix = "fcareer.growth")
    public static class Config {
        /** yaş ≤ ageN → çarpan factorN; hepsinin üstü 0. */
        private int age1 = 19;
        private double factor1 = 0.55;
        private int age2 = 21;
        private double factor2 = 0.45;
        private int age3 = 23;
        private double factor3 = 0.35;
        private int age4 = 25;
        private double factor4 = 0.25;
        private int age5 = 27;
        private double factor5 = 0.12;
        private int age6 = 29;
        private double factor6 = 0.05;
        private double hardToTrain = 0.3;
        private int declineFromAge = 32;
        private double declineBase = 1.0;
        private double declineStep = 0.4;
        private double ovrPerPhysical = 0.6;
        /** GT-a (verified:false, kaynak: Buğra oyun tecrübesi, ölçülmedi): teknik grubun ve zihinsel/bitiricilik grubunun düşüş başlangıç yaşı. */
        private int technicalDeclineFromAge = 33;
        private int mentalDeclineFromAge = 35;
        /** GT-a (verified:false): grup düşüş puanı başına OVR düşüşü (fiziksel ovrPerPhysical). */
        private double ovrPerTechnical = 0.3;
        private double ovrPerMental = 0.2;
        /** GT-a (verified:false): plan attribute'ları kaç yıl geç ve hangi çarpanla düşer. */
        private int planDeclineDelay = 2;
        private double planDeclineFactor = 0.5;
        /** FIT-3 madde 9 (verified:false): mevki/rol planında sezon kazancı bütçesinin plan attribute'larına giden payı; kalanı diğerlerine eşit. */
        private double planShare = 0.45;
        /** FIT-3 madde 9 (verified:false): rol planı Role+ ailesini kaç sezonda bir kademe artırır (Role+ → Role++). */
        private int familiaritySeasonsPerLevel = 1;
        /** GROWTH-CAL (verified:false, kaynak: Buğra oyun tecrübesi, ölçülmedi): altyapıdaki (A takımda değil) oyuncunun sezon OVR kazancı tavanı. */
        private int academyMaxGain = 5;
        /** GROWTH-CAL (verified:false, ölçülmedi): A takımdaki oyuncunun sezon OVR kazancı tavanı; kalibrasyon raporu gerçek veri verince ayarlanır. */
        private int firstTeamMaxGain = 8;
    }

    /** Oyuncunun o sezon nerede geliştiği: altyapı (tavan academyMaxGain) ya da A takım (tavan firstTeamMaxGain). */
    public enum Stage { ACADEMY, FIRST_TEAM }

    public record Projection(int projectedRating, FitPlayer grown) {
    }

    private final Config config;

    @Autowired
    public GrowthProjector(Config config) {
        this.config = config;
    }

    public Projection project(FitPlayer player) {
        return project(player, Stage.FIRST_TEAM);
    }

    public Projection project(FitPlayer player, Stage stage) {
        if (declining(player)) {
            return decline(player, List.of());
        }
        int gain = seasonGain(player, stage);
        if (gain <= 0) {
            return new Projection(player.getOverall(), player);
        }
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        player.getAttrs().forEach((attr, value) -> attrs.put(attr, Math.min(MAX_ATTR, value + (int) Math.round(gain * (HARD_TO_TRAIN.contains(attr) ? config.getHardToTrain() : 1.0)))));
        int projected = Math.min(MAX_ATTR, player.getOverall() + gain);
        FitPlayer grown = FitPlayer.builder().id(player.getId()).name(player.getName()).age(player.getAge() + 1).overall(projected)
                .positions(player.getPositions()).attrs(attrs).playstyles(player.getPlaystyles()).acceleRate(player.getAcceleRate()).potential(player.getPotential())
                .valueEur(player.getValueEur()).gender(player.getGender()).preferredFoot(player.getPreferredFoot()).weakFoot(player.getWeakFoot())
                .skillMoves(player.getSkillMoves()).heightCm(player.getHeightCm()).roleFamiliarity(player.getRoleFamiliarity()).build();
        return new Projection(projected, grown);
    }

    /**
     * FIT-3 madde 9: plan farkındalıklı bir sezon. Plan boşsa (Dengeli) {@link #project(FitPlayer)} aynen. Planlıysa sezon bütçesi
     * (kazanç × planın alanındaki attribute sayısı: saha planında saha, kaleci planında kaleci attribute'ları) `planShare` ile plana, kalanı alandaki
     * diğerlerine eşit bölünür.
     * Hız grubu 0,3× ve 99 tavanı aynen. Düşüş yaşında (GT-a) plan attribute'ları geç ve yavaş düşer, kart OVR Dengeli ile aynı. projectedRating
     * bugünkü tanım (OVR + kazanç); mevki OVR etkisi çağıran tarafta position-overall-weights ile hesaplanır.
     */
    public Projection project(FitPlayer player, Collection<Attr> plan) {
        return project(player, plan, Stage.FIRST_TEAM);
    }

    public Projection project(FitPlayer player, Collection<Attr> plan, Stage stage) {
        if (!plan.isEmpty() && declining(player)) {
            return decline(player, plan);
        }
        if (plan.isEmpty() || declining(player)) {
            return project(player, stage);
        }
        int gain = seasonGain(player, stage);
        if (gain <= 0) {
            return new Projection(player.getOverall(), player);
        }
        return apply(player, increments(player, plan, stage), Math.min(MAX_ATTR, player.getOverall() + gain));
    }

    /**
     * Bir sezonun attribute artışları (yuvarlamadan önce, hız grubu çarpanı dahil). Plan boşsa Dengeli (her attribute kazanç kadar); düşüş yaşında ya da
     * kazanç yoksa boş. Plan ölçeklemesi (ana mevkide OVR artışını Dengeli'ye eşitleme) çağıran tarafta: PlanProjector.
     */
    public Map<Attr, Double> increments(FitPlayer player, Collection<Attr> plan) {
        return increments(player, plan, Stage.FIRST_TEAM);
    }

    public Map<Attr, Double> increments(FitPlayer player, Collection<Attr> plan, Stage stage) {
        int gain = declining(player) ? 0 : seasonGain(player, stage);
        Map<Attr, Double> out = new EnumMap<>(Attr.class);
        if (gain <= 0) {
            return out;
        }
        boolean goalkeeperPlan = plan.stream().anyMatch(GOALKEEPING::contains);
        // Bütçe planın alanında: kaleci planı yalnız kaleci attribute'ları, saha planı yalnız saha attribute'ları.
        List<Attr> relevant = player.getAttrs().keySet().stream().filter((a) -> plan.isEmpty() || goalkeeperPlan == GOALKEEPING.contains(a)).toList();
        long inPlan = relevant.stream().filter(plan::contains).count();
        long others = relevant.size() - inPlan;
        double budget = (double) gain * relevant.size();
        double perPlan = inPlan == 0 ? 0 : config.getPlanShare() * budget / inPlan;
        double perOther = plan.isEmpty() ? gain : others == 0 ? 0 : (1 - config.getPlanShare()) * budget / others;
        player.getAttrs().keySet().forEach((attr) -> {
            double base = !relevant.contains(attr) ? 0 : plan.contains(attr) ? perPlan : perOther;
            out.put(attr, base * (HARD_TO_TRAIN.contains(attr) ? config.getHardToTrain() : 1.0));
        });
        return out;
    }

    /** Artışları uygular (yuvarlama, 99 tavanı); yaş bir artar, OVR verilen. */
    public Projection apply(FitPlayer player, Map<Attr, Double> increments, int overall) {
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        player.getAttrs().forEach((attr, value) -> attrs.put(attr, Math.min(MAX_ATTR, value + (int) Math.round(increments.getOrDefault(attr, 0.0)))));
        return new Projection(overall, copy(player, attrs, overall));
    }

    public Config config() {
        return config;
    }

    /** Düşüş yaşında mı: ilk düşen grup (en erken başlangıç yaşı) başladıysa. */
    public boolean declining(FitPlayer player) {
        int first = Math.min(config.getDeclineFromAge(), Math.min(config.getTechnicalDeclineFromAge(), config.getMentalDeclineFromAge()));
        return Objects.nonNull(player.getAge()) && player.getAge() >= first;
    }

    private int startAge(DeclineGroup group) {
        return switch (group) {
            case PHYSICAL -> config.getDeclineFromAge();
            case TECHNICAL -> config.getTechnicalDeclineFromAge();
            case MENTAL -> config.getMentalDeclineFromAge();
        };
    }

    /** Grubun sezon düşüşü (puan): başlangıç yaşından önce 0, sonra declineBase + declineStep × (yaş − başlangıç). */
    private double groupDecline(int age, int from) {
        return age < from ? 0 : config.getDeclineBase() + config.getDeclineStep() * (age - from);
    }

    /**
     * Bir sezonun attribute düşüşleri (yuvarlamadan önce). Plan boşsa Dengeli. Planlıysa plan attribute'ları planDeclineDelay yıl geç ve
     * planDeclineFactor kadar yavaş düşer; korunan puan plan dışı, düşmekte olan attribute'lara oranla dağılır (toplam Dengeli ile aynı).
     */
    public Map<Attr, Double> declines(FitPlayer player, Collection<Attr> plan) {
        Map<Attr, Double> out = new EnumMap<>(Attr.class);
        if (!declining(player)) {
            return out;
        }
        int age = player.getAge();
        Map<Attr, Double> balanced = new EnumMap<>(Attr.class);
        player.getAttrs().keySet().forEach((attr) -> balanced.put(attr, groupDecline(age, startAge(group(attr)))));
        if (plan.isEmpty()) {
            return balanced;
        }
        double total = balanced.values().stream().mapToDouble(Double::doubleValue).sum();
        double inPlan = 0;
        double others = 0;
        for (Attr attr : player.getAttrs().keySet()) {
            if (plan.contains(attr)) {
                double slowed = groupDecline(age, startAge(group(attr)) + config.getPlanDeclineDelay()) * config.getPlanDeclineFactor();
                out.put(attr, slowed);
                inPlan += slowed;
            } else {
                others += balanced.get(attr);
            }
        }
        double scale = others > 0 ? Math.max(0, total - inPlan) / others : 0;
        for (Attr attr : player.getAttrs().keySet()) {
            if (!plan.contains(attr)) {
                out.put(attr, balanced.get(attr) * scale);
            }
        }
        return out;
    }

    /** Düşüş sezonu: attribute'lar düşüş kadar azalır (en az 1); kart OVR yaş kuralıyla (Dengeli grup düşüşlerinden), plandan bağımsız. */
    private Projection decline(FitPlayer player, Collection<Attr> plan) {
        Map<Attr, Integer> points = rounded(declines(player, List.of()));
        if (!plan.isEmpty()) {
            // Planlı dağılım Dengeli'nin tam sayı toplamına yuvarlanır (en büyük kalan): plan toplamı değiştirmez.
            points = roundedTo(declines(player, plan), points.values().stream().mapToInt(Integer::intValue).sum());
        }
        Map<Attr, Integer> drop = points;
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        player.getAttrs().forEach((attr, value) -> attrs.put(attr, Math.max(1, value - drop.getOrDefault(attr, 0))));
        int age = player.getAge();
        double ovrLoss = config.getOvrPerPhysical() * Math.round(groupDecline(age, config.getDeclineFromAge()))
                + config.getOvrPerTechnical() * Math.round(groupDecline(age, config.getTechnicalDeclineFromAge()))
                + config.getOvrPerMental() * Math.round(groupDecline(age, config.getMentalDeclineFromAge()));
        int projected = Math.max(1, player.getOverall() - (int) Math.round(ovrLoss));
        return new Projection(projected, copy(player, attrs, projected));
    }

    private static Map<Attr, Integer> rounded(Map<Attr, Double> values) {
        Map<Attr, Integer> out = new EnumMap<>(Attr.class);
        values.forEach((attr, v) -> out.put(attr, (int) Math.round(v)));
        return out;
    }

    /** Değerleri toplamı `total` olacak biçimde tam sayıya yuvarlar: önce aşağı, kalan puanlar en büyük kesirli kısma. */
    private static Map<Attr, Integer> roundedTo(Map<Attr, Double> values, int total) {
        Map<Attr, Integer> out = new EnumMap<>(Attr.class);
        values.forEach((attr, v) -> out.put(attr, (int) Math.floor(v)));
        int left = total - out.values().stream().mapToInt(Integer::intValue).sum();
        List<Attr> byRemainder = values.keySet().stream()
                .sorted(java.util.Comparator.comparingDouble((Attr a) -> values.get(a) - Math.floor(values.get(a))).reversed().thenComparing(Enum::ordinal)).toList();
        for (int i = 0; i < left && i < byRemainder.size(); i++) {
            out.merge(byRemainder.get(i), 1, Integer::sum);
        }
        return out;
    }

    private static FitPlayer copy(FitPlayer player, Map<Attr, Integer> attrs, int overall) {
        return FitPlayer.builder().id(player.getId()).name(player.getName()).age(player.getAge() + 1).overall(overall)
                .positions(player.getPositions()).attrs(attrs).playstyles(player.getPlaystyles()).acceleRate(player.getAcceleRate()).potential(player.getPotential())
                .valueEur(player.getValueEur()).gender(player.getGender()).preferredFoot(player.getPreferredFoot()).weakFoot(player.getWeakFoot())
                .skillMoves(player.getSkillMoves()).heightCm(player.getHeightCm()).roleFamiliarity(player.getRoleFamiliarity()).build();
    }

    /** Sezon kazancı: kalan boşluk × yaş çarpanı, aşamanın tavanıyla sınırlı (tavan plan dağılımından önce; plan toplamı değiştirmez). */
    private int seasonGain(FitPlayer player, Stage stage) {
        if (Objects.isNull(player.getPotential()) || Objects.isNull(player.getAge())) {
            return 0;
        }
        int headroom = Math.max(0, Math.min(MAX_ATTR, player.getPotential()) - player.getOverall());
        int cap = stage == Stage.ACADEMY ? config.getAcademyMaxGain() : config.getFirstTeamMaxGain();
        return Math.min(cap, (int) Math.round(headroom * ageFactor(player.getAge())));
    }

    private double ageFactor(int age) {
        if (age <= config.getAge1()) {
            return config.getFactor1();
        }
        if (age <= config.getAge2()) {
            return config.getFactor2();
        }
        if (age <= config.getAge3()) {
            return config.getFactor3();
        }
        if (age <= config.getAge4()) {
            return config.getFactor4();
        }
        if (age <= config.getAge5()) {
            return config.getFactor5();
        }
        return age <= config.getAge6() ? config.getFactor6() : 0;
    }
}
