package com.fcareer.billing.provider;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class StubPaymentProvider implements PaymentProvider {

    private static final String STUB = "STUB";

    @Override
    public String name() {
        return STUB;
    }

    @Override
    public String createSubscription(Long userId, String plan) {
        return STUB + "-" + UUID.randomUUID();
    }
}
