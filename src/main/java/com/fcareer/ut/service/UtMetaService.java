package com.fcareer.ut.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.catalog.model.request.PlayerSearchRequest;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.ut.model.UtMetaModels.ChemStyleResult;
import com.fcareer.ut.model.UtMetaModels.MetaCard;
import com.fcareer.ut.model.UtMetaModels.MetaFormations;
import com.fcareer.ut.model.UtMetaModels.MetaSeed;
import com.fcareer.ut.model.UtMetaModels.UpgradeOption;
import com.fcareer.ut.model.UtMetaModels.UpgradeResult;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UtMetaService {

    private static final String RESOURCE = "seed/ut-meta.json";
    private static final String GK = "GK";
    private static final String CB = "CB";
    private static final String FB = "FB";
    private static final String CDM = "CDM";
    private static final String CM = "CM";
    private static final String CAM = "CAM";
    private static final String W = "W";
    private static final String ST = "ST";
    private static final Set<String> FB_POSITIONS = Set.of("LB", "RB", "LWB", "RWB");
    private static final Set<String> WINGER_POSITIONS = Set.of("LM", "RM", "LW", "RW");
    private static final double STAT_SHARE = 0.65;
    private static final double MAX_ATTR = 99;
    private static final int POOL_SIZE = 100;
    private static final int MAX_LIMIT = 50;
    private static final int PLAYSTYLE_PLUS_LEVEL = 2;
    private static final String TIER_C = "C";

    private final ObjectMapper objectMapper;
    private final PlayerService playerService;

    private MetaSeed seed;

    @PostConstruct
    void load() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            seed = objectMapper.readValue(in, MetaSeed.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        log.info("UT meta seed loaded formations : {}", seed.formations().size());
    }

    public MetaFormations formations() {
        return new MetaFormations(seed.snapshotNote(), seed.formations());
    }

    public List<MetaCard> tier(String position, Short gender, Short gameVersionId, int limit) {
        PlayerSearchRequest request = PlayerSearchRequest.builder()
                .gameVersionId(gameVersionId).gender(gender).sort("overall").ascending(false).page(0).size(POOL_SIZE).build();
        request.setPositions(List.of(position));
        List<Long> ids = playerService.search(request).getItems().stream().map(PlayerSummaryResponse::getId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return playerService.fitPlayers(ids, gameVersionId).stream()
                .map(p -> card(p, position, false))
                .sorted(Comparator.comparingDouble(MetaCard::metaRating).reversed())
                .limit(Math.max(1, Math.min(limit, MAX_LIMIT)))
                .toList();
    }

    public UpgradeResult upgrades(Long currentId, Short gender, Short gameVersionId, String position, int limit) {
        MetaCard current = chemStyles(currentId, gameVersionId, position);
        List<UpgradeOption> options = tier(current.position(), gender, gameVersionId, MAX_LIMIT).stream()
                .filter(c -> !c.id().equals(currentId) && c.metaRating() > current.metaRating())
                .map(c -> new UpgradeOption(c, round1(c.metaRating() - current.metaRating()), c.overall() - current.overall()))
                .sorted(Comparator.comparingDouble(UpgradeOption::deltaMeta).reversed())
                .limit(Math.max(1, Math.min(limit, MAX_LIMIT)))
                .toList();
        return new UpgradeResult(current, options, "Fiyat verisi olmadığı için maliyet/değer karşılaştırması yok; liste yalnızca meta rating farkına göre.");
    }

    public MetaCard chemStyles(Long playerId, Short gameVersionId, String position) {
        FitPlayer player = playerService.fitPlayers(List.of(playerId), gameVersionId).get(0);
        String resolved = Objects.isNull(position) || position.isBlank() ? player.getPositions().get(0) : position;
        return card(player, resolved, true);
    }

    MetaCard card(FitPlayer player, String position, boolean withStyles) {
        double base = metaRating(player.getAttrs(), player, position);
        List<ChemStyleResult> styles = new ArrayList<>();
        for (Map.Entry<String, List<String>> style : seed.chemStyles().entrySet()) {
            double rating = metaRating(boosted(player.getAttrs(), style.getValue()), player, position);
            styles.add(new ChemStyleResult(style.getKey(), round1(rating), round1(rating - base), tierOf(rating)));
        }
        ChemStyleResult best = styles.stream().max(Comparator.comparingDouble(ChemStyleResult::metaRating)).orElse(null);
        return new MetaCard(player.getId(), player.getName(), player.getAge(), player.getOverall(), position, round1(base), tierOf(base),
                Objects.isNull(best) ? null : best.style(), withStyles ? styles : null, true);
    }

    private double metaRating(Map<Attr, Integer> attrs, FitPlayer player, String position) {
        String group = group(position);
        Map<String, Integer> weights = seed.positionAttrs().get(group);
        double weightSum = 0;
        double weighted = 0;
        for (Map.Entry<String, Integer> entry : weights.entrySet()) {
            weighted += attrs.getOrDefault(Attr.requireKey(entry.getKey()), 0) * entry.getValue();
            weightSum += entry.getValue();
        }
        double stat = weighted / weightSum;
        double pace = (attrs.getOrDefault(Attr.ACCELERATION, 0) + attrs.getOrDefault(Attr.SPRINT_SPEED, 0)) / 2.0;
        double paceWeight = seed.paceWeight().getOrDefault(group, 0.0);
        double base = stat * (1 - paceWeight) + pace * paceWeight;
        return base * STAT_SHARE + player.getOverall() * (1 - STAT_SHARE) + playstyleBonus(player);
    }

    private double playstyleBonus(FitPlayer player) {
        double bonus = 0;
        for (Map.Entry<String, Integer> entry : player.getPlaystyles().entrySet()) {
            if (entry.getValue() >= PLAYSTYLE_PLUS_LEVEL) {
                bonus += seed.playstylePlusBonus().getOrDefault(normalize(entry.getKey()), 0.0);
            }
        }
        return Math.min(bonus, seed.playstylePlusCap());
    }

    private Map<Attr, Integer> boosted(Map<Attr, Integer> attrs, List<String> keys) {
        Map<Attr, Integer> copy = new EnumMap<>(Attr.class);
        copy.putAll(attrs);
        for (String key : keys) {
            copy.computeIfPresent(Attr.requireKey(key), (a, v) -> (int) Math.min(MAX_ATTR, v + seed.chemStyleBoost()));
        }
        return copy;
    }

    private String tierOf(double rating) {
        return seed.tiers().entrySet().stream()
                .filter(e -> rating >= e.getValue())
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(TIER_C);
    }

    private static String group(String position) {
        if (FB_POSITIONS.contains(position)) {
            return FB;
        }
        if (WINGER_POSITIONS.contains(position)) {
            return W;
        }
        return switch (position) {
            case GK, CB, CDM, CM, CAM -> position;
            default -> ST;
        };
    }

    private static String normalize(String name) {
        return name.toLowerCase().replaceAll("[^a-z]", "");
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
