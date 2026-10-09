package com.fcareer.common.constants;

public final class ErrorCodes {

    public static final String NOT_FOUND = "fc.exception.0001";
    public static final String INVALID_TACTIC = "fc.exception.0002";
    public static final String TAG_CONFLICT = "fc.exception.0003";
    public static final String INSUFFICIENT_CREDIT = "fc.exception.0004";
    public static final String PREMIUM_REQUIRED = "fc.exception.0005";
    public static final String EMAIL_ALREADY_REGISTERED = "fc.exception.0006";
    public static final String INVALID_CREDENTIALS = "fc.exception.0007";
    public static final String INVALID_REFRESH_TOKEN = "fc.exception.0008";
    public static final String CAREER_ACCESS_DENIED = "fc.exception.0009";
    public static final String INVALID_ROSTER_EVENT = "fc.exception.0010";
    public static final String INSUFFICIENT_BUDGET = "fc.exception.0011";
    public static final String IMPORT_PARSE_ERROR = "fc.exception.0012";
    public static final String RATE_LIMIT_EXCEEDED = "fc.exception.0013";
    public static final String DOWNSTREAM_ERROR = "fc.exception.0014";
    public static final String VALIDATION_ERROR = "fc.exception.0015";
    public static final String UNAUTHORIZED = "fc.exception.0016";
    public static final String FORBIDDEN = "fc.exception.0017";
    public static final String PAYMENT_UNAVAILABLE = "fc.exception.0018";
    public static final String SUBSCRIPTION_ALREADY_ACTIVE = "fc.exception.0019";
    public static final String PAYLOAD_TOO_LARGE = "fc.exception.0020";
    public static final String EXTENSION_OUTDATED = "fc.exception.0021";
    public static final String UNKNOWN_ERROR = "fc.exception.9999";

    private ErrorCodes() {
    }
}
