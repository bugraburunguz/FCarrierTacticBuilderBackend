package com.fcareer.ut.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

public final class SbcVoteModels {

    private SbcVoteModels() {
    }

    public record VoteRequest(@Min(-1) @Max(1) int vote) {
    }

    /** mine: 1 beğendi, -1 beğenmedi, 0 oy yok. */
    public record SetVotes(long setId, long up, long down, int mine) {
    }

    public record VoteSummary(List<SetVotes> sets) {
    }
}
