package com.fcareer.catalog.entity;

import com.fcareer.catalog.model.enums.ValuationSource;
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

@Getter
@Setter
@Entity
@Table(name = "player_valuation")
@EntityListeners(AuditingEntityListener.class)
public class PlayerValuationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long basePlayerId;

    @Column(nullable = false)
    private Short gameVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ValuationSource source;

    private Long ownerUserId;

    private Short potential;

    private Long valueEur;

    private Long wageEur;

    private String modelVersion;

    @CreatedDate
    @Column(columnDefinition = "timestamptz", updatable = false)
    private Instant createdAt;
}
