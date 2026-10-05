package com.fcareer.career.service;

import com.fcareer.career.entity.CareerEntity;
import com.fcareer.career.entity.RosterEventEntity;
import com.fcareer.career.entity.UserPlayerStateEntity;
import com.fcareer.career.mapper.CareerMapper;
import com.fcareer.career.model.enums.RosterEventType;
import com.fcareer.career.model.request.CreateCareerRequest;
import com.fcareer.career.model.request.RosterEventRequest;
import com.fcareer.career.model.response.CareerPlayerResponse;
import com.fcareer.career.model.response.CareerResponse;
import com.fcareer.career.model.response.RosterEventResponse;
import com.fcareer.career.repository.CareerRepository;
import com.fcareer.career.repository.RosterEventRepository;
import com.fcareer.career.repository.UserPlayerStateRepository;
import com.fcareer.catalog.configuration.CatalogProperties;
import com.fcareer.catalog.entity.ClubEntity;
import com.fcareer.catalog.entity.PlayerRatingEntity;
import com.fcareer.catalog.entity.PlayerValuationEntity;
import com.fcareer.catalog.model.enums.ValuationSource;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CareerService {

    private static final String CAREER = "career";
    private static final String CLUB = "club";
    private static final String GAME_VERSION = "game_version";
    private static final String PLAYER = "player";
    private static final int FIRST_SEASON = 1;
    private static final long NO_FEE = 0L;

    private final CareerRepository careerRepository;
    private final UserPlayerStateRepository userPlayerStateRepository;
    private final RosterEventRepository rosterEventRepository;
    private final ClubRepository clubRepository;
    private final GameVersionRepository gameVersionRepository;
    private final PlayerRatingRepository playerRatingRepository;
    private final PlayerValuationRepository playerValuationRepository;
    private final PlayerService playerService;
    private final CatalogProperties catalogProperties;
    private final CareerMapper careerMapper;
    private final Clock clock;

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
        career.setSeason(Objects.isNull(request.getSeason()) ? FIRST_SEASON : request.getSeason());
        career.setBudgetEur(Objects.isNull(request.getBudgetEur()) ? NO_FEE : request.getBudgetEur());
        career = careerRepository.save(career);

        List<PlayerRatingEntity> ratings = playerRatingRepository.findByClubIdAndGameVersionId(club.getId(), gameVersion);
        for (PlayerRatingEntity rating : ratings) {
            addToSquad(career, rating.getBasePlayerId(), false);
        }
        log.info("Career created careerId : {} userId : {} squadSize : {}", career.getId(), userId, ratings.size());
        return careerMapper.toResponse(career, club.getName(), ratings.size());
    }

    @Transactional(readOnly = true)
    public List<CareerResponse> list(Long userId) {
        return careerRepository.findByUserIdOrderByIdDesc(userId).stream()
                .map(c -> careerMapper.toResponse(c, clubName(c.getClubId()),
                        userPlayerStateRepository.findByCareerIdOrderByIdAsc(c.getId()).size()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CareerResponse get(Long userId, Long careerId) {
        CareerEntity career = owned(userId, careerId);
        return careerMapper.toResponse(career, clubName(career.getClubId()),
                userPlayerStateRepository.findByCareerIdOrderByIdAsc(careerId).size());
    }

    @Transactional(readOnly = true)
    public List<CareerPlayerResponse> squad(Long userId, Long careerId) {
        CareerEntity career = owned(userId, careerId);
        List<UserPlayerStateEntity> states = userPlayerStateRepository.findByCareerIdOrderByIdAsc(careerId);
        Map<Long, PlayerSummaryResponse> summaries = playerService.summaries(
                states.stream().map(UserPlayerStateEntity::getBasePlayerId).toList(), career.getGameVersionId());
        return states.stream()
                .filter(s -> summaries.containsKey(s.getBasePlayerId()))
                .map(s -> careerMapper.toPlayer(s, summaries.get(s.getBasePlayerId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CareerSquad squadPlayerIds(Long userId, Long careerId) {
        CareerEntity career = owned(userId, careerId);
        List<Long> ids = userPlayerStateRepository.findByCareerIdOrderByIdAsc(careerId).stream()
                .map(UserPlayerStateEntity::getBasePlayerId)
                .toList();
        return new CareerSquad(ids, career.getGameVersionId(), career.getBudgetEur());
    }

    public RosterEventResponse applyEvent(Long userId, Long careerId, RosterEventRequest request) {
        CareerEntity career = careerRepository.findByIdForUpdate(careerId)
                .orElseThrow(() -> new NotFoundException(CAREER, careerId));
        assertOwner(career, userId);
        Long playerId = request.getPlayerId();
        boolean inSquad = userPlayerStateRepository.findByCareerIdAndBasePlayerId(careerId, playerId).isPresent();
        long fee = Objects.isNull(request.getFeeEur()) ? NO_FEE : request.getFeeEur();

        switch (request.getType()) {
            case BUY -> {
                requireFee(request);
                requireOutsideSquad(inSquad, playerId);
                spend(career, fee);
                addToSquad(career, requirePlayerInCatalog(career, playerId), false);
            }
            case LOAN_IN -> {
                requireOutsideSquad(inSquad, playerId);
                spend(career, fee);
                addToSquad(career, requirePlayerInCatalog(career, playerId), true);
            }
            case PROMOTE -> {
                requireOutsideSquad(inSquad, playerId);
                addToSquad(career, requirePlayerInCatalog(career, playerId), false);
            }
            case SELL, LOAN_OUT -> {
                requireInSquad(inSquad, playerId);
                career.setBudgetEur(career.getBudgetEur() + fee);
                userPlayerStateRepository.delete(userPlayerStateRepository
                        .findByCareerIdAndBasePlayerId(careerId, playerId).orElseThrow());
            }
        }

        RosterEventEntity event = new RosterEventEntity();
        event.setCareerId(careerId);
        event.setBasePlayerId(playerId);
        event.setType(request.getType());
        event.setFeeEur(Objects.isNull(request.getFeeEur()) ? null : fee);
        event.setFromClubId(request.getFromClubId());
        event.setToClubId(request.getToClubId());
        event = rosterEventRepository.save(event);
        log.info("Roster event applied careerId : {} type : {} playerId : {}", careerId, request.getType(), playerId);
        return careerMapper.toEvent(event, career.getBudgetEur());
    }

    @Transactional(readOnly = true)
    public List<RosterEventResponse> events(Long userId, Long careerId) {
        owned(userId, careerId);
        return rosterEventRepository.findByCareerIdOrderByIdDesc(careerId).stream()
                .map(e -> careerMapper.toEvent(e, null))
                .toList();
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

    private void spend(CareerEntity career, long fee) {
        if (career.getBudgetEur() < fee) {
            log.warn("Insufficient budget careerId : {} budget : {} fee : {}", career.getId(), career.getBudgetEur(), fee);
            throw new InsufficientBudgetException();
        }
        career.setBudgetEur(career.getBudgetEur() - fee);
    }

    private Long requirePlayerInCatalog(CareerEntity career, Long playerId) {
        return playerRatingRepository.findByBasePlayerIdAndGameVersionId(playerId, career.getGameVersionId())
                .map(PlayerRatingEntity::getBasePlayerId)
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

    private String clubName(Integer clubId) {
        return clubRepository.findById(clubId).map(ClubEntity::getName).orElse(null);
    }

    public record CareerSquad(List<Long> playerIds, Short gameVersionId, Long budgetEur) {
    }
}
