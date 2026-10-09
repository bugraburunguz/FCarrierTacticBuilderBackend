package com.fcareer.billing.provider;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Yalnız geliştirmede: fcareer.billing.stub-enabled=true. Prod'da bean yoktur; abonelik iyzico entegrasyonuna kadar 503 döner. */
@Component
@ConditionalOnProperty(prefix = "fcareer.billing", name = "stub-enabled", havingValue = "true")
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
