package com.fcareer.ingestion.provider;

import com.fcareer.fitengine.model.Attr;
import com.fcareer.ingestion.configuration.IngestionProperties;
import com.fcareer.ingestion.model.ExternalPlayer;
import com.fcareer.ingestion.model.ExternalPlayerPage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "fcareer.ingestion", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockProvider implements ExternalDataProvider {

    private static final String MOCK = "mock";
    private static final long BASE_ID = 900_000L;
    private static final int WOMEN_CLUB_FROM = 16;
    private static final List<String> POSITIONS = List.of("GK", "RB", "CB", "CB", "LB", "CDM", "CM", "CAM", "RW", "LW", "ST");
    private static final List<String> PLAYSTYLES = List.of("FinesseShot", "WhippedPass", "IncisivePass", "Technical",
            "Anticipate", "Intercept", "Relentless", "QuickStep", "Jockey", "PowerShot");
    private static final List<String> LEAGUES = List.of("Mock League One", "Mock League Two");

    private final IngestionProperties properties;

    @Override
    public String name() {
        return MOCK;
    }

    @Override
    public ExternalPlayerPage fetch(int offset, int limit) {
        int total = properties.getMock().getPlayerCount();
        List<ExternalPlayer> items = new ArrayList<>();
        for (int i = offset; i < Math.min(total, offset + limit); i++) {
            items.add(generate(i));
        }
        return new ExternalPlayerPage(items, total);
    }

    private ExternalPlayer generate(int index) {
        Random random = new Random(properties.getMock().getSeed() * 31 + index);
        String position = POSITIONS.get(index % POSITIONS.size());
        int overall = 55 + random.nextInt(30);
        Map<Attr, Integer> attrs = new EnumMap<>(Attr.class);
        boolean goalkeeper = "GK".equals(position);
        for (Attr attr : Attr.values()) {
            boolean gkAttr = attr.name().startsWith("GK_");
            int base = gkAttr == goalkeeper ? overall : 20;
            attrs.put(attr, Math.max(10, Math.min(99, base - 12 + random.nextInt(25))));
        }
        Map<String, Integer> playstyles = new java.util.LinkedHashMap<>();
        if (random.nextInt(3) == 0) {
            playstyles.put(PLAYSTYLES.get(random.nextInt(PLAYSTYLES.size())), 1 + random.nextInt(2));
        }
        int clubIndex = index % 20;
        return new ExternalPlayer(BASE_ID + index, "Mock Player " + index, LocalDate.of(1990 + random.nextInt(14), 1 + random.nextInt(12), 1 + random.nextInt(28)),
                "Mockland", 165 + random.nextInt(30), 60 + random.nextInt(30), random.nextBoolean() ? "Right" : "Left",
                LEAGUES.get(clubIndex % LEAGUES.size()), 5_000L + clubIndex, "Mock FC " + clubIndex,
                overall, List.of(position), 1 + random.nextInt(5), 1 + random.nextInt(5), attrs, playstyles,
                clubIndex >= WOMEN_CLUB_FROM ? 1 : 0, null);
    }
}
