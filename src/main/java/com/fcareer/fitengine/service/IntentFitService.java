package com.fcareer.fitengine.service;

import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.model.Attr;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitTag;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.fitengine.model.WeaponState;
import com.fcareer.fitengine.model.response.IntentFitResponse;
import com.fcareer.fitengine.model.response.TagCheck;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.fcareer.fitengine.service.RoleFitService.norm;

@Service
@RequiredArgsConstructor
public class IntentFitService {

    private static final int FULL_SCORE = 100;

    private final FitEngineProperties properties;
    private final NlgService nlgService;

    public IntentFitResponse calculate(FitPlayer player, IntentProfile intent) {
        if (!intent.hasTags()) {
            return IntentFitResponse.builder()
                    .playerId(player.getId())
                    .score(FULL_SCORE)
                    .perTag(List.of())
                    .build();
        }
        List<TagCheck> perTag = new ArrayList<>();
        double total = 0;
        for (FitTag tag : intent.getTags()) {
            TagCheck check = checkTag(player, tag);
            perTag.add(check);
            total += check.getRatio();
        }
        int score = (int) Math.round(FULL_SCORE * total / intent.getTags().size());
        return IntentFitResponse.builder()
                .playerId(player.getId())
                .score(score)
                .perTag(perTag)
                .build();
    }

    private TagCheck checkTag(FitPlayer player, FitTag tag) {
        Attr weapon = tag.primaryWeapon();
        int value = player.attr(weapon);
        double ratio = Math.max(0, Math.min(1, norm(value) / norm(tag.getExpectedMin())));
        WeaponState state = stateOf(ratio);
        return TagCheck.builder()
                .tagId(tag.getId())
                .label(tag.getLabelTr())
                .attr(weapon.getKey())
                .value(value)
                .expected(tag.getExpectedMin())
                .ratio(Math.round(ratio * 1000.0) / 1000.0)
                .ok(ratio >= properties.getIntent().getOkRatio())
                .state(state)
                .note(nlgService.tagNote(tag, value, tag.getExpectedMin(), state))
                .build();
    }

    private WeaponState stateOf(double ratio) {
        if (ratio >= properties.getIntent().getOkRatio()) {
            return WeaponState.GREEN;
        }
        if (ratio >= properties.getIntent().getWeakRatio()) {
            return WeaponState.YELLOW;
        }
        return WeaponState.RED;
    }
}
