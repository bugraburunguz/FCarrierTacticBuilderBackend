package com.fcareer.career.service;

import com.fcareer.career.entity.CareerEntity;
import com.fcareer.career.entity.RosterEventEntity;
import com.fcareer.career.entity.UserPlayerStateEntity;
import com.fcareer.career.mapper.CareerMapper;
import com.fcareer.career.model.enums.RosterEventType;
import com.fcareer.career.model.request.CreateCareerRequest;
import com.fcareer.career.model.request.RosterEventRequest;
import com.fcareer.career.model.request.UpdateCareerRequest;
import com.fcareer.career.model.response.CareerPlayerResponse;
import com.fcareer.career.model.response.CareerResponse;
import com.fcareer.career.model.response.RosterEventResponse;
import com.fcareer.career.repository.CareerRepository;
import com.fcareer.career.repository.RosterEventRepository;
import com.fcareer.career.repository.UserPlayerStateRepository;
import com.fcareer.catalog.configuration.CatalogProperties;
import com.fcareer.catalog.entity.BasePlayerEntity;
import com.fcareer.catalog.entity.ClubEntity;
import com.fcareer.catalog.entity.PlayerRatingEntity;
import com.fcareer.catalog.entity.PlayerValuationEntity;
import com.fcareer.catalog.model.enums.ValuationSource;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.catalog.repository.BasePlayerRepository;
import com.fcareer.catalog.repository.ClubRepository;
import com.fcareer.catalog.repository.GameVersionRepository;
import com.fcareer.catalog.repository.PlayerRatingRepository;
import com.fcareer.catalog.repository.PlayerValuationRepository;
import com.fcareer.catalog.service.PlayerService;
import com.fcareer.common.exception.CareerAccessDeniedException;
import com.fcareer.common.exception.InsufficientBudgetException;
import com.fcareer.common.exception.InvalidRosterEventException;
import com.fcareer.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CareerService {

    private static final String CAREER = "career";
    private static final String CLUB = "club";
    private static final String EVENT = "roster_event";
    private static final String GAME_VERSION = "game_version";
    private static final String PLAYER = "player";
    private static final int FIRST_SEASON = 1;
    private static final long NO_FEE = 0L;

    private final CareerRepository careerRepository;
    private final UserPlayerStateRepository userPlayerStateRepository;
    private final RosterEventRepository rosterEventRepository;
    private final ClubRepository clubRepository;
    private final BasePlayerRepository basePlayerRepository;
    private final GameVersionRepository gameVersionRepository;
    private final PlayerRatingRepository playerRatingRepository;
    private final PlayerValuationRepository playerValuationRepository;
    private final PlayerService playerService;
    private final CatalogProperties catalogProperties;
    private final CareerMapper careerMapper;
    private final Clock clock;
    private final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc;

    public CareerResponse create(Long userId, CreateCareerRequest request) {
        short gameVersion = Objects.isNull(request.getGameVersionId())
                ? catalogProperties.getDefaultGameVersion() : request.getGameVersionId();
        if (!gameVersionRepository.existsById(gameVersion)) {
            throw new NotFoundException(GAME_VERSION, gameVersion);
        }
        ClubEntity club = clubRepository.findById(request.getClubId())
                .orElseThrow(() -> new NotFoundException(CLUB, request.getClubId()));

        CareerEntity career = new CareerEntity();
        career.setUserId(userId);
        career.setGameVersionId(gameVersion);
        career.setClubId(club.getId());
        career.setName(cleanName(request.getName()));
        career.setSeason(Objects.isNull(request.getSeason()) ? FIRST_SEASON : request.getSeason());
        career.setBudgetEur(Objects.isNull(request.getBudgetEur()) ? NO_FEE : request.getBudgetEur());
        career = careerRepository.save(career);

        List<PlayerRatingEntity> ratings = playerRatingRepository.findByClubIdAndGameVersionId(club.getId(), gameVersion);
        for (PlayerRatingEntity rating : ratings) {
            addToSquad(career, rating.getBasePlayerId(), false);
        }
        log.info("Career created careerId : {} userId : {} squadSize : {}", career.getId(), userId, ratings.size());
        return careerMapper.toResponse(career, club.getName(), club.getGender(), ratings.size());
    }

    public CareerResponse update(Long userId, Long careerId, UpdateCareerRequest request) {
        CareerEntity career = careerRepository.findByIdForUpdate(careerId)
                .orElseThrow(() -> new NotFoundException(CAREER, careerId));
        assertOwner(career, userId);
        if (Objects.nonNull(request.getName())) {
            career.setName(cleanName(request.getName()));
        }
        if (Objects.nonNull(request.getBudgetEur())) {
            career.setBudgetEur(request.getBudgetEur());
        }
        if (Objects.nonNull(request.getSeason())) {
            career.setSeason(request.getSeason());
        }
        log.info("Career updated careerId : {}", careerId);
        return toResponse(career);
    }

    public void delete(Long userId, Long careerId) {
        CareerEntity career = careerRepository.findByIdForUpdate(careerId)
                .orElseThrow(() -> new NotFoundException(CAREER, careerId));
        assertOwner(career, userId);
        rosterEventRepository.deleteByCareerId(careerId);
        userPlayerStateRepository.deleteByCareerId(careerId);
        rosterEventRepository.flush();
        userPlayerStateRepository.flush();
        deleteGeneratedPlayers(careerId);
        careerRepository.deleteSnapshots(careerId);
        careerRepository.delete(career);
        log.info("Career deleted careerId : {} userId : {}", careerId, userId);
    }

    @Transactional(readOnly = true)
    public List<CareerResponse> list(Long userId) {
        return careerRepository.findByUserIdOrderByIdDesc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CareerResponse get(Long userId, Long careerId) {
        return toResponse(owned(userId, careerId));
    }

    @Transactional(readOnly = true)
    public List<CareerPlayerResponse> squad(Long userId, Long careerId) {
        CareerEntity career = owned(userId, careerId);
        List<UserPlayerStateEntity> states = userPlayerStateRepository.findByCareerIdOrderByIdAsc(careerId);
        Map<Long, PlayerSummaryResponse> summaries = playerService.summaries(
                states.stream().map(UserPlayerStateEntity::getBasePlayerId).toList(), career.getGameVersionId());
        Map<Long, int[]> overlay = importedRatings(careerId, states);
        return states.stream()
                .filter(s -> summaries.containsKey(s.getBasePlayerId()))
                .map(s -> {
                    PlayerSummaryResponse summary = summaries.get(s.getBasePlayerId());
                    int[] imported = overlay.get(s.getBasePlayerId());
                    if (Objects.nonNull(imported)) {
                        summary.setOverall(imported[0]);
                        summary.setPotential(imported[1]);
                        summary.setValuationEstimated(false);
                    }
                    return careerMapper.toPlayer(s, summary);
                })
                .toList();
    }

    private Map<Long, int[]> importedRatings(Long careerId, List<UserPlayerStateEntity> states) {
        Map<Long, int[]> overlay = new java.util.HashMap<>();
        if (states.isEmpty()) {
            return overlay;
        }
        jdbc.query("SELECT base_player_id, overall, potential FROM career_player_data WHERE career_id = :c AND base_player_id IN (:ids)",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                        .addValue("c", careerId)
                        .addValue("ids", states.stream().map(UserPlayerStateEntity::getBasePlayerId).toList()),
                rs -> {
                    overlay.put(rs.getLong("base_player_id"), new int[]{rs.getInt("overall"), rs.getInt("potential")});
                });
        return overlay;
    }

    @Transactional(readOnly = true)
    public CareerSquad squadPlayerIds(Long userId, Long careerId) {
        CareerEntity career = owned(userId, careerId);
        List<Long> ids = userPlayerStateRepository.findByCareerIdOrderByIdAsc(careerId).stream()
                .map(UserPlayerStateEntity::getBasePlayerId)
                .toList();
        return new CareerSquad(ids, career.getGameVersionId(), career.getBudgetEur(), clubGender(career.getClubId()), career.getClubId());
    }

    public RosterEventResponse applyEvent(Long userId, Long careerId, RosterEventRequest request) {
        CareerEntity career = careerRepository.findByIdForUpdate(careerId)
                .orElseThrow(() -> new NotFoundException(CAREER, careerId));
        assertOwner(career, userId);
        Long playerId = request.getPlayerId();
        boolean inSquad = userPlayerStateRepository.findByCareerIdAndBasePlayerId(careerId, playerId).isPresent();
        long fee = Objects.isNull(request.getFeeEur()) ? NO_FEE : request.getFeeEur();
        Integer fromClubId = request.getFromClubId();
        Integer toClubId = request.getToClubId();

        switch (request.getType()) {
            case BUY, LOAN_IN -> {
                if (request.getType() == RosterEventType.BUY) {
                    requireFee(request);
                }
                requireOutsideSquad(inSquad, playerId);
                PlayerRatingEntity rating = requirePlayerInCatalog(career, playerId);
                requireSameGender(career, playerId);
                spend(career, fee);
                addToSquad(career, playerId, request.getType() == RosterEventType.LOAN_IN);
                fromClubId = Objects.isNull(fromClubId) ? rating.getClubId() : fromClubId;
                toClubId = career.getClubId();
            }
            case PROMOTE -> {
                requireOutsideSquad(inSquad, playerId);
                requirePlayerInCatalog(career, playerId);
                requireSameGender(career, playerId);
                addToSquad(career, playerId, false);
                toClubId = career.getClubId();
            }
            case SELL, LOAN_OUT -> {
                requireInSquad(inSquad, playerId);
                career.setBudgetEur(career.getBudgetEur() + fee);
                userPlayerStateRepository.delete(userPlayerStateRepository
                        .findByCareerIdAndBasePlayerId(careerId, playerId).orElseThrow());
                fromClubId = career.getClubId();
            }
        }

        RosterEventEntity event = new RosterEventEntity();
        event.setCareerId(careerId);
        event.setBasePlayerId(playerId);
        event.setType(request.getType());
        event.setFeeEur(Objects.isNull(request.getFeeEur()) ? null : fee);
        event.setFromClubId(fromClubId);
        event.setToClubId(toClubId);
        event = rosterEventRepository.save(event);
        log.info("Roster event applied careerId : {} type : {} playerId : {}", careerId, request.getType(), playerId);
        return toEventResponses(List.of(event), career.getBudgetEur()).get(0);
    }

    public RosterEventResponse undoLastEvent(Long userId, Long careerId, Long eventId) {
        CareerEntity career = careerRepository.findByIdForUpdate(careerId)
                .orElseThrow(() -> new NotFoundException(CAREER, careerId));
        assertOwner(career, userId);
        RosterEventEntity latest = rosterEventRepository.findByCareerIdOrderByIdDesc(careerId).stream().findFirst()
                .orElseThrow(() -> new NotFoundException(EVENT, eventId));
        if (!Objects.equals(latest.getId(), eventId)) {
            throw new InvalidRosterEventException("only the latest event can be undone");
        }
        long fee = Objects.isNull(latest.getFeeEur()) ? NO_FEE : latest.getFeeEur();
        Long playerId = latest.getBasePlayerId();
        switch (latest.getType()) {
            case BUY, LOAN_IN -> {
                userPlayerStateRepository.findByCareerIdAndBasePlayerId(careerId, playerId)
                        .ifPresent(userPlayerStateRepository::delete);
                career.setBudgetEur(career.getBudgetEur() + fee);
            }
            case PROMOTE -> userPlayerStateRepository.findByCareerIdAndBasePlayerId(careerId, playerId)
                    .ifPresent(userPlayerStateRepository::delete);
            case SELL, LOAN_OUT -> {
                requireOutsideSquad(userPlayerStateRepository.findByCareerIdAndBasePlayerId(careerId, playerId).isPresent(), playerId);
                if (career.getBudgetEur() < fee) {
                    throw new InsufficientBudgetException();
                }
                career.setBudgetEur(career.getBudgetEur() - fee);
                addToSquad(career, playerId, false);
            }
        }
        RosterEventResponse response = toEventResponses(List.of(latest), career.getBudgetEur()).get(0);
        rosterEventRepository.delete(latest);
        log.info("Roster event undone careerId : {} eventId : {}", careerId, eventId);
        return response;
    }

    @Transactional(readOnly = true)
    public List<RosterEventResponse> events(Long userId, Long careerId) {
        owned(userId, careerId);
        return toEventResponses(rosterEventRepository.findByCareerIdOrderByIdDesc(careerId), null);
    }

    private List<RosterEventResponse> toEventResponses(List<RosterEventEntity> events, Long budgetAfter) {
        Set<Long> playerIds = events.stream().map(RosterEventEntity::getBasePlayerId).collect(Collectors.toSet());
        Set<Integer> clubIds = new HashSet<>();
        events.forEach(e -> {
            if (Objects.nonNull(e.getFromClubId())) {
                clubIds.add(e.getFromClubId());
            }
            if (Objects.nonNull(e.getToClubId())) {
                clubIds.add(e.getToClubId());
            }
        });
        Map<Long, String> playerNames = basePlayerRepository.findAllById(playerIds).stream()
                .collect(Collectors.toMap(BasePlayerEntity::getId, BasePlayerEntity::getFullName));
        Map<Integer, String> clubNames = clubRepository.findAllById(clubIds).stream()
                .collect(Collectors.toMap(ClubEntity::getId, ClubEntity::getName));
        return events.stream().map(e -> careerMapper.toEvent(e, budgetAfter, playerNames, clubNames)).toList();
    }

    private void requireFee(RosterEventRequest request) {
        if (Objects.isNull(request.getFeeEur())) {
            throw new InvalidRosterEventException("feeEur is required for " + RosterEventType.BUY);
        }
    }

    private void requireOutsideSquad(boolean inSquad, Long playerId) {
        if (inSquad) {
            log.warn("Player already in squad playerId : {}", playerId);
            throw new InvalidRosterEventException("player already in squad");
        }
    }

    private void requireInSquad(boolean inSquad, Long playerId) {
        if (!inSquad) {
            log.warn("Player not in squad playerId : {}", playerId);
            throw new InvalidRosterEventException("player not in squad");
        }
    }

    private void requireSameGender(CareerEntity career, Long playerId) {
        Short clubGender = clubGender(career.getClubId());
        BasePlayerEntity player = basePlayerRepository.findById(playerId)
                .orElseThrow(() -> new NotFoundException(PLAYER, playerId));
        if (Objects.nonNull(clubGender) && !Objects.equals(clubGender, player.getGender())) {
            log.warn("Gender mismatch careerId : {} playerId : {}", career.getId(), playerId);
            throw new InvalidRosterEventException("player gender does not match the club");
        }
    }

    private void spend(CareerEntity career, long fee) {
        if (career.getBudgetEur() < fee) {
            log.warn("Insufficient budget careerId : {} budget : {} fee : {}", career.getId(), career.getBudgetEur(), fee);
            throw new InsufficientBudgetException();
        }
        career.setBudgetEur(career.getBudgetEur() - fee);
    }

    private PlayerRatingEntity requirePlayerInCatalog(CareerEntity career, Long playerId) {
        return playerRatingRepository.findByBasePlayerIdAndGameVersionId(playerId, career.getGameVersionId())
                .orElseThrow(() -> new NotFoundException(PLAYER, playerId));
    }

    private void addToSquad(CareerEntity career, Long playerId, boolean onLoan) {
        PlayerValuationEntity valuation = playerValuationRepository
                .findByBasePlayerIdAndGameVersionIdAndSourceAndOwnerUserIdIsNull(
                        playerId, career.getGameVersionId(), ValuationSource.MODELED)
                .orElse(null);
        Instant now = clock.instant();
        UserPlayerStateEntity state = new UserPlayerStateEntity();
        state.setCareerId(career.getId());
        state.setBasePlayerId(playerId);
        state.setClubId(career.getClubId());
        state.setOnLoan(onLoan);
        state.setAcquiredAt(now);
        state.setUpdatedAt(now);
        if (Objects.nonNull(valuation)) {
            state.setDynamicPotential(valuation.getPotential());
            state.setValueEur(valuation.getValueEur());
        }
        userPlayerStateRepository.save(state);
    }

    private void deleteGeneratedPlayers(Long careerId) {
        java.util.Map<String, Object> params = java.util.Map.of("c", careerId);
        jdbc.update("DELETE FROM career_player_data WHERE career_id = :c", params);
        jdbc.update("DELETE FROM player_valuation WHERE base_player_id IN (SELECT id FROM base_player WHERE owner_career_id = :c)", params);
        jdbc.update("DELETE FROM player_rating WHERE base_player_id IN (SELECT id FROM base_player WHERE owner_career_id = :c)", params);
        jdbc.update("DELETE FROM base_player WHERE owner_career_id = :c", params);
    }

    public CareerEntity requireOwned(Long userId, Long careerId) {
        return owned(userId, careerId);
    }

    private CareerEntity owned(Long userId, Long careerId) {
        CareerEntity career = careerRepository.findById(careerId).orElseThrow(() -> new NotFoundException(CAREER, careerId));
        assertOwner(career, userId);
        return career;
    }

    private void assertOwner(CareerEntity career, Long userId) {
        if (!Objects.equals(career.getUserId(), userId)) {
            log.warn("Career access denied careerId : {} userId : {}", career.getId(), userId);
            throw new CareerAccessDeniedException();
        }
    }

    private CareerResponse toResponse(CareerEntity career) {
        ClubEntity club = clubRepository.findById(career.getClubId()).orElse(null);
        return careerMapper.toResponse(career, Objects.isNull(club) ? null : club.getName(),
                Objects.isNull(club) ? null : club.getGender(),
                userPlayerStateRepository.findByCareerIdOrderByIdAsc(career.getId()).size());
    }

    private Short clubGender(Integer clubId) {
        return clubRepository.findById(clubId).map(ClubEntity::getGender).orElse(null);
    }

    private static String cleanName(String name) {
        return Objects.isNull(name) || name.isBlank() ? null : name.trim();
    }

    public record CareerSquad(List<Long> playerIds, Short gameVersionId, Long budgetEur, Short gender, Integer clubId) {
    }
}
