package com.fcareer.billing.service;

import com.fcareer.auth.entity.AppUserEntity;
import com.fcareer.auth.model.SubscriptionType;
import com.fcareer.auth.repository.AppUserRepository;
import com.fcareer.billing.model.SubscriptionStatus;
import com.fcareer.billing.repository.SubscriptionRepository;
import com.fcareer.common.exception.NotFoundException;
import com.fcareer.common.exception.PremiumRequiredException;
import com.fcareer.ut.kb.ContributorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntitlementService {

    private static final String USER = "user";

    private final SubscriptionRepository subscriptionRepository;
    private final AppUserRepository appUserRepository;
    private final Clock clock;
    private final ContributorService contributorService;

    @Transactional
    public boolean isPremium(AppUserEntity user) {
        if (contributorService.isActive(user.getId())) {
            return true; // 08 D-20: aktif katkıcı PREMIUM haklarını alır
        }
        if (user.getSubscriptionType() != SubscriptionType.PREMIUM) {
            return false;
        }
        boolean active = subscriptionRepository.findFirstByUserIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
                user.getId(), SubscriptionStatus.ACTIVE, clock.instant()).isPresent();
        if (!active) {
            log.info("Premium expired, downgrading userId : {}", user.getId());
            user.setSubscriptionType(SubscriptionType.FREE);
        }
        return active;
    }

    @Transactional
    public void requirePremium(Long userId) {
        AppUserEntity user = appUserRepository.findById(userId).orElseThrow(() -> new NotFoundException(USER, userId));
        if (!isPremium(user)) {
            log.warn("Premium required userId : {}", userId);
            throw new PremiumRequiredException();
        }
    }
}
