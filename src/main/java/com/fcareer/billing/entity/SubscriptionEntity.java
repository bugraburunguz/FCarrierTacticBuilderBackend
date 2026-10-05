package com.fcareer.billing.entity;

import com.fcareer.billing.model.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "subscription")
public class SubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String plan;

    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status;

    @Column(columnDefinition = "timestamptz")
    private Instant startedAt;

    @Column(columnDefinition = "timestamptz")
    private Instant expiresAt;

    private Boolean autoRenew;

    private String paymentProvider;

    private String externalSubscriptionId;
}
