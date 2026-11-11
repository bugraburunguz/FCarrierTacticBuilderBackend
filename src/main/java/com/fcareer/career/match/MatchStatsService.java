package com.fcareer.career.match;

import com.fcareer.career.match.MatchModels.CompetitionSummary;
import com.fcareer.career.match.MatchModels.Leader;
import com.fcareer.career.match.MatchModels.Metric;
import com.fcareer.career.match.MatchModels.PlayerMatches;
import com.fcareer.career.match.MatchModels.SquadForm;
import com.fcareer.career.service.CareerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MatchStatsService {

    private final CareerService careerService;
    private final MatchDataRepository repository;
    private final MatchProperties properties;

    @Transactional(readOnly = true)
    public PlayerMatches player(Long userId, Long careerId, Long playerId) {
        careerService.requireOwned(userId, careerId);
        return new PlayerMatches(repository.ratings(careerId, playerId), repository.seasons(careerId, playerId));
    }

    @Transactional(readOnly = true)
    public List<SquadForm> squadForm(Long userId, Long careerId) {
        CareerService.CareerSquad squad = careerService.squadPlayerIds(userId, careerId);
        return repository.squadForm(careerId, squad.playerIds(), properties.getRecentRatings());
    }

    @Transactional(readOnly = true)
    public List<CompetitionSummary> competitions(Long userId, Long careerId) {
        careerService.requireOwned(userId, careerId);
        return repository.competitions(careerId);
    }

    @Transactional(readOnly = true)
    public List<Leader> leaders(Long userId, Long careerId, Integer competitionId, String metric, Integer limit) {
        Metric parsed = Metric.parse(metric);
        careerService.requireOwned(userId, careerId);
        int size = Objects.isNull(limit) || limit <= 0 ? properties.getLeadersDefaultLimit() : Math.min(limit, properties.getLeadersMaxLimit());
        return repository.leaders(careerId, competitionId, parsed, size, properties.getRatingLeadersMinAppearances());
    }
}
