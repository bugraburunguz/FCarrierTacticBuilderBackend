package com.fcareer.common.constants;

public final class RedisConstants {

    public static final String REFRESH_TOKEN_PREFIX = "fc:auth:refresh:";
    public static final String RATE_LIMIT_PREFIX = "fc:ratelimit:";
    public static final String CACHE_ROLES = "fc:cache:roles";
    public static final String CACHE_BEHAVIOR_TAGS = "fc:cache:behavior-tags";
    public static final String CACHE_PRESETS = "fc:cache:presets";

    private RedisConstants() {
    }
}
