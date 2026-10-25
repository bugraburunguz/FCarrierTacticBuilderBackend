package com.fcareer.career.academy;

import com.fcareer.ingestion.base.BaseCatalogImporter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ACADEMY-a madde 1-2: kariyer altyapı takımı içe aktarılan export'tan bulunur; takım, lig ya da kulüp kimliği kodda yazılı değildir.
 * 1. career_youthplayers oyuncularının teamplayerlinks takımı (çoğunluk).
 * 2. Oyuncu yoksa: adı çözülmemiş (`*TeamName_…`) takımlar arasından, kullanıcının kadrosundaki oyuncuların önceki takımı (previousteam; altyapıdan terfi)
 *    olarak en çok geçen ve tek olanı (en az {@value #MIN_PROMOTED} terfi).
 * 3. Kesin değilse bulunamadı.
 */
public final class AcademyDetector {

    private static final String PLACEHOLDER_PREFIX = "*TeamName";
    private static final int MIN_PROMOTED = 2;
    private static final String SUFFIX = " Youth Academy";
    private static final String FALLBACK_LABEL = "Altyapı";

    private AcademyDetector() {
    }

    /** teamId null → bulunamadı; signal: YOUTH_PLAYERS, PREVIOUS_TEAM, NOT_FOUND. */
    public record Detection(Integer teamId, String signal) {
    }

    public static Detection detect(Map<String, List<Map<String, String>>> tables, Integer userTeamId) {
        List<Map<String, String>> links = tables.getOrDefault("teamplayerlinks", List.of());
        Map<Long, Integer> playerTeam = new HashMap<>();
        links.forEach((r) -> Optional.ofNullable(toLong(r.get("playerid"))).ifPresent((p) -> playerTeam.put(p, toInt(r.get("teamid")))));
        Set<Long> youth = tables.getOrDefault("career_youthplayers", List.of()).stream().map((r) -> toLong(r.get("playerid"))).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        // Terfi etmiş gençler listede kalmış olabilir: kullanıcının takımı altyapı adayı değildir.
        Optional<Integer> byYouth = uniqueTop(youth.stream().map(playerTeam::get).filter((t) -> Objects.nonNull(t) && !t.equals(userTeamId))
                .collect(Collectors.groupingBy((t) -> t, Collectors.counting())), 1);
        if (byYouth.isPresent()) {
            return new Detection(byYouth.get(), "YOUTH_PLAYERS");
        }
        Set<Integer> unresolved = new HashSet<>();
        tables.getOrDefault("teams", List.of()).forEach((r) -> {
            if (placeholder(r.get("teamname"))) {
                Optional.ofNullable(toInt(r.get("teamid"))).ifPresent(unresolved::add);
            }
        });
        Map<Integer, Long> promoted = tables.getOrDefault("previousteam", List.of()).stream()
                .filter((r) -> Objects.nonNull(userTeamId) && userTeamId.equals(playerTeam.get(toLong(r.get("playerid")))))
                .map((r) -> toInt(r.get("previousteamid"))).filter((t) -> Objects.nonNull(t) && unresolved.contains(t))
                .collect(Collectors.groupingBy((t) -> t, Collectors.counting()));
        return uniqueTop(promoted, MIN_PROMOTED).map((t) -> new Detection(t, "PREVIOUS_TEAM")).orElse(new Detection(null, "NOT_FOUND"));
    }

    /** Altyapı "{kulüp adı} Youth Academy" (oyunun biçimi); diğer çözülmemiş adlar katalogla aynı genel kural ("Takım #id"). */
    public static String displayName(String teamName, Integer teamId, Integer academyTeamId, String clubName) {
        if (Objects.nonNull(academyTeamId) && academyTeamId.equals(teamId)) {
            return Objects.isNull(clubName) || clubName.isBlank() || placeholder(clubName) ? FALLBACK_LABEL : clubName + SUFFIX;
        }
        return BaseCatalogImporter.displayTeamName(teamName, teamId);
    }

    private static boolean placeholder(String name) {
        return Objects.nonNull(name) && name.startsWith(PLACEHOLDER_PREFIX);
    }

    /** En çok geçen ve tek olan anahtar (eşitlikte yok), en az `min` kez. */
    private static Optional<Integer> uniqueTop(Map<Integer, Long> counts, int min) {
        long max = counts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        List<Integer> top = counts.entrySet().stream().filter((e) -> e.getValue() == max).map(Map.Entry::getKey).toList();
        return max >= min && top.size() == 1 ? Optional.of(top.get(0)) : Optional.empty();
    }

    private static Long toLong(String s) {
        try {
            return Objects.isNull(s) ? null : Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer toInt(String s) {
        Long v = toLong(s);
        return Objects.isNull(v) ? null : v.intValue();
    }
}
