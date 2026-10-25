package com.fcareer.catalog.service;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Testlerde Spring'siz yüklenmiş PositionOverallService (load() paket içi @PostConstruct). */
public final class PositionOverallFixture {

    private PositionOverallFixture() {
    }

    public static PositionOverallService loaded(ObjectMapper mapper) {
        PositionOverallService service = new PositionOverallService(mapper);
        service.load();
        return service;
    }
}
