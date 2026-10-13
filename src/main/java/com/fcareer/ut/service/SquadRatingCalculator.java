package com.fcareer.ut.service;

import java.util.Comparator;
import java.util.List;

/** Takım rating'i: toplam + ortalamanın üstündeki fazlaların toplamı, yuvarlanıp oyuncu sayısına bölünür ve aşağı yuvarlanır. Kural değerleri seed/ut-rules.fc27.json'dadır. */
public final class SquadRatingCalculator {

    private SquadRatingCalculator() {
    }

    /** En iyi {@code size} oyuncu üzerinden: kadro ekranı 18 (XI + 7 yedek), SBC 11 (UtRules.squadSize / sbcSize). */
    public static int squadRating(List<Integer> ratings, int size) {
        List<Integer> top = ratings.stream().sorted(Comparator.reverseOrder()).limit(size).toList();
        if (top.isEmpty()) {
            return 0;
        }
        int sum = top.stream().mapToInt(Integer::intValue).sum();
        double average = (double) sum / top.size();
        double excess = top.stream().mapToDouble(r -> Math.max(0, r - average)).sum();
        return (int) Math.floor(Math.round(sum + excess) / (double) top.size());
    }
}
