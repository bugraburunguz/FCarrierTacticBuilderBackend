package com.fcareer.catalog.service;

import com.fcareer.catalog.entity.ClubEntity;
import com.fcareer.catalog.entity.LeagueEntity;
import com.fcareer.catalog.model.response.ClubResponse;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.catalog.repository.LeagueRepository;
import lombok.RequiredArgsConstructor;
import com.fcareer.ingestion.base.BaseCatalogImporter;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.fcareer.common.constants.ApiConstants.MAX_PAGE_SIZE;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClubService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int ANY_LEAGUE = 0;
    private static final int ANY_GENDER = -1;

    private final ClubRepository clubRepository;
    private final LeagueRepository leagueRepository;

    public List<ClubResponse> search(String query, Integer limit, Integer leagueId, Integer gender) {
        int size = Objects.isNull(limit) || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_PAGE_SIZE);
        String term = Objects.isNull(query) ? "" : query.trim();
        List<ClubEntity> clubs = clubRepository.searchWithPlayers(term, Objects.isNull(leagueId) ? ANY_LEAGUE : leagueId,
                Objects.isNull(gender) ? ANY_GENDER : gender, PageRequest.of(0, size));
        Map<Integer, String> leagues = leagueRepository.findAllById(
                        clubs.stream().map(ClubEntity::getLeagueId).filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(LeagueEntity::getId, (l) -> BaseCatalogImporter.displayLeagueName(l.getName(), l.getEaLeagueId()), (a, b) -> a));
        return clubs.stream()
                .map(c -> ClubResponse.builder()
                        .id(c.getId())
                        .name(c.getName())
                        .leagueId(c.getLeagueId())
                        .league(leagues.get(c.getLeagueId()))
                        .gender(Objects.isNull(c.getGender()) ? null : c.getGender().intValue())
                        .build())
                .toList();
    }
}
