package com.fcareer.ut.controller;

import com.fcareer.ut.model.UtMetaModels.MetaCard;
import com.fcareer.ut.model.UtMetaModels.MetaFormations;
import com.fcareer.ut.model.UtMetaModels.UpgradeResult;
import com.fcareer.ut.model.UtSquadModels.SquadEvaluateRequest;
import com.fcareer.ut.model.UtSquadModels.SquadEvaluation;
import com.fcareer.ut.service.UtMetaService;
import com.fcareer.ut.service.UtSquadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.fcareer.common.constants.ApiConstants.API_V1;

@RestController
@RequestMapping(API_V1 + "/ut")
@RequiredArgsConstructor
public class UtMetaController {

    private final UtMetaService utMetaService;
    private final UtSquadService utSquadService;

    @GetMapping("/meta/formations")
    public MetaFormations formations() {
        return utMetaService.formations();
    }

    @GetMapping("/meta/tier")
    public List<MetaCard> tier(@RequestParam("pos") String position,
                               @RequestParam(name = "gender", required = false, defaultValue = "0") Short gender,
                               @RequestParam(name = "gv", required = false) Short gameVersionId,
                               @RequestParam(name = "limit", required = false, defaultValue = "20") int limit) {
        return utMetaService.tier(position, gender, gameVersionId, limit);
    }

    @GetMapping("/card/{id}/upgrades")
    public UpgradeResult upgrades(@PathVariable Long id,
                                  @RequestParam(name = "pos", required = false) String position,
                                  @RequestParam(name = "gender", required = false, defaultValue = "0") Short gender,
                                  @RequestParam(name = "gv", required = false) Short gameVersionId,
                                  @RequestParam(name = "limit", required = false, defaultValue = "10") int limit) {
        return utMetaService.upgrades(id, gender, gameVersionId, position, limit);
    }

    @PostMapping("/squad/evaluate")
    public SquadEvaluation evaluate(@Valid @RequestBody SquadEvaluateRequest request) {
        return utSquadService.evaluate(request);
    }

    @PostMapping("/card/{id}/chemstyle")
    public MetaCard chemStyle(@PathVariable Long id,
                              @RequestParam(name = "pos", required = false) String position,
                              @RequestParam(name = "gv", required = false) Short gameVersionId) {
        return utMetaService.chemStyles(id, gameVersionId, position);
    }
}
