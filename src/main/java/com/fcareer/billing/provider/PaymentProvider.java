package com.fcareer.billing.provider;

public interface PaymentProvider {

    String name();

    String createSubscription(Long userId, String plan);
}
