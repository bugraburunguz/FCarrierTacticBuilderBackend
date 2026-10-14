package com.fcareer.recommendation.pipeline;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** E3-6 çeşitlendirme: kulüp / lig / yaş bandı / arketip üzerinde MMR; listede (varsa) en az 1 serbest, 1 genç (≤21), 1 "hemen hazır". */
class DiversifierTest {

    private final Diversifier diversifier = new Diversifier(new RecommendProperties());

    private static Diversifier.Candidate c(long id, double rank, String club, String league, int age, String archetype, boolean free, boolean ready) {
        return new Diversifier.Candidate(id, rank, club, league, age <= 21 ? "U21" : age <= 28 ? "22-28" : "29+", archetype, free, age <= 21, ready, false, age <= 27);
    }

    @Test
    void sameClubDuplicatesAreSpreadOutWhenAlternativesExist() {
        List<Diversifier.Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            pool.add(c(i, 90 - i, "A FC", "L1", 25, "poacher", false, false));
        }
        pool.add(c(10, 87.5, "B FC", "L2", 25, "poacher", false, false));
        pool.add(c(11, 87.4, "C FC", "L3", 25, "poacher", false, false));
        List<Diversifier.Candidate> picked = diversifier.select(pool, 4, 10, 0);
        assertThat(picked).extracting(Diversifier.Candidate::club).contains("B FC", "C FC");
        assertThat(picked.stream().filter((x) -> x.club().equals("A FC")).count()).isLessThan(4);
    }

    @Test
    void theTopCandidateIsAlwaysPickedFirst() {
        List<Diversifier.Candidate> pool = List.of(c(1, 70, "A", "L1", 25, "x", false, false), c(2, 95, "B", "L2", 25, "y", false, false), c(3, 60, "C", "L3", 25, "z", false, false));
        assertThat(diversifier.select(pool, 2, 10, 0).get(0).id()).isEqualTo(2);
    }

    @Test
    void guaranteesOneFreeOneYoungAndOneReadyWhenTheyExist() {
        List<Diversifier.Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            pool.add(c(i, 95 - i, "Club" + i, "L" + i, 27, "a" + (i % 3), false, false));
        }
        pool.add(c(20, 40, "FreeFC", "FA", 30, "b", true, false));
        pool.add(c(21, 41, "YoungFC", "L9", 19, "c", false, false));
        pool.add(c(22, 42, "ReadyFC", "L8", 26, "d", false, true));
        List<Diversifier.Candidate> picked = diversifier.select(pool, 5, 10, 0);
        assertThat(picked).hasSize(5);
        assertThat(picked).anyMatch(Diversifier.Candidate::free);
        assertThat(picked).anyMatch(Diversifier.Candidate::young);
        assertThat(picked).anyMatch(Diversifier.Candidate::ready);
        assertThat(picked.get(0).id()).as("kota koruması listenin başını bozmaz").isEqualTo(0);
    }

    @Test
    void aMissingKindIsNotInventedWhenNoCandidateHasIt() {
        List<Diversifier.Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            pool.add(c(i, 90 - i, "Club" + i, "L" + i, 27, "a", false, false));
        }
        List<Diversifier.Candidate> picked = diversifier.select(pool, 3, 10, 0);
        assertThat(picked).hasSize(3).noneMatch(Diversifier.Candidate::free).noneMatch(Diversifier.Candidate::young);
    }

    @Test
    void aCandidateThatCoversSeveralGuaranteesIsEnough() {
        List<Diversifier.Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            pool.add(c(i, 90 - i, "Club" + i, "L" + i, 27, "a", false, false));
        }
        pool.add(c(30, 30, "YoungFree", "FA", 19, "b", true, true));
        List<Diversifier.Candidate> picked = diversifier.select(pool, 3, 10, 0);
        assertThat(picked).extracting(Diversifier.Candidate::id).contains(30L);
    }

    private static Diversifier.Candidate oop(long id, double rank, String club) {
        return new Diversifier.Candidate(id, rank, club, "L" + id, "22-28", "a" + id, false, false, false, true, true);
    }

    @Test
    void outOfPositionCandidatesAreCappedEvenWhenTheyRankHighest() {
        List<Diversifier.Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            pool.add(oop(100 + i, 95 - i, "Oop" + i));
        }
        for (int i = 0; i < 8; i++) {
            pool.add(c(i, 80 - i, "Nat" + i, "N" + i, 26, "n" + i, false, false));
        }
        List<Diversifier.Candidate> picked = diversifier.select(pool, 10, 2, 0);
        assertThat(picked).hasSize(10);
        assertThat(picked.stream().filter(Diversifier.Candidate::outOfPosition).count()).isLessThanOrEqualTo(2);
    }

    @Test
    void aMinimumNumberOfPrimeAgePlayersIsEnforcedWhenThePoolHasThem() {
        List<Diversifier.Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            pool.add(c(i, 95 - i, "Old" + i, "O" + i, 33, "o" + i, false, false));
        }
        for (int i = 0; i < 6; i++) {
            pool.add(c(20 + i, 70 - i, "Prime" + i, "P" + i, 25, "p" + i, false, false));
        }
        List<Diversifier.Candidate> picked = diversifier.select(pool, 10, 2, 4);
        assertThat(picked.stream().filter(Diversifier.Candidate::prime).count()).isGreaterThanOrEqualTo(4);
        assertThat(picked.get(0).id()).as("liste başı korunur").isEqualTo(0);
    }

    @Test
    void theMinimumPrimeCountIsNotInventedWhenThePoolLacksPrimePlayers() {
        List<Diversifier.Candidate> pool = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            pool.add(c(i, 90 - i, "Old" + i, "O" + i, 33, "o", false, false));
        }
        assertThat(diversifier.select(pool, 4, 2, 4)).hasSize(4);
    }
}
