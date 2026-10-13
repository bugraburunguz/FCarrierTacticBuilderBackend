package com.fcareer.fitengine.service;

import com.fcareer.common.service.MessageService;
import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.level.LevelFitService;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.fitengine.model.WeaponState;
import com.fcareer.fitengine.model.response.RoleFitResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** FitEngineV3'ü mevcut RoleFitResponse biçimine uyarlar: score = currentFit; levelFit, katkılar ve rozet (levelFit eşikleri) eklenir. */
@Service
@RequiredArgsConstructor
public class RoleFitV3Service {

    public static final int TOP_CONTRIBUTIONS = 3;
    private static final double ROUND = 10.0;

    private final FitEngineV3 engine;
    private final LevelFitService levelFitService;
    private final FitEngineProperties properties;
    private final MessageService messageService;

    /** Etiket metni tooltip içindir; renk tek başına anlam taşımaz. Seviyesinde bandı renksizdir (badge null). */
    public enum FitLabel {
        GOOD(WeaponState.GREEN, "nlg.fit.level.good"),
        AT_LEVEL(null, "nlg.fit.level.atLevel"),
        WEAK(WeaponState.YELLOW, "nlg.fit.level.weak"),
        WRONG_ROLE(WeaponState.RED, "nlg.fit.level.wrongRole");

        private final WeaponState badge;
        private final String messageKey;

        FitLabel(WeaponState badge, String messageKey) {
            this.badge = badge;
            this.messageKey = messageKey;
        }

        public WeaponState badge() {
            return badge;
        }

        public String messageKey() {
            return messageKey;
        }
    }

    public static FitLabel label(double basis, FitEngineProperties.V3 v3) {
        if (basis >= v3.getGreenFrom()) {
            return FitLabel.GOOD;
        }
        if (basis >= v3.getNeutralFrom()) {
            return FitLabel.AT_LEVEL;
        }
        return basis >= v3.getYellowFrom() ? FitLabel.WEAK : FitLabel.WRONG_ROLE;
    }

    public RoleFitResponse calculate(FitPlayer player, FitRole role, IntentProfile intent, String slotPosition) {
        String position = resolvePosition(player, role, intent, slotPosition);
        FitEngineV3.Result result = engine.currentFit(player, position, role);
        int gender = Objects.isNull(player.getGender()) ? 0 : player.getGender();
        Optional<LevelFitService.LevelFit> level = Objects.isNull(player.getOverall()) ? Optional.empty()
                : levelFitService.levelFit(gender, position, role.getBaseRole(), role.getFocus(), player.getOverall(), result.currentFit());
        double badgeBasis = level.map(LevelFitService.LevelFit::value).orElse(result.currentFit());
        FitLabel label = label(badgeBasis, properties.getV3());
        return RoleFitResponse.builder()
                .playerId(player.getId())
                .playerName(player.getName())
                .roleId(role.getId())
                .roleName(role.getName())
                .score(round1(result.currentFit()))
                .projectedRating(Objects.isNull(player.getOverall()) ? 0 : player.getOverall())
                .badge(label.badge())
                .badgeText(messageService.getMessage(label.messageKey()))
                .fitLabel(label.name())
                .outOfPosition(!result.inPosition())
                .reasons(List.of())
                .engineVersion(FitEngineV3.VERSION)
                .currentFit(round1(result.currentFit()))
                .levelFit(level.map(LevelFitService.LevelFit::value).orElse(null))
                .levelBandLo(level.map(LevelFitService.LevelFit::bandLo).orElse(null))
                .levelBandHi(level.map(LevelFitService.LevelFit::bandHi).orElse(null))
                .levelSample(level.map(LevelFitService.LevelFit::sampleCount).orElse(null))
                .contributions(result.top(TOP_CONTRIBUTIONS).stream()
                        .map((c) -> new FitEngineV3.Contribution(c.term(), c.key(), Math.round(c.points() * ROUND) / ROUND)).toList())
                .build();
    }

    private static double round1(double v) {
        return Math.round(v * ROUND) / ROUND;
    }

    private static String resolvePosition(FitPlayer player, FitRole role, IntentProfile intent, String slotPosition) {
        if (Objects.nonNull(slotPosition)) {
            return slotPosition;
        }
        if (Objects.nonNull(intent) && Objects.nonNull(intent.getSlotPosition())) {
            return intent.getSlotPosition();
        }
        return player.getPositions().stream().filter(role.getPositions()::contains).findFirst().orElse(role.getPositions().get(0));
    }
}
