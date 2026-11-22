package com.fcareer.common.constants;

import java.time.Duration;

public final class ApiConstants {

    public static final String API_V1 = "/api/v1";
    public static final String ADMIN_PATH = API_V1 + "/admin";
    public static final String BEARER_PREFIX = "Bearer ";
    public static final int DEFAULT_PAGE_SIZE = 25;
    public static final int MAX_PAGE_SIZE = 100;
    public static final Duration CATALOG_HTTP_MAX_AGE = Duration.ofMinutes(5);
    public static final String[] CATALOG_CACHED_PATHS = {API_V1 + "/formations", API_V1 + "/presets", API_V1 + "/presets/*", API_V1 + "/roles",
            API_V1 + "/behavior-tags", API_V1 + "/config/key-attributes", API_V1 + "/leagues", API_V1 + "/nationalities"};

    private ApiConstants() {
    }
}
