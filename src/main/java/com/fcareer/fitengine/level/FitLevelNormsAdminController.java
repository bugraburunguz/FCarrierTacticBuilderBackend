package com.fcareer.fitengine.level;

import com.fcareer.catalog.configuration.CatalogProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.fcareer.common.constants.ApiConstants.ADMIN_PATH;

@RestController
@RequestMapping(ADMIN_PATH + "/fit")
@RequiredArgsConstructor
public class FitLevelNormsAdminController {

    private final FitLevelNormsBuilder builder;
    private final LevelFitService levelFitService;
    private final CatalogProperties catalogProperties;

    @PostMapping("/level-norms")
    public int rebuild(@RequestParam(name = "gv", required = false) Short gameVersionId) {
        int rows = builder.rebuild(gameVersionId == null ? catalogProperties.getDefaultGameVersion() : gameVersionId);
        levelFitService.refresh();
        return rows;
    }
}
