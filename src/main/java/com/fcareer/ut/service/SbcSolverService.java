package com.fcareer.ut.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fcareer.ut.model.SbcModels.FloorBand;
import com.fcareer.ut.model.SbcModels.Pick;
import com.fcareer.ut.model.SbcModels.PoolEntry;
import com.fcareer.ut.model.SbcModels.Rarity;
import com.fcareer.ut.model.SbcModels.ScoreRow;
import com.fcareer.ut.model.SbcModels.ScoreSeed;
import com.fcareer.ut.model.SbcModels.ScoreTable;
import com.fcareer.ut.model.SbcModels.StreamlinedRequest;
import com.fcareer.ut.model.SbcModels.StreamlinedResult;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class SbcSolverService {

    private static final String RESOURCE = "seed/sbc-score.json";
    private static final long INF = Long.MAX_VALUE / 4;
    private static final long MAX_DP_WORK = 60_000_000L;
    private static final int WORD_BITS = 64;
    private static final int MIN_RATING = 45;
    private static final int MAX_RATING = 99;

    private final ObjectMapper objectMapper;

    private ScoreSeed seed;

    @PostConstruct
    void load() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            seed = objectMapper.readValue(in, ScoreSeed.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        log.info("SBC score table loaded version : {}", seed.version());
    }

    public ScoreTable scoreTable() {
        List<ScoreRow> rows = new ArrayList<>();
        for (Rarity rarity : Rarity.values()) {
            for (int rating = MAX_RATING; rating >= MIN_RATING; rating--) {
                int score = scoreOf(rating, rarity);
                if (score > 0 && (rarity == Rarity.REGULAR || explicit(rating, rarity))) {
                    rows.add(new ScoreRow(rating, rarity, score));
                }
            }
        }
        return new ScoreTable(seed.version(), seed.note(), rows);
    }

    /** Özel kart tablosunda olmayan rating, bir alt özel değer ile regular değerin yükseğini alır: rating artınca skor azalmaz (BE-BUG-25). */
    public int scoreOf(int rating, Rarity rarity) {
        int regular = regularScore(rating);
        if (rarity == Rarity.REGULAR) {
            return regular;
        }
        Integer special = specialMap(rarity).get(rating);
        if (Objects.nonNull(special)) {
            return special;
        }
        return Math.max(regular, specialMap(rarity).entrySet().stream().filter(e -> e.getKey() < rating).mapToInt(Map.Entry::getValue).max().orElse(0));
    }

    private int regularScore(int rating) {
        Integer regular = seed.regular().get(rating);
        if (Objects.nonNull(regular)) {
            return regular;
        }
        return seed.regularFloor().stream().filter(b -> rating >= b.min() && rating <= b.max()).mapToInt(FloorBand::score).findFirst().orElse(0);
    }

    public StreamlinedResult solveStreamlined(StreamlinedRequest request) {
        List<Group> groups = groups(request);
        boolean coinless = request.pool().stream().anyMatch(p -> !p.untradeable() && Objects.isNull(p.priceEach()));
        long available = groups.stream().mapToLong(Group::value).sum();
        if (available < request.targetScore()) {
            return new StreamlinedResult(false, request.targetScore(), 0, 0, 0, 0, coinless, false, List.of(), available,
                    "Havuzdaki itemlar hedef skora yetmiyor.");
        }
        long gcd = groups.stream().mapToLong(Group::value).reduce(0, SbcSolverService::gcd);
        int target = (int) ((request.targetScore() + gcd - 1) / gcd);
        boolean exact = (long) groups.size() * target <= MAX_DP_WORK;
        int[] taken = exact ? knapsack(groups, gcd, target) : greedy(groups, request.targetScore());
        return result(request, groups, taken, coinless, !exact);
    }

    private int[] knapsack(List<Group> groups, long gcd, int target) {
        long[] cost = new long[target + 1];
        long[] sum = new long[target + 1];
        Arrays.fill(cost, INF);
        Arrays.fill(sum, INF);
        cost[0] = 0;
        sum[0] = 0;
        int words = target / WORD_BITS + 1;
        long[][] took = new long[groups.size()][words];
        int[] capSource = new int[groups.size()];
        for (int i = 0; i < groups.size(); i++) {
            Group group = groups.get(i);
            int units = (int) Math.min(target, group.value() / gcd);
            for (int j = target; j >= 0; j--) {
                if (cost[j] >= INF) {
                    continue;
                }
                int t = Math.min(target, j + units);
                long newCost = cost[j] + group.cost();
                long newSum = sum[j] + group.value();
                if (newCost < cost[t] || (newCost == cost[t] && newSum < sum[t])) {
                    cost[t] = newCost;
                    sum[t] = newSum;
                    took[i][t / WORD_BITS] |= 1L << (t % WORD_BITS);
                    if (t == target) {
                        capSource[i] = j;
                    }
                }
            }
        }
        int[] taken = new int[groups.size()];
        int t = target;
        for (int i = groups.size() - 1; i >= 0; i--) {
            if ((took[i][t / WORD_BITS] & (1L << (t % WORD_BITS))) != 0) {
                taken[i] = 1;
                int units = (int) Math.min(target, groups.get(i).value() / gcd);
                t = t == target ? capSource[i] : t - units;
            }
        }
        return taken;
    }

    private int[] greedy(List<Group> groups, int targetScore) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingDouble((Integer i) -> (double) groups.get(i).cost() / groups.get(i).value())
                .thenComparing(Comparator.comparingLong((Integer i) -> groups.get(i).value()).reversed()));
        int[] taken = new int[groups.size()];
        long total = 0;
        for (int index : order) {
            if (total >= targetScore) {
                break;
            }
            taken[index] = 1;
            total += groups.get(index).value();
        }
        for (int index = order.size() - 1; index >= 0; index--) {
            int i = order.get(index);
            if (taken[i] == 1 && total - groups.get(i).value() >= targetScore) {
                taken[i] = 0;
                total -= groups.get(i).value();
            }
        }
        return taken;
    }

    private StreamlinedResult result(StreamlinedRequest request, List<Group> groups, int[] taken, boolean coinless, boolean approximate) {
        Map<Integer, Integer> countByEntry = new java.util.LinkedHashMap<>();
        long totalScore = 0;
        long totalCost = 0;
        int items = 0;
        for (int i = 0; i < groups.size(); i++) {
            if (taken[i] == 1) {
                Group group = groups.get(i);
                countByEntry.merge(group.entry(), group.count(), Integer::sum);
                totalScore += group.value();
                totalCost += group.cost();
                items += group.count();
            }
        }
        List<Pick> picks = new ArrayList<>();
        countByEntry.forEach((entryIndex, count) -> {
            PoolEntry entry = request.pool().get(entryIndex);
            int each = scoreOf(entry.rating(), entry.rarity());
            long price = entry.untradeable() || Objects.isNull(entry.priceEach()) ? 0 : entry.priceEach();
            picks.add(new Pick(entry.rating(), entry.rarity(), entry.untradeable(), count, each, entry.untradeable() ? null : entry.priceEach(),
                    (long) each * count, price * count));
        });
        picks.sort(Comparator.comparingInt(Pick::rating).reversed());
        String note = totalScore - request.targetScore() > 0
                ? "Hedefin " + (totalScore - request.targetScore()) + " puan üstünde; gereğinden fazla değer göndermiş olabilirsin." : null;
        return new StreamlinedResult(true, request.targetScore(), totalScore, totalScore - request.targetScore(), totalCost, items,
                coinless, approximate, picks, null, note);
    }

    private List<Group> groups(StreamlinedRequest request) {
        List<Group> groups = new ArrayList<>();
        for (int index = 0; index < request.pool().size(); index++) {
            PoolEntry entry = request.pool().get(index);
            if (Objects.nonNull(request.minRating()) && entry.rating() < request.minRating()) {
                continue;
            }
            int score = scoreOf(entry.rating(), entry.rarity());
            if (score <= 0) {
                continue;
            }
            long price = entry.untradeable() || Objects.isNull(entry.priceEach()) ? 0 : entry.priceEach();
            int remaining = entry.count();
            for (int chunk = 1; remaining > 0; chunk <<= 1) {
                int size = Math.min(chunk, remaining);
                groups.add(new Group(index, size, (long) score * size, price * size));
                remaining -= size;
            }
        }
        return groups;
    }

    private Map<Integer, Integer> specialMap(Rarity rarity) {
        return switch (rarity) {
            case TOTW -> seed.totw();
            case ICON -> seed.icon();
            case HERO -> seed.hero();
            default -> Map.of();
        };
    }

    private boolean explicit(int rating, Rarity rarity) {
        return specialMap(rarity).containsKey(rating);
    }

    private static long gcd(long a, long b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    private record Group(int entry, int count, long value, long cost) {
    }
}
