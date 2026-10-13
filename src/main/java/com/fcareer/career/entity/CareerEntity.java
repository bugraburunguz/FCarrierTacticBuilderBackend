package com.fcareer.career.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
@Table(name = "career")
@EntityListeners(AuditingEntityListener.class)
public class CareerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Short gameVersionId;

    private Integer clubId;

    private Integer season;

    private Long budgetEur;

    private String name;

    private String spoilerMode = "SHOW";

    private Long wageBudgetEur;

    private java.time.LocalDate windowEndsOn;

    /** Kariyerin oyun içi tarihi (BE-BUG-18); boşsa yaş gerçek tarihe göre hesaplanır. */
    @Column(name = "current_game_date")
    private java.time.LocalDate gameDate;

    private String challengeId;

    @CreatedDate
    @Column(columnDefinition = "timestamptz", updatable = false)
    private Instant createdAt;
}
