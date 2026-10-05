package com.fcareer.ingestion.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "fcareer.ingestion", name = "run-on-startup", havingValue = "true")
public class IngestionStartupRunner implements ApplicationRunner {

    private final IngestionService ingestionService;

    @Override
    public void run(ApplicationArguments args) {
        ingestionService.run();
    }
}
