package com.fcareer.catalog.alias;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Kulüp adı veri kalitesi: (1) aynı lig+cinsiyette, aksanı düşmüş ya da aksanı çıkarılmış hâli başka bir kulüple aynı olan ad yok
 * ("Atltico de SL" ↔ "Atlético de SL"); (2) adda U+FFFD yok; (3) yer tutucu ("*TeamName…") ad yok. İhlaller açılışta uyarı olarak loglanır.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClubDataQuality implements ApplicationRunner {

    public static final String PLACEHOLDER_PREFIX = "*TeamName";

    private final JdbcTemplate jdbc;

    /** Aksanlı harfleri düşürerek (ASCII dışı atılır) ve aksanı sıyırarak iki anahtar üretir. */
    static List<String> keys(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        String dropped = lower.replaceAll("[^a-z0-9]", "");
        String stripped = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("[^a-z0-9]", "");
        return dropped.equals(stripped) ? List.of(dropped) : List.of(dropped, stripped);
    }

    public static boolean isPlaceholder(String name) {
        return name != null && name.startsWith(PLACEHOLDER_PREFIX);
    }

    public List<String> violations() {
        List<String> found = new ArrayList<>();
        Map<String, String> seen = new HashMap<>();
        jdbc.query("SELECT id, name, league_id, gender FROM club ORDER BY id", rs -> {
            String name = rs.getString("name");
            long id = rs.getLong("id");
            if (name.contains("\uFFFD")) {
                found.add("club " + id + ": ad U+FFFD içeriyor");
            }
            if (isPlaceholder(name)) {
                found.add("club " + id + ": yer tutucu ad");
            }
            String scope = rs.getInt("league_id") + "/" + rs.getInt("gender") + "/";
            for (String key : keys(name)) {
                String previous = seen.putIfAbsent(scope + key, id + ":" + name);
                if (previous != null && !previous.endsWith(":" + name)) {
                    found.add("club " + id + " \"" + name + "\" ↔ " + previous + ": aksan farkıyla aynı ad");
                }
            }
        });
        return found;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> found = violations();
        if (!found.isEmpty()) {
            log.warn("Club data quality violations count : {} first : {}", found.size(), found.get(0));
        }
    }
}
