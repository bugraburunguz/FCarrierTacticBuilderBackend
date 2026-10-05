package com.fcareer.billing.repository;

import com.fcareer.billing.entity.SubscriptionEntity;
import com.fcareer.billing.model.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<SubscriptionEntity, Long> {

    Optional<SubscriptionEntity> findFirstByUserIdAndStatusAndExpiresAtAfterOrderByExpiresAtDesc(
            Long userId, SubscriptionStatus status, Instant now);
}
