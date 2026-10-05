package com.fcareer.fitengine.model;

public enum RuleOperator {
    LT,
    LTE,
    EQ,
    GTE,
    GT;

    public boolean test(double actual, double expected) {
        return switch (this) {
            case LT -> actual < expected;
            case LTE -> actual <= expected;
            case EQ -> Double.compare(actual, expected) == 0;
            case GTE -> actual >= expected;
            case GT -> actual > expected;
        };
    }
}
