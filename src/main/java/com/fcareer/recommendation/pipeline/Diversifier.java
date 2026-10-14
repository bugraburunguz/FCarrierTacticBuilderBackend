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

    public record Candidate(long id, double rank, String club, String league, String ageBand, String archetype, boolean free, boolean young, boolean ready,
                            boolean outOfPosition, boolean prime) {
    }

    private static final double CLUB_WEIGHT = 0.35;
    private static final double LEAGUE_WEIGHT = 0.20;
    private static final double AGE_WEIGHT = 0.20;
    private static final double ARCHETYPE_WEIGHT = 0.25;

    private final RecommendProperties properties;

    public Diversifier(RecommendProperties properties) {
        this.properties = properties;
    }

    /** maxOutOfPosition: listede en fazla bu kadar mevki dışı aday; minPrime: listede en az bu kadar "prime" aday (havuzda varsa). */
    public List<Candidate> select(List<Candidate> pool, int limit, int maxOutOfPosition, int minPrime) {
        List<Candidate> ranked = pool.stream().sorted(Comparator.comparingDouble(Candidate::rank).reversed().thenComparingLong(Candidate::id)).toList();
        List<Candidate> mmrPool = ranked.stream().limit(Math.max(properties.getMmrPool(), limit)).toList();
        List<Candidate> picked = mmr(mmrPool, ranked, limit, maxOutOfPosition);
        guarantee(picked, ranked, Candidate::free, limit, maxOutOfPosition);
        guarantee(picked, ranked, Candidate::young, limit, maxOutOfPosition);
        guarantee(picked, ranked, Candidate::ready, limit, maxOutOfPosition);
        ensurePrime(picked, ranked, limit, maxOutOfPosition, minPrime);
        picked.sort(Comparator.comparingDouble(Candidate::rank).reversed().thenComparingLong(Candidate::id));
        return picked;
    }

    private List<Candidate> mmr(List<Candidate> pool, List<Candidate> all, int limit, int maxOutOfPosition) {
        List<Candidate> picked = new ArrayList<>();
        if (pool.isEmpty()) {
            return picked;
        }
        double max = pool.stream().mapToDouble(Candidate::rank).max().orElse(1);
        double min = pool.stream().mapToDouble(Candidate::rank).min().orElse(0);
        double span = Math.max(max - min, 1e-9);
        // MMR havuzu mevki dışı adaylarla dolsa bile sınırı aşmadan sıralı listenin geri kalanından tamamlanır.
        List<Candidate> rest = new ArrayList<>(pool);
        all.stream().filter((c) -> !pool.contains(c)).forEach(rest::add);
        Candidate first = rest.stream().filter((c) -> !c.outOfPosition() || maxOutOfPosition > 0).findFirst().orElse(null);
        if (Objects.isNull(first)) {
            return picked;
        }
        rest.remove(first);
        picked.add(first);
        double lambda = properties.getMmrLambda();
        while (picked.size() < limit && !rest.isEmpty()) {
            Candidate best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            long oopCount = picked.stream().filter(Candidate::outOfPosition).count();
            for (Candidate candidate : rest) {
                if (candidate.outOfPosition() && oopCount >= maxOutOfPosition) {
                    continue;
                }
                double relevance = (candidate.rank() - min) / span;
                double similarity = picked.stream().mapToDouble((p) -> similarity(candidate, p)).max().orElse(0);
                double score = lambda * relevance - (1 - lambda) * similarity;
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            if (Objects.isNull(best)) {
                break;
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
    private void guarantee(List<Candidate> picked, List<Candidate> ranked, Predicate<Candidate> kind, int limit, int maxOutOfPosition) {
        if (picked.stream().anyMatch(kind)) {
            return;
        }
        long oopNow = picked.stream().filter(Candidate::outOfPosition).count();
        Candidate extra = ranked.stream().filter(kind).filter((c) -> !picked.contains(c)).filter((c) -> !c.outOfPosition() || oopNow < maxOutOfPosition).findFirst().orElse(null);
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

    /** En az minPrime "prime" aday: listede olmayan en iyi prime adaylar, korumalı olmayan en düşük sıralı yerine konur. */
    private void ensurePrime(List<Candidate> picked, List<Candidate> ranked, int limit, int maxOutOfPosition, int minPrime) {
        List<Predicate<Candidate>> kinds = List.of(Candidate::free, Candidate::young, Candidate::ready);
        while (picked.stream().filter(Candidate::prime).count() < minPrime) {
            long oopNow = picked.stream().filter(Candidate::outOfPosition).count();
            Candidate extra = ranked.stream().filter(Candidate::prime).filter((c) -> !picked.contains(c)).filter((c) -> !c.outOfPosition() || oopNow < maxOutOfPosition).findFirst().orElse(null);
            if (Objects.isNull(extra)) {
                return;
            }
            if (picked.size() < limit) {
                picked.add(extra);
                continue;
            }
            Candidate victim = picked.stream().skip(1).filter((c) -> !c.prime())
                    .filter((c) -> kinds.stream().noneMatch((k) -> k.test(c) && picked.stream().filter(k).count() == 1))
                    .min(Comparator.comparingDouble(Candidate::rank)).orElse(null);
            if (Objects.isNull(victim)) {
                return;
            }
            picked.remove(victim);
            picked.add(extra);
        }
    }
}
