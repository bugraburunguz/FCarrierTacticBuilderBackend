package com.fcareer.catalog.mapper;

import com.fcareer.catalog.entity.BasePlayerEntity;
import com.fcareer.catalog.entity.RatingColumns;
import com.fcareer.catalog.entity.PlayerRatingEntity;
import com.fcareer.catalog.repository.CareerPlayerRepository;
import com.fcareer.catalog.entity.PlayerValuationEntity;
import com.fcareer.catalog.model.response.PlayerDetailResponse;
import com.fcareer.catalog.model.response.PlayerSummaryResponse;
import com.fcareer.catalog.service.PositionOverallService;
import com.fcareer.fitengine.model.AcceleRate;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class PlayerMapper {

    private final RatingAttrAccessor ratingAttrAccessor;
    private final PositionOverallService positionOverallService;
    private final com.fcareer.ingestion.base.LeRoleCodes leRoleCodes;

    /** Katalog öznitelikleri (kariyer kaydında öznitelik yoksa düşülen kaynak). */
    public Map<Attr, Integer> attrs(RatingColumns rating) {
        return ratingAttrAccessor.read(rating);
    }

    public FitPlayer toFitPlayer(BasePlayerEntity base, RatingColumns rating, PlayerValuationEntity valuation, LocalDate gameDate) {
        return FitPlayer.builder()
                .id(base.getId())
                .name(base.getFullName())
                .age(age(base, gameDate))
                .overall(rating.getOverall().intValue())
                .positions(rating.getPositions())
                .attrs(ratingAttrAccessor.read(rating))
                .playstyles(rating.getPlaystyles())
                .acceleRate(Objects.isNull(rating.getAccelerate()) ? null
                        : AcceleRate.fromLabel(rating.getAccelerate()).orElse(null))
                .potential(Objects.isNull(valuation) || Objects.isNull(valuation.getPotential()) ? null
                        : valuation.getPotential().intValue())
                .valueEur(Objects.isNull(valuation) ? null : valuation.getValueEur())
                .gender(Objects.isNull(base.getGender()) ? null : base.getGender().intValue())
                .preferredFoot(base.getPreferredFoot())
                .weakFoot(Objects.isNull(rating.getWeakFoot()) ? null : rating.getWeakFoot().intValue())
                .skillMoves(Objects.isNull(rating.getSkillMoves()) ? null : rating.getSkillMoves().intValue())
                .heightCm(Objects.isNull(base.getHeightCm()) ? null : base.getHeightCm().intValue())
                .build();
    }

    /** Kariyer oyuncusu (career_player) → motor girdisi. Aşinalık LE rol kodlarından rol kimliğine çevrilir; potansiyel kariyer değeri yoksa değerleme tablosundan. */
    public FitPlayer toFitPlayer(BasePlayerEntity base, CareerPlayerRepository.Row row, PlayerValuationEntity valuation, LocalDate gameDate) {
        return FitPlayer.builder()
                .id(base.getId())
                .name(base.getFullName())
                .age(age(base, gameDate))
                .overall(row.ovr())
                .positions(row.positions())
                .attrs(row.attrs())
                .playstyles(row.playstyles())
                .acceleRate(Objects.isNull(row.accelerate()) ? null : AcceleRate.fromLabel(row.accelerate()).orElse(null))
                .potential(Objects.nonNull(valuation) && Objects.nonNull(valuation.getPotential()) ? Integer.valueOf(valuation.getPotential().intValue()) : row.pot())
                .valueEur(Objects.isNull(valuation) ? null : valuation.getValueEur())
                .gender(Objects.isNull(base.getGender()) ? null : base.getGender().intValue())
                .preferredFoot(base.getPreferredFoot())
                .weakFoot(row.weakFoot())
                .skillMoves(row.skillMoves())
                .heightCm(Objects.isNull(base.getHeightCm()) ? null : base.getHeightCm().intValue())
                .roleFamiliarity(leRoleCodes.toRoleIds(row.roleFamiliarity()))
                .build();
    }

    /** Detay ekranı için kariyer satırını bellek içi derece görünümüne çevirir (kalıcı değil). */
    public RatingColumns toRating(CareerPlayerRepository.Row row) {
        RatingColumns rating = new PlayerRatingEntity();
        rating.setOverall((short) row.ovr());
        rating.setPositions(row.positions());
        rating.setAccelerate(row.accelerate());
        rating.setWeakFoot(Objects.isNull(row.weakFoot()) ? null : row.weakFoot().shortValue());
        rating.setSkillMoves(Objects.isNull(row.skillMoves()) ? null : row.skillMoves().shortValue());
        rating.setPlaystyles(row.playstyles());
        rating.setBodyType(row.bodyType());
        ratingAttrAccessor.write(rating, row.attrs());
        return rating;
    }

    public PlayerDetailResponse toDetail(PlayerSummaryResponse summary, BasePlayerEntity base, RatingColumns rating) {
        Map<String, Integer> attrs = new LinkedHashMap<>();
        ratingAttrAccessor.read(rating).forEach((attr, value) -> attrs.put(attr.getKey(), value));
        return PlayerDetailResponse.builder()
                .summary(summary)
                .preferredFoot(base.getPreferredFoot())
                .heightCm(Objects.isNull(base.getHeightCm()) ? null : base.getHeightCm().intValue())
                .weightKg(Objects.isNull(base.getWeightKg()) ? null : base.getWeightKg().intValue())
                .weakFoot(Objects.isNull(rating.getWeakFoot()) ? null : rating.getWeakFoot().intValue())
                .skillMoves(Objects.isNull(rating.getSkillMoves()) ? null : rating.getSkillMoves().intValue())
                .attrs(attrs)
                .playstyles(rating.getPlaystyles())
                .positionOveralls(positionOverallService.byPosition(ratingAttrAccessor.read(rating), rating.getPositions().contains("GK"),
                        rating.getPositions().get(0), rating.getOverall()))
                .build();
    }

    /** Yaş oyun tarihine göre (kariyer), gameDate yoksa bugüne göre (ENG-04). */
    public static Integer age(BasePlayerEntity base, LocalDate gameDate) {
        return Objects.isNull(base.getDob()) ? null : Period.between(base.getDob(), Objects.isNull(gameDate) ? LocalDate.now() : gameDate).getYears();
    }

    public static Attr normalizeAttr(String raw) {
        return Attr.fromKey(raw.replace("_", "")).orElseThrow(() -> new IllegalArgumentException(raw));
    }
}
