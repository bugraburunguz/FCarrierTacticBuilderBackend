package com.fcareer.catalog.mapper;

import com.fcareer.catalog.entity.BasePlayerEntity;
import com.fcareer.catalog.entity.RatingColumns;
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

    public FitPlayer toFitPlayer(BasePlayerEntity base, RatingColumns rating, PlayerValuationEntity valuation) {
        return FitPlayer.builder()
                .id(base.getId())
                .name(base.getFullName())
                .age(age(base))
                .overall(rating.getOverall().intValue())
                .positions(rating.getPositions())
                .attrs(ratingAttrAccessor.read(rating))
                .playstyles(rating.getPlaystyles())
                .acceleRate(Objects.isNull(rating.getAccelerate()) ? null
                        : AcceleRate.fromLabel(rating.getAccelerate()).orElse(null))
                .potential(Objects.isNull(valuation) || Objects.isNull(valuation.getPotential()) ? null
                        : valuation.getPotential().intValue())
                .valueEur(Objects.isNull(valuation) ? null : valuation.getValueEur())
                .build();
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

    public static Integer age(BasePlayerEntity base) {
        return Objects.isNull(base.getDob()) ? null : Period.between(base.getDob(), LocalDate.now()).getYears();
    }

    public static Attr normalizeAttr(String raw) {
        return Attr.fromKey(raw.replace("_", "")).orElseThrow(() -> new IllegalArgumentException(raw));
    }
}
