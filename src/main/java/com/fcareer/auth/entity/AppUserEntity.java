package com.fcareer.auth.entity;

import com.fcareer.auth.model.SubscriptionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "app_user")
@EntityListeners(AuditingEntityListener.class)
public class AppUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionType subscriptionType = SubscriptionType.FREE;

    @Column(nullable = false)
    private int creditBalance;

    @Column(nullable = false)
    private int dailyCreditLimit;

    private LocalDate lastCreditRefresh;

    @Column(nullable = false)
    private boolean emailVerified = true;

    /** USER | ADMIN; admin yetkisi JWT role claim ile taşınır (BE-SEC-07). */
    @Column(nullable = false)
    private String role = "USER";

    /** PROD-PREP 3: aydınlatma metnini okuduğunu onayladığı an ve metnin sürümü (bilgilendirme onayı). */
    @Column(columnDefinition = "timestamptz")
    private Instant privacyAckAt;

    private Integer privacyAckVersion;

    @CreatedDate
    @Column(columnDefinition = "timestamptz", updatable = false)
    private Instant createdAt;
}
