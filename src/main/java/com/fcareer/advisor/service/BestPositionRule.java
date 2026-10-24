package com.fcareer.advisor.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * "En iyi mevki" seçimi (KARIYER-UX FAZ 3.1). Simetrik mevkiler (LB/RB, LWB/RWB, LM/RM, LW/RW) aynı özniteliklerden hesaplandığı için puanları
 * hep eşittir; eskiden eşitlikte ilk gelen sol taraf seçiliyordu. Kurallar:
 * <ol>
 *   <li>Listelenen bir mevki en iyi puana {@code listedPreference} kadar yakınsa listelenen mevki gösterilir (en yükseği; eşitlikte listedeki sıra).</li>
 *   <li>Simetrik eşitlikte oyuncunun listelenen mevkilerindeki taraf seçilir.</li>
 *   <li>İki taraf da listede ya da hiçbiri yoksa tercih edilen ayak: sol → L, sağ → R.</li>
 *   <li>O da yoksa ilk listelenen mevkinin tarafı.</li>
 * </ol>
 */
public final class BestPositionRule {

    private static final Map<String, String> MIRROR = Map.of("LB", "RB", "RB", "LB", "LWB", "RWB", "RWB", "LWB", "LM", "RM", "RM", "LM", "LW", "RW", "RW", "LW");
    private static final String LEFT = "L";
    private static final String RIGHT = "R";
    private static final String LEFT_FOOT = "Left";
    private static final String RIGHT_FOOT = "Right";

    private BestPositionRule() {
    }

    /** Mevkinin tarafı: simetrik mevkilerde "L" ya da "R", diğerlerinde null. */
    static String side(String position) {
        return MIRROR.containsKey(position) ? position.substring(0, 1) : null;
    }

    /** Oyuncunun tercih edilen tarafı (kural 2-4); belirlenemezse null. */
    static String preferredSide(List<String> listed, String preferredFoot) {
        Set<String> sides = listed.stream().map(BestPositionRule::side).filter(Objects::nonNull).collect(Collectors.toSet());
        if (sides.size() == 1) {
            return sides.iterator().next();
        }
        if (LEFT_FOOT.equalsIgnoreCase(preferredFoot)) {
            return LEFT;
        }
        if (RIGHT_FOOT.equalsIgnoreCase(preferredFoot)) {
            return RIGHT;
        }
        return listed.isEmpty() ? null : side(listed.get(0));
    }

    /** Mevki bazlı overall haritasından gösterilecek en iyi mevki; harita boşsa null. */
    public static Map.Entry<String, Integer> pick(Map<String, Integer> overalls, List<String> listed, String preferredFoot, int listedPreference) {
        if (overalls.isEmpty()) {
            return null;
        }
        int best = overalls.values().stream().mapToInt(Integer::intValue).max().orElseThrow();
        Map.Entry<String, Integer> listedPick = null;
        for (String position : listed) {
            Integer value = overalls.get(position);
            if (Objects.nonNull(value) && value >= best - listedPreference && (Objects.isNull(listedPick) || value > listedPick.getValue())) {
                listedPick = Map.entry(position, value);
            }
        }
        if (Objects.nonNull(listedPick)) {
            return listedPick;
        }
        List<String> tied = overalls.entrySet().stream().filter(e -> e.getValue() == best).map(Map.Entry::getKey).toList();
        String side = preferredSide(listed, preferredFoot);
        String chosen = tied.stream().filter(p -> Objects.nonNull(side) && side.equals(side(p))).findFirst().orElse(tied.get(0));
        return Map.entry(chosen, best);
    }

    /** Sıralı listede simetrik çiftlerin (ikisi de varsa) tercih edilen tarafı öne alır; diğer sıra değişmez. */
    public static List<String> sideFirst(List<String> sorted, List<String> listed, String preferredFoot) {
        return sideFirst(sorted, p -> 0, listed, preferredFoot);
    }

    /** Aynı kural, yalnız puanları eşit simetrik çiftlerde (öneri listesi: puana göre sıralı). */
    public static List<String> sideFirst(List<String> sorted, ToDoubleFunction<String> score, List<String> listed, String preferredFoot) {
        String side = preferredSide(listed, preferredFoot);
        List<String> out = new ArrayList<>(sorted);
        if (Objects.isNull(side)) {
            return out;
        }
        for (int i = 0; i < out.size(); i++) {
            String position = out.get(i);
            String mirror = MIRROR.get(position);
            int j = Objects.isNull(mirror) ? -1 : out.indexOf(mirror);
            if (j > i && !side.equals(side(position)) && score.applyAsDouble(position) == score.applyAsDouble(mirror)) {
                out.set(i, mirror);
                out.set(j, position);
            }
        }
        return out;
    }
}
