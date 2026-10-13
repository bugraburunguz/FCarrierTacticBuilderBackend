package com.fcareer.fitengine.level;

import com.fcareer.catalog.configuration.CatalogProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Yerel/ilk kurulumda fit_level_norms'u açılışta üretir: `fcareer.fit.level-norms.rebuild-on-start=true`. Varsayılan kapalı. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fcareer.fit.level-norms.rebuild-on-start", havingValue = "true")
public class FitLevelNormsStartupRunner implements ApplicationRunner {

    private final FitLevelNormsBuilder builder;
    private final LevelFitService levelFitService;
    private final CatalogProperties catalogProperties;

    @Override
    public void run(ApplicationArguments args) {
        builder.rebuild(catalogProperties.getDefaultGameVersion());
        levelFitService.refresh();
    }
}
