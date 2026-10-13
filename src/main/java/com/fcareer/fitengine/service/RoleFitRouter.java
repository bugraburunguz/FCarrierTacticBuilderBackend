package com.fcareer.fitengine.service;

import com.fcareer.fitengine.configuration.FitEngineProperties;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.model.IntentProfile;
import com.fcareer.fitengine.model.response.RoleFitResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Kariyer odaklı servislerin (advisor, mevki önerisi) rol uyumunu `fcareer.fit.career-engine-version` bayrağına göre aldığı tek nokta. */
@Service
@RequiredArgsConstructor
public class RoleFitRouter {

    private final RoleFitService roleFitService;
    private final RoleFitV3Service roleFitV3Service;
    private final FitEngineProperties properties;

    public String careerEngineVersion() {
        return properties.getCareerEngineVersion();
    }

    public RoleFitResponse forCareer(FitPlayer player, FitRole role, IntentProfile intent, String slotPosition) {
        return FitEngineV3.VERSION.equals(properties.getCareerEngineVersion())
                ? roleFitV3Service.calculate(player, role, intent, slotPosition)
                : roleFitService.calculate(player, role, intent, slotPosition);
    }
}
