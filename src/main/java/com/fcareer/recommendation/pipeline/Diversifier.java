package com.fcareer.recommendation.pipeline;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * MMR çeşitlendirme: ilgi (rank) ile seçilmişlere benzerlik (kulüp, lig, yaş bandı, arketip) dengelenir. Ardından listede (aday havuzunda varsa) en az
 * 1 serbest, 1 genç ve 1 "hemen hazır" oyuncu garanti edilir; olmayan tür uydurulmaz.
 */
@Component
public class Diversifier {

    public record Candidate(long id, double rank, String club, String league, String ageBand, String archetype, boolean free, boolean young, boolean ready) {
    }

    private static final double CLUB_WEIGHT = 0.35;
    private static final double LEAGUE_WEIGHT = 0.20;
    private static final double AGE_WEIGHT = 0.20;
    private static final double ARCHETYPE_WEIGHT = 0.25;

    private final RecommendProperties properties;

    public Diversifier(RecommendProperties properties) {
        this.properties = properties;
    }

    public List<Candidate> select(List<Candidate> pool, int limit) {
        List<Candidate> ranked = pool.stream().sorted(Comparator.comparingDouble(Candidate::rank).reversed().thenComparingLong(Candidate::id)).toList();
        List<Candidate> mmrPool = ranked.stream().limit(Math.max(properties.getMmrPool(), limit)).toList();
        List<Candidate> picked = mmr(mmrPool, limit);
        guarantee(picked, ranked, Candidate::free, limit);
        guarantee(picked, ranked, Candidate::young, limit);
        guarantee(picked, ranked, Candidate::ready, limit);
        picked.sort(Comparator.comparingDouble(Candidate::rank).reversed().thenComparingLong(Candidate::id));
        return picked;
    }

    private List<Candidate> mmr(List<Candidate> pool, int limit) {
        List<Candidate> picked = new ArrayList<>();
        if (pool.isEmpty()) {
            return picked;
        }
        double max = pool.stream().mapToDouble(Candidate::rank).max().orElse(1);
        double min = pool.stream().mapToDouble(Candidate::rank).min().orElse(0);
        double span = Math.max(max - min, 1e-9);
        List<Candidate> rest = new ArrayList<>(pool);
        picked.add(rest.remove(0));
        double lambda = properties.getMmrLambda();
        while (picked.size() < limit && !rest.isEmpty()) {
            Candidate best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (Candidate candidate : rest) {
                double relevance = (candidate.rank() - min) / span;
                double similarity = picked.stream().mapToDouble((p) -> similarity(candidate, p)).max().orElse(0);
                double score = lambda * relevance - (1 - lambda) * similarity;
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            picked.add(best);
            rest.remove(best);
        }
        return picked;
    }

    private static double similarity(Candidate a, Candidate b) {
        return (same(a.club(), b.club()) ? CLUB_WEIGHT : 0) + (same(a.league(), b.league()) ? LEAGUE_WEIGHT : 0)
                + (same(a.ageBand(), b.ageBand()) ? AGE_WEIGHT : 0) + (same(a.archetype(), b.archetype()) ? ARCHETYPE_WEIGHT : 0);
    }

    private static boolean same(String a, String b) {
        return Objects.nonNull(a) && a.equals(b);
    }

    /** Türü yoksa havuzdaki en iyi örneği, başka bir garantiyi tek başına taşımayan en düşük sıralı yerine koyar (listenin başı korunur). */
    private void guarantee(List<Candidate> picked, List<Candidate> ranked, Predicate<Candidate> kind, int limit) {
        if (picked.stream().anyMatch(kind)) {
            return;
        }
        Candidate extra = ranked.stream().filter(kind).findFirst().orElse(null);
        if (Objects.isNull(extra)) {
            return;
        }
        if (picked.size() < limit) {
            picked.add(extra);
            return;
        }
        List<Predicate<Candidate>> kinds = List.of(Candidate::free, Candidate::young, Candidate::ready);
        Candidate victim = picked.stream().skip(1)
                .filter((c) -> kinds.stream().noneMatch((k) -> k.test(c) && picked.stream().filter(k).count() == 1))
                .min(Comparator.comparingDouble(Candidate::rank)).orElse(null);
        if (Objects.nonNull(victim)) {
            picked.remove(victim);
            picked.add(extra);
        }
    }
}
