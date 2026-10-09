package com.fcareer.ut.service;

import java.util.List;

/** Takım rating'i: toplam + ortalamanın üstündeki fazlaların toplamı, yuvarlanıp oyuncu sayısına bölünür ve aşağı yuvarlanır. Kural değerleri seed/ut-rules.fc27.json'dadır. */
public final class SquadRatingCalculator {

    private SquadRatingCalculator() {
    }

    public static int squadRating(List<Integer> ratings) {
        if (ratings.isEmpty()) {
            return 0;
        }
        int sum = ratings.stream().mapToInt(Integer::intValue).sum();
        double average = (double) sum / ratings.size();
        double excess = ratings.stream().mapToDouble(r -> Math.max(0, r - average)).sum();
        return (int) Math.floor(Math.round(sum + excess) / (double) ratings.size());
    }
}
