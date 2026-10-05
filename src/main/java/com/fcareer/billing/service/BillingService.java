package com.fcareer.billing.service;

import com.fcareer.auth.entity.AppUserEntity;
import com.fcareer.auth.model.SubscriptionType;
import com.fcareer.auth.repository.AppUserRepository;
import com.fcareer.billing.configuration.BillingProperties;
import com.fcareer.billing.entity.SubscriptionEntity;
import com.fcareer.billing.model.SubscriptionStatus;
import com.fcareer.billing.model.response.SubscriptionResponse;
import com.fcareer.billing.provider.PaymentProvider;
import com.fcareer.billing.repository.SubscriptionRepository;
import com.fcareer.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private static final String USER = "user";

    private final AppUserRepository appUserRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentProvider paymentProvider;
    private final BillingProperties properties;
    private final Clock clock;

    @Transactional
    public SubscriptionResponse subscribe(Long userId) {
        AppUserEntity user = appUserRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException(USER, userId));
        Instant now = clock.instant();
        String externalId = paymentProvider.createSubscription(userId, properties.getPlan());

        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setUserId(userId);
        subscription.setPlan(properties.getPlan());
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartedAt(now);
        subscription.setExpiresAt(now.plus(properties.getSubscriptionDays(), ChronoUnit.DAYS));
        subscription.setAutoRenew(false);
        subscription.setPaymentProvider(paymentProvider.name());
        subscription.setExternalSubscriptionId(externalId);
        subscriptionRepository.save(subscription);

        user.setSubscriptionType(SubscriptionType.PREMIUM);
        log.info("Subscription activated userId : {} provider : {}", userId, paymentProvider.name());
        return SubscriptionResponse.builder()
                .plan(subscription.getPlan())
                .status(subscription.getStatus())
                .startedAt(subscription.getStartedAt())
                .expiresAt(subscription.getExpiresAt())
                .build();
    }
}
