package com.fcareer.advisor.service;

import com.fcareer.advisor.model.response.AdvisorResponses.Conversion;
import com.fcareer.advisor.model.response.AdvisorResponses.PositionOption;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * FIT-3 madde 12: en iyi mevki v2 — rol bazlı dönüşüm. "En iyi mevki" EA'nın mevki OVR formülünden kalır; öneri her mevkideki en iyi rol Güç'üne bakar
 * (mevki dışı çarpanı dahil). Listede olmayan (ayna taraf da listede sayılır) mevkideki en iyi Güç, listedeki en iyiyi `gain` kadar geçiyor ve EA OVR'si orada
 * oyuncunun OVR'sinden en çok `maxOverallDrop` düşükse dönüşüm önerilir.
 */
public final class ConversionRule {

    private ConversionRule() {
    }

    public static Optional<Conversion> find(List<PositionOption> options, List<String> listed, Map<String, Integer> overalls, int overall, double gain,
                                            int maxOverallDrop, Function<String, Optional<String>> positionPlanName) {
        return find(options, listed, overalls, overall, gain, maxOverallDrop, positionPlanName, (o) -> true);
    }

    /** eligible (FIT-3 kararı 2): aday mevki takımın ilk 11'i ölçüsünde güçlü mü (alt üçte biri geçiyor mu; gençte 2 sezonluk tahminle). */
    public static Optional<Conversion> find(List<PositionOption> options, List<String> listed, Map<String, Integer> overalls, int overall, double gain,
                                            int maxOverallDrop, Function<String, Optional<String>> positionPlanName, Predicate<PositionOption> eligible) {
        Set<String> zone = new HashSet<>(listed);
        listed.forEach((p) -> Optional.ofNullable(SideChoice.mirror(p)).ifPresent(zone::add));
        Optional<PositionOption> from = options.stream().filter((o) -> zone.contains(o.getPosition())).max(Comparator.comparingDouble(PositionOption::getScore));
        // Önce EA OVR koşulu, sonra en yüksek Güç (en iyi mevki OVR'ye takılırsa ikinci aday denenir).
        Optional<PositionOption> to = options.stream().filter((o) -> !zone.contains(o.getPosition()))
                .filter((o) -> Objects.nonNull(overalls.get(o.getPosition())) && overalls.get(o.getPosition()) >= overall - maxOverallDrop)
                .filter((o) -> o.getScore() - from.map(PositionOption::getScore).orElse(Double.MAX_VALUE) >= gain)
                .filter(eligible)
                .max(Comparator.comparingDouble(PositionOption::getScore));
        if (from.isEmpty() || to.isEmpty()) {
            return Optional.empty();
        }
        PositionOption a = from.get();
        PositionOption b = to.get();
        Integer overallAt = overalls.get(b.getPosition());
        if (b.getScore() - a.getScore() < gain) {
            return Optional.empty();
        }
        Optional<String> plan = positionPlanName.apply(b.getPosition());
        String text = String.format(Locale.ROOT, "%s listeli ama %s'de daha etkili: %s · %s gücü %d, %s · %s %d.%s", String.join("/", listed), b.getPosition(),
                b.getPosition(), base(b.getRoleName()), Math.round(b.getScore()), a.getPosition(), base(a.getRoleName()), Math.round(a.getScore()),
                plan.map((p) -> " " + p + " önerilir.").orElse(""));
        return Optional.of(Conversion.builder().position(b.getPosition()).roleName(b.getRoleName()).strength(b.getScore()).fromPosition(a.getPosition())
                .fromRoleName(a.getRoleName()).fromStrength(a.getScore()).overallAt(overallAt).planName(plan.orElse(null)).text(text).build());
    }

    private static String base(String roleName) {
        return roleName.replaceAll("\\s*\\(.*\\)\\s*$", "");
    }
}
