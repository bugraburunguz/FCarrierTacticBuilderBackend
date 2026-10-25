package com.fcareer.advisor.service;

import com.fcareer.advisor.service.SideChoice.Best;
import com.fcareer.advisor.service.SideChoice.RoleAtSides;
import com.fcareer.fitengine.model.FitPlayer;
import com.fcareer.fitengine.model.FitRole;
import com.fcareer.fitengine.service.FitEngineV3;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** FIT-3 madde 13: iki kanatta (ayna mevki doğal sayılır) her rolün FitEngineV3 Güç'ü ve ayak terimi → SideChoice.sideBests. */
@Component
@RequiredArgsConstructor
public class SideScorer {

    private static final String FOOT = "foot";

    private final FitEngineV3 engine;

    public Map<String, Best> sideBests(FitPlayer player, String here, String there, Collection<FitRole> roles) {
        FitPlayer both = SideChoice.bothSides(player, here);
        List<RoleAtSides> atSides = new ArrayList<>();
        for (FitRole role : roles) {
            if (!role.getPositions().contains(here) || !role.getPositions().contains(there)) {
                continue;
            }
            FitEngineV3.Result h = engine.currentFit(both, here, role);
            FitEngineV3.Result t = engine.currentFit(both, there, role);
            atSides.add(new RoleAtSides(role.getName(), h.currentFit(), foot(h), t.currentFit(), foot(t)));
        }
        return SideChoice.sideBests(here, there, atSides);
    }

    private static double foot(FitEngineV3.Result result) {
        return result.contributions().stream().filter((c) -> FOOT.equals(c.term())).mapToDouble(FitEngineV3.Contribution::points).sum();
    }
}
