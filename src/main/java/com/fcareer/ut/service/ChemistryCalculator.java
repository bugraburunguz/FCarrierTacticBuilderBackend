package com.fcareer.ut.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** FC27 kimyası (RULE-01): kulüp/lig/ülke eşikleri ile Icon, Hero, Hall of FUT ve menajer kuralları; yalnız ilk 11, kimlikler id ile (adla değil). */
@Component
public class ChemistryCalculator {

    private static final String ICON = "ICON";
    private static final String HERO = "HERO";
    private static final String HALL_OF_FUT = "HOF";

    public record ChemCard(long clubId, long leagueId, long nationId, int gender, String type, List<String> positions) {
    }

    public record ChemSlot(String position, ChemCard card) {
    }

    public record ChemManager(long nationId, long leagueId) {
    }

    public record ChemResult(List<Integer> perSlot, int total) {
    }

    private final UtRules rules;

    public ChemistryCalculator(UtRules rules) {
        this.rules = rules;
    }

    public ChemResult compute(List<ChemSlot> slots, ChemManager manager) {
        JsonNode config = rules.chemistry();
        List<ChemCard> placed = slots.stream().map(s -> Objects.nonNull(s.card()) && s.card().positions().contains(s.position()) ? s.card() : null).toList();
        Map<Long, Integer> clubs = new HashMap<>();
        Map<Long, Integer> nations = new HashMap<>();
        Map<String, Integer> leagues = new HashMap<>();
        Set<String> allLeagues = new HashSet<>();
        placed.stream().filter(Objects::nonNull).forEach(c -> allLeagues.add(leagueKey(c)));
        for (ChemCard card : placed) {
            if (Objects.isNull(card)) {
                continue;
            }
            clubs.merge(card.clubId(), 1, Integer::sum);
            nations.merge(card.nationId(), 1, Integer::sum);
            if (ICON.equals(card.type())) {
                nations.merge(card.nationId(), config.at("/icon/nationIncrement").asInt(), Integer::sum);
                allLeagues.forEach(l -> leagues.merge(l, config.at("/icon/everyLeagueInXiIncrement").asInt(), Integer::sum));
            } else {
                leagues.merge(leagueKey(card), 1, Integer::sum);
                if (HERO.equals(card.type()) || HALL_OF_FUT.equals(card.type())) {
                    nations.merge(card.nationId(), config.at("/hero/nationIncrement").asInt(), Integer::sum);
                    leagues.merge(leagueKey(card), config.at("/hero/leagueIncrement").asInt(), Integer::sum);
                }
            }
        }
        int max = config.get("maxPerPlayer").asInt();
        List<Integer> perSlot = placed.stream().map(card -> playerChem(card, clubs, leagues, nations, manager, max)).toList();
        int total = Math.min(config.get("maxSquad").asInt(), perSlot.stream().mapToInt(Integer::intValue).sum());
        return new ChemResult(perSlot, total);
    }

    private int playerChem(ChemCard card, Map<Long, Integer> clubs, Map<String, Integer> leagues, Map<Long, Integer> nations, ChemManager manager, int max) {
        if (Objects.isNull(card)) {
            return rules.chemistry().get("outOfPositionChem").asInt();
        }
        if (Objects.nonNull(card.type())) {
            return max;
        }
        int points = points(clubs.getOrDefault(card.clubId(), 0), rules.thresholds("club"))
                + points(leagues.getOrDefault(leagueKey(card), 0), rules.thresholds("league"))
                + points(nations.getOrDefault(card.nationId(), 0), rules.thresholds("nation"));
        boolean managerMatch = Objects.nonNull(manager) && (card.nationId() == manager.nationId() || card.leagueId() == manager.leagueId());
        return Math.min(max, points + (managerMatch ? rules.chemistry().at("/manager/bonus").asInt() : 0));
    }

    private static int points(int count, List<Integer> thresholds) {
        return (int) thresholds.stream().filter(t -> count >= t).count();
    }

    /** Kadın ve erkek ligleri aynı ad taşısa da bağlanmaz. */
    private static String leagueKey(ChemCard card) {
        return card.gender() + ":" + card.leagueId();
    }
}
